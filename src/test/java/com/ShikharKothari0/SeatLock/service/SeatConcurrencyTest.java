package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.entity.*;
import com.ShikharKothari0.SeatLock.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class SeatConcurrencyTest {
    @Autowired private SeatService seatService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private VenueRepository venueRepository;

    private UUID contendedSeatId;

    @BeforeEach
    void setUp() {
        Venue venue = venueRepository.save(new Venue(null, "Concurrency Test Arena", "Mumbai", 10));
        Event event = eventRepository.save(
                new Event(null, "Concurrency Test Event", venue, Instant.now(), Instant.now().plusSeconds(3600))
        );
        Seat seat = seatRepository.save(
                new Seat(null, event, "C1", "Section C", SeatStatus.AVAILABLE, null, null)
        );
        contendedSeatId = seat.getId();
    }

    @Test
    void fiftyConcurrentRequestsForOneSeat_onlyOneShouldWin() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // all threads wait here, then release together
                    seatService.reserveSeatNaively(contendedSeatId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();      // wait until all 50 threads are spun up and waiting
        startLatch.countDown();  // release them all at the exact same moment
        doneLatch.await();       // wait for all to finish
        executor.shutdown();

        System.out.println("Successes: " + successCount.get());
        System.out.println("Failures: " + failureCount.get());

        // The real assertion: exactly one thread should have won the seat
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(49);

        Seat finalState = seatRepository.findById(contendedSeatId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
