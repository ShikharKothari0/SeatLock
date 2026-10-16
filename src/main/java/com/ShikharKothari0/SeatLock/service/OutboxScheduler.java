package com.ShikharKothari0.SeatLock.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {
    private final OutboxWorker worker;
    public OutboxScheduler(OutboxWorker worker) { this.worker = worker; }
    @Scheduled(fixedDelayString = "${seatlock.outbox.poll-ms:1000}")
    public void dispatch() {
        for (int i = 0; i < 20 && !Thread.currentThread().isInterrupted(); i++) if (!worker.dispatchOne()) break;
    }
}
