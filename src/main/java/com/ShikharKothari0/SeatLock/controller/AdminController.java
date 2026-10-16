package com.ShikharKothari0.SeatLock.controller;

import com.ShikharKothari0.SeatLock.dto.admin.CacheMetricsResponse;
import com.ShikharKothari0.SeatLock.dto.admin.CircuitBreakerMetricsResponse;
import com.ShikharKothari0.SeatLock.dto.admin.MetricStreamSnapshot;
import com.ShikharKothari0.SeatLock.dto.admin.MetricsOverviewResponse;
import com.ShikharKothari0.SeatLock.dto.admin.RedisMetricsResponse;
import com.ShikharKothari0.SeatLock.dto.admin.SystemHealthResponse;
import com.ShikharKothari0.SeatLock.service.AdminMetricsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminMetricsService adminMetricsService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AdminController(AdminMetricsService adminMetricsService) {
        this.adminMetricsService = adminMetricsService;
    }

    @GetMapping("/metrics/overview")
    public ResponseEntity<MetricsOverviewResponse> getOverview() {
        return ResponseEntity.ok(adminMetricsService.getOverview());
    }

    @GetMapping("/metrics/cache")
    public ResponseEntity<CacheMetricsResponse> getCacheMetrics() {
        return ResponseEntity.ok(adminMetricsService.getCacheMetrics());
    }

    @GetMapping("/metrics/redis")
    public ResponseEntity<RedisMetricsResponse> getRedisMetrics() {
        return ResponseEntity.ok(adminMetricsService.getRedisMetrics());
    }

    @GetMapping("/metrics/circuit-breakers")
    public ResponseEntity<List<CircuitBreakerMetricsResponse>> getCircuitBreakerMetrics() {
        return ResponseEntity.ok(adminMetricsService.getCircuitBreakerMetrics());
    }

    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(adminMetricsService.getSystemHealth());
    }

    @GetMapping(value = "/metrics/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> subscription = new AtomicReference<>();
        Runnable cancel = () -> {
            closed.set(true);
            ScheduledFuture<?> future = subscription.get();
            if (future != null) future.cancel(false);
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(error -> cancel.run());

        Runnable task = () -> {
            if (closed.get()) return;
            try {
                MetricStreamSnapshot snapshot = adminMetricsService.getMetricStreamSnapshot();
                emitter.send(SseEmitter.event()
                        .name("metrics")
                        .data(snapshot)
                        .build());
            } catch (IOException e) {
                cancel.run();
                emitter.complete();
            } catch (RuntimeException e) {
                cancel.run();
                emitter.completeWithError(e);
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(task, 0, 3, TimeUnit.SECONDS);
        subscription.set(future);
        // Completion may race with scheduling the first snapshot.
        if (closed.get()) future.cancel(false);

        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
