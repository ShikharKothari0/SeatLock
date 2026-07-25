package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.service.BookingService;
import com.ShikharKothari0.SeatLock.service.SeatHoldService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

public class FullSystemIntegrationTest extends IntegrationTestBase{
    private static final Logger log =
            LoggerFactory.getLogger(FullSystemIntegrationTest.class);

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TestKafkaEventCapture eventCapture;

    @BeforeEach
    void clearCapture() {
        eventCapture.clearAll();
    }

// Test 1: full hold → confirm → Kafka events

    @Test
    void holdAndConfirmPublishesEventsToCorrectKafkaTopics()
            throws InterruptedException
    {
        // Part a: hold seat A1
        seatHoldService.holdSeat(SEAT_A1, TEST_USER);

        // Part b: verify seat-held event arrives on Kafka
        ConsumerRecord<String, String> heldRecord =
                eventCapture.heldEvents().poll(20, TimeUnit.SECONDS);

        assertThat(heldRecord)
                .as("seat-held event must be published within 10 seconds")
                .isNotNull();

        assertThat(heldRecord.key())
                .as("Message key must be the seatId (for partition routing)")
                .isEqualTo(SEAT_A1.toString());

        assertThat(heldRecord.value())
                .contains("\"seatId\":\"" + SEAT_A1 + "\"")
                .contains("\"userId\":\"" + TEST_USER + "\"")
                .contains("\"expiresAt\"");

        log.info("seat-held event verified: partition={} offset={} key={}",
                heldRecord.partition(), heldRecord.offset(), heldRecord.key());

        // Part c: confirm the booking
        String idempotencyKey = "integration-test-" + UUID.randomUUID();
        BookingResponse response = bookingService.confirmBooking(
                new BookingConfirmRequest(TEST_USER, List.of(SEAT_A1), idempotencyKey)
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.status().name()).isEqualTo("CONFIRMED");
        assertThat(response.seatIds()).containsExactly(SEAT_A1);

        // Part d: verify seat-confirmed event arrives on Kafka
        ConsumerRecord<String, String> confirmedRecord =
                eventCapture.confirmedEvents().poll(20, TimeUnit.SECONDS);

        assertThat(confirmedRecord)
                .as("seat-confirmed event must be published within 20 seconds")
                .isNotNull();

        assertThat(confirmedRecord.key())
                .as("Message key must be the bookingId")
                .isEqualTo(response.id().toString());

        assertThat(confirmedRecord.value())
                .contains("\"bookingId\":\"" + response.id() + "\"")
                .contains("\"userId\":\"" + TEST_USER + "\"");

        // Part e: verify Postgres state
        long confirmedSeats = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.CONFIRMED)
                .count();

        assertThat(confirmedSeats)
                .as("Exactly 1 seat must be CONFIRMED in Postgres")
                .isEqualTo(1L);

        assertThat(bookingRepository.count())
                .as("Exactly 1 booking must exist in Postgres")
                .isEqualTo(1L);

        // Part f: verify Redis lock was released after confirm
        String lockOwner = redisTemplate.opsForValue()
                .get("seat:lock:" + SEAT_A1);

        assertThat(lockOwner)
                .as("Redis lock must be released after booking confirmed")
                .isNull();

        log.info("Full hold → confirm → Kafka events test passed");
    }

