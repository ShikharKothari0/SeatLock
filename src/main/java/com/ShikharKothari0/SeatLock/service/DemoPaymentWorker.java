package com.ShikharKothari0.SeatLock.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="seatlock.payments.worker-enabled",havingValue="true",matchIfMissing=true)
public class DemoPaymentWorker {
    private final DemoPaymentService payments;
    public DemoPaymentWorker(DemoPaymentService payments) { this.payments=payments; }
    @Scheduled(fixedDelayString="${seatlock.payments.poll-ms:2000}")
    public void process() { payments.processDue(); }
}
