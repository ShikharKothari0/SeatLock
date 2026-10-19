package com.ShikharKothari0.SeatLock.service;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

@Service
public class TransactionEffects {
    private final JdbcTemplate jdbc;
    private final RedisLockService locks;
    private final SeatCacheService cache;
    public TransactionEffects(JdbcTemplate jdbc, RedisLockService locks, SeatCacheService cache) {
        this.jdbc = jdbc; this.locks = locks; this.cache = cache;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSalesOpen(UUID eventId) {
        var rows=jdbc.queryForList("SELECT publication, sale_start_time<=CURRENT_TIMESTAMP AND COALESCE(sale_end_time,event_time)>CURRENT_TIMESTAMP AS open FROM event WHERE id=? FOR UPDATE",eventId);
        if(rows.isEmpty() || !"PUBLISHED".equals(rows.getFirst().get("publication")) || !Boolean.TRUE.equals(rows.getFirst().get("open")))
            throw new com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException("Event sales are not open");
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String kind, String destination, String key, String payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO reservation_outbox(id,kind,destination,message_key,payload) VALUES (?,?,?,?,?)",
                id, kind, destination, key, payload);
        return id;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void serializeBooking(String key) {
        // Transaction-scoped and shared across application instances. Hash collisions only serialize unrelated requests.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, key);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(UUID eventId) {
        jdbc.update("UPDATE event SET seat_revision=seat_revision+1 WHERE id=?", eventId);
        afterCommit(() -> cache.evictCache(eventId));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseAfterCommit(UUID seatId, String token) {
        if (token == null) return;
        String key = "seat:lock:" + seatId;
        enqueue("REDIS_RELEASE", key, seatId.toString(), token);
        // Keep the normal release response fast. The durable row retries failures and repeats safely.
        afterCommit(() -> locks.releaseOwnedLock(key, token));
    }
    public void releaseOnRollback(UUID seatId, String token) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) bestEffort(() -> locks.releaseOwnedLock("seat:lock:" + seatId, token));
                // Unknown outcomes/process death are bounded by the Redis TTL.
            }
        });
    }
    public void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { bestEffort(action); }
        });
    }
    private void bestEffort(Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { LoggerFactory.getLogger(TransactionEffects.class).warn("Post-transaction cleanup deferred: {}", error.getClass().getSimpleName()); }
    }
}