// Test 2: idempotency under the full stack

    @Test
    void idempotentConfirmReturnsSameBookingAndPublishesExactlyOneKafkaEvent()
            throws InterruptedException
    {
        seatHoldService.holdSeat(SEAT_A1, TEST_USER);

        String idempotencyKey = "idem-integration-" + UUID.randomUUID();
        BookingConfirmRequest request =
                new BookingConfirmRequest(TEST_USER, List.of(SEAT_A1), idempotencyKey);

        // first call
        BookingResponse first = bookingService.confirmBooking(request);

        // wait for the first seat-confirmed event
        ConsumerRecord<String, String> firstEvent =
                eventCapture.confirmedEvents().poll(20, TimeUnit.SECONDS);

        assertThat(firstEvent)
                .as("First confirm must publish a seat-confirmed event")
                .isNotNull();

        // second call with identical request
        BookingResponse second = bookingService.confirmBooking(request);

        // assertions: same booking returned, only one DB row
        assertThat(second.id())
                .as("Second call must return the same booking ID as the first")
                .isEqualTo(first.id());

        assertThat(bookingRepository.count())
                .as("Only one booking must exist — no duplicate created")
                .isEqualTo(1L);

        // wait 3 seconds, confirm no second Kafka event was published
        ConsumerRecord<String, String> secondEvent =
                eventCapture.confirmedEvents().poll(20, TimeUnit.SECONDS);

        assertThat(secondEvent)
                .as("Idempotent second call must NOT publish a second seat-confirmed event")
                .isNull();

        log.info("Idempotency integration test passed — bookingId={}", first.id());
    }

// Test 3: multi-seat booking publishes one event covering all seats

    @Test
    void multiSeatBookingPublishesSingleEventWithAllSeatIds()
            throws InterruptedException
    {
        // hold two seats for the same user
        seatHoldService.holdSeat(SEAT_A1, TEST_USER);
        seatHoldService.holdSeat(SEAT_A2, TEST_USER);

        // consume and discard the two seat-held events
        eventCapture.heldEvents().poll(20, TimeUnit.SECONDS);
        eventCapture.heldEvents().poll(20, TimeUnit.SECONDS);

        // confirm both seats in one request
        BookingResponse response = bookingService.confirmBooking(
                new BookingConfirmRequest(
                        TEST_USER,
                        List.of(SEAT_A1, SEAT_A2),
                        "multi-seat-test-" + UUID.randomUUID()
                )
        );

        // verify exactly one seat-confirmed event covers both seats
        ConsumerRecord<String, String> confirmedRecord =
                eventCapture.confirmedEvents().poll(20, TimeUnit.SECONDS);

        assertThat(confirmedRecord).isNotNull();

        assertThat(confirmedRecord.value())
                .as("Single confirmed event must contain both seat IDs")
                .contains(SEAT_A1.toString())
                .contains(SEAT_A2.toString());

        // verify no second seat-confirmed event was published
        ConsumerRecord<String, String> extraEvent =
                eventCapture.confirmedEvents().poll(2, TimeUnit.SECONDS);

        assertThat(extraEvent)
                .as("Only one seat-confirmed event must be published for a multi-seat booking")
                .isNull();

        // verify Postgres: 2 confirmed seats, 1 booking
        long confirmedCount = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.CONFIRMED)
                .count();

        assertThat(confirmedCount).isEqualTo(2L);
        assertThat(bookingRepository.count()).isEqualTo(1L);

        log.info("Multi-seat booking test passed: {} seats confirmed under bookingId={}",
                response.seatIds().size(), response.id());
    }

// Test 4: full consumer processing verification

    @Test
    void seatConfirmedConsumerReceivesAndProcessesEvent()
            throws InterruptedException
    {
        // hold and confirm a seat
        seatHoldService.holdSeat(SEAT_A1, TEST_USER);
        bookingService.confirmBooking(
                new BookingConfirmRequest(
                        TEST_USER,
                        List.of(SEAT_A1),
                        "consumer-test-" + UUID.randomUUID()
                )
        );

        // verify capture component received the event, this proves the event went through the Kafka topic and
        // the consumer group received it — not just that the producer fired it
        await()
                .atMost(TEN_SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(eventCapture.confirmedEvents())
                                .as("SeatConfirmedConsumer group must have received the event")
                                .isNotEmpty()
                );

        ConsumerRecord<String, String> record =
                eventCapture.confirmedEvents().poll();

        assertThat(record).isNotNull();
        assertThat(record.value())
                .contains("bookingId")
                .contains("userId")
                .contains("seatIds")
                .contains("confirmedAt");

        log.info("Consumer processing test passed: event received at " +
                "partition={} offset={}", record.partition(), record.offset());
    }
}
