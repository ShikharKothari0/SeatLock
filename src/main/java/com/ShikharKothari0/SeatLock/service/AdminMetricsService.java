package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.dto.admin.CacheMetricsResponse;
import com.ShikharKothari0.SeatLock.dto.admin.CircuitBreakerMetricsResponse;
import com.ShikharKothari0.SeatLock.dto.admin.MetricsOverviewResponse;
import com.ShikharKothari0.SeatLock.dto.admin.MetricStreamSnapshot;
import com.ShikharKothari0.SeatLock.dto.admin.RedisMetricsResponse;
import com.ShikharKothari0.SeatLock.dto.admin.SystemHealthResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AdminMetricsService {
    private static final Logger log = LoggerFactory.getLogger(AdminMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public AdminMetricsService(
            MeterRegistry meterRegistry,
            StringRedisTemplate redisTemplate,
            RedisConnectionFactory redisConnectionFactory,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public MetricsOverviewResponse getOverview() {
        Counter holdsCreated = meterRegistry.counter("seatlock.holds.success");
        Counter holdsRejected = meterRegistry.counter("seatlock.holds.rejected");
        Counter holdsExpired = meterRegistry.counter("seatlock.holds.expired");
        Counter bookingsConfirmed = meterRegistry.counter("seatlock.bookings.confirmed");
        Timer holdLatency = meterRegistry.timer("seatlock.holds.latency");

        long totalHolds = (long) (holdsCreated != null ? holdsCreated.count() : 0) +
                         (long) (holdsRejected != null ? holdsRejected.count() : 0);
        long successfulHolds = holdsCreated != null ? (long) holdsCreated.count() : 0;
        double holdSuccessRate = totalHolds > 0 ? (double) successfulHolds / totalHolds * 100.0 : 0.0;

        return new MetricsOverviewResponse(
                bookingsConfirmed != null ? (long) bookingsConfirmed.count() : 0,
                calculateBookingsPerMinute(),
                totalHolds,
                holdsExpired != null ? (long) holdsExpired.count() : 0,
                holdsRejected != null ? (long) holdsRejected.count() : 0,
                holdSuccessRate,
                holdLatency != null ? holdLatency.mean(TimeUnit.MILLISECONDS) : 0.0,
                holdLatency != null ? percentile(holdLatency, 0.99) : 0.0
        );
    }

    public CacheMetricsResponse getCacheMetrics() {
        Counter hits = meterRegistry.counter("seatlock.cache.hits");
        Counter misses = meterRegistry.counter("seatlock.cache.misses");

        long cacheHits = hits != null ? (long) hits.count() : 0;
        long cacheMisses = misses != null ? (long) misses.count() : 0;
        long total = cacheHits + cacheMisses;
        double hitRatio = total > 0 ? (double) cacheHits / total * 100.0 : 0.0;

        long activeCacheKeys = countCacheKeys();
        long cacheInvalidations = 0;

        return new CacheMetricsResponse(cacheHits, cacheMisses, hitRatio, cacheInvalidations, activeCacheKeys);
    }

    public RedisMetricsResponse getRedisMetrics() {
        try {
            var connection = redisConnectionFactory.getConnection();
            Properties props = connection.serverCommands().info();

            boolean connected = true;
            long memoryUsedBytes = parseLong(props.getProperty("used_memory"), 0);
            long totalKeys = countSeatLockKeys();
            long connectedClients = parseLong(props.getProperty("connected_clients"), 0);
            long activeLocks = countActiveLocks();

            long keyspaceHits = parseLong(props.getProperty("keyspace_hits"), 0);
            long keyspaceMisses = parseLong(props.getProperty("keyspace_misses"), 0);
            long totalOps = keyspaceHits + keyspaceMisses;
            double hitRatio = totalOps > 0 ? (double) keyspaceHits / totalOps * 100.0 : 0.0;

            return new RedisMetricsResponse(connected, activeLocks, memoryUsedBytes, totalKeys, hitRatio, connectedClients);
        } catch (Exception e) {
            log.warn("Failed to fetch Redis metrics: {}", e.getMessage());
            return new RedisMetricsResponse(false, 0, 0, 0, 0.0, 0);
        }
    }

    public List<CircuitBreakerMetricsResponse> getCircuitBreakerMetrics() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .map(this::toCircuitBreakerMetrics)
                .collect(Collectors.toList());
    }

    public SystemHealthResponse getSystemHealth() {
        var runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        double cpuUsage = getProcessCpuUsage();

        int hikariActive = 0;
        int hikariPending = 0;

        return new SystemHealthResponse(
                "UP",
                cpuUsage,
                heapUsed,
                heapMax,
                Thread.getAllStackTraces().keySet().size(),
                hikariActive,
                hikariPending,
                getCircuitBreakerMetrics()
        );
    }

    public MetricStreamSnapshot getMetricStreamSnapshot() {
        return new MetricStreamSnapshot(
                getOverview(),
                getCacheMetrics(),
                getRedisMetrics(),
                getCircuitBreakerMetrics(),
                Instant.now()
        );
    }

    private double calculateBookingsPerMinute() {
        Counter bookingsConfirmed = meterRegistry.counter("seatlock.bookings.confirmed");
        if (bookingsConfirmed == null) return 0.0;
        return bookingsConfirmed.count();
    }

    private double percentile(Timer timer, double percentile) {
        try {
            var snapshot = timer.takeSnapshot();
            var values = snapshot.percentileValues();
            for (var v : values) {
                if (Math.abs(v.percentile() - percentile) < 0.001) {
                    return v.value(TimeUnit.MILLISECONDS);
                }
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long countCacheKeys() {
        try {
            Set<String> keys = redisTemplate.keys("seats:event:*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countActiveLocks() {
        try {
            Set<String> keys = redisTemplate.keys("seat:lock:*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countSeatLockKeys() {
        try {
            Set<String> keys = redisTemplate.keys("seatlock:*");
            long seatLockKeys = keys != null ? keys.size() : 0;
            Set<String> seatKeys = redisTemplate.keys("seat:*");
            long seatKeysCount = seatKeys != null ? seatKeys.size() : 0;
            Set<String> ratelimitKeys = redisTemplate.keys("ratelimit:*");
            long ratelimitKeysCount = ratelimitKeys != null ? ratelimitKeys.size() : 0;
            return seatLockKeys + seatKeysCount + ratelimitKeysCount;
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseLong(String value, long defaultValue) {
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private CircuitBreakerMetricsResponse toCircuitBreakerMetrics(CircuitBreaker cb) {
        var metrics = cb.getMetrics();
        return new CircuitBreakerMetricsResponse(
                cb.getName(),
                cb.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfBufferedCalls()
        );
    }

    private double getProcessCpuUsage() {
        try {
            var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
                return osBean.getProcessCpuLoad() * 100.0;
            }
        } catch (Exception ignored) {}
        return 0.0;
    }
}