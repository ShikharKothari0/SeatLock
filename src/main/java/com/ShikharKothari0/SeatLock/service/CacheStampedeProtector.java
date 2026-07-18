package com.ShikharKothari0.SeatLock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

@Component
class CacheStampedeProtector {
    private static final Logger log =
            LoggerFactory.getLogger(CacheStampedeProtector.class);

    private static final String LOCK_KEY_PREFIX = "seat-cache-lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int MAX_WAIT_RETRIES = 10;
    private static final long RETRY_DELAY_MS = 50;

    private final StringRedisTemplate redisTemplate;

    public CacheStampedeProtector(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Ensures only one thread repopulates the cache for a given key during a cache miss.
     * Other concurrent threads wait briefly and retry reading from cache rather than independently querying the database.
     *
     * @param eventId          the event whose seat cache is being repopulated
     * @param cacheReader      supplier that attempts to read from cache; returns null on miss
     * @param databaseLoader   supplier that loads fresh data from the database
     * @param cacheWriter      consumer that writes fresh data into the cache
     */
    public <T> T loadWithStampedeProtection(
            UUID eventId,
            Supplier<T> cacheReader,
            Supplier<T> databaseLoader,
            java.util.function.Consumer<T> cacheWriter
    ) {
        String lockKey = LOCK_KEY_PREFIX + eventId;
        String lockValue = UUID.randomUUID().toString();

        // attempt to acquire the short-lived repopulation lock
        boolean acquiredLock = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL)
        );

        if (acquiredLock) {
            try {
                log.debug("Stampede lock acquired — eventId={} loading from DB", eventId);
                T freshData = databaseLoader.get();
                cacheWriter.accept(freshData);
                return freshData;

            } finally {
                releaseLockIfOwned(lockKey, lockValue);
            }
        }

        // lock not acquired, meaning another thread is already loading
        // wait briefly and retry reading from cache instead of hitting the DB
        log.debug("Stampede lock held by another thread — eventId={} waiting", eventId);

        for (int attempt = 0; attempt < MAX_WAIT_RETRIES; attempt++) {
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            T cached = cacheReader.get();
            if (cached != null) {
                log.debug("Cache populated by another thread — eventId={} " +
                        "served without DB query, waitAttempts={}", eventId, attempt + 1);
                return cached;
            }
        }

        // fallback: waited too long, load from DB anyway
        // this happens rarely (e.g. the lock-holding thread crashed or the DB query is unusually slow)
        // correctness over efficiency in this edge case
        log.warn("Stampede wait exhausted — eventId={} falling back to direct DB load",
                eventId);
        T freshData = databaseLoader.get();
        cacheWriter.accept(freshData);
        return freshData;
    }

    private void releaseLockIfOwned(String lockKey, String lockValue) {
        String currentValue = redisTemplate.opsForValue().get(lockKey);
        if (lockValue.equals(currentValue)) {
            redisTemplate.delete(lockKey);
        }
        // if currentValue doesn't match, the lock already expired and was possibly acquired by someone else, so don't delete their lock
    }
}
