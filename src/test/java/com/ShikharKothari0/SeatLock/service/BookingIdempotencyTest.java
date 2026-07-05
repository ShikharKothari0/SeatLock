package com.ShikharKothari0.SeatLock.service;

import com.ShikharKothari0.SeatLock.config.RedisTestContainer;
import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.repository.BookingRepository;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class BookingIdempotencyTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("seatlock_test")
                    .withUsername("admin")
                    .withPassword("password");

    @Container
    static RedisTestContainer redis = RedisTestContainer.getInstance();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getMappedPort);
    }

    @Autowired
    private SeatHoldService seatHoldService;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final UUID TEST_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SEAT_A1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SEAT_B1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void resetState() {
        // load seats once, reset all fields then save, this nulls out booking_id in Postgres
        List<Seat> allSeats = seatRepository.findAll();
        allSeats.forEach(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);                      // sets booking_id = NULL in the Postgres
        });
        seatRepository.saveAll(allSeats);               // save the list

        // delete all bookings
        bookingRepository.deleteAll();

        // flush Redis
        Set<String> keys = redisTemplate.keys("seat:lock:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void sameIdempotencyKeyReturnsSameBookingWithoutCreatingDuplicate() {
        // hold the seat
        seatHoldService.holdSeat(SEAT_A1, TEST_USER_ID);

        String idempotencyKey = "idem-test-" + UUID.randomUUID();

        BookingConfirmRequest request = new BookingConfirmRequest(
                TEST_USER_ID,
                List.of(SEAT_A1),
                idempotencyKey
        );

        // confirm the booking twice with the same key
        BookingResponse firstResponse = bookingService.confirmBooking(request);
        BookingResponse secondResponse = bookingService.confirmBooking(request);

        // assert that the same booking response is returned both the times.
        assertThat(secondResponse.id())
                .as("Second call must return the same booking ID as the first call")
                .isEqualTo(firstResponse.id());

        assertThat(secondResponse.status())
                .isEqualTo(firstResponse.status());

        assertThat(secondResponse.seatIds())
                .containsExactlyInAnyOrderElementsOf(firstResponse.seatIds());

        // assert that exactly one booking row in the database
        long bookingCount = bookingRepository.count();
        assertThat(bookingCount)
                .as("Exactly one booking must exist — duplicate requests must not create extras")
                .isEqualTo(1L);

        // assert that the seat is confirmed exactly once
        long confirmedSeatCount = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.CONFIRMED)
                .count();
        assertThat(confirmedSeatCount)
                .as("Exactly one seat must be CONFIRMED")
                .isEqualTo(1L);
    }

    @Test
    void differentIdempotencyKeysCreateSeparateBookings() {
        // hold two different seats
        seatHoldService.holdSeat(SEAT_A1, TEST_USER_ID);
        seatHoldService.holdSeat(SEAT_B1, TEST_USER_ID);

        // confirm A1 with key-1
        BookingResponse first = bookingService.confirmBooking(
                new BookingConfirmRequest(TEST_USER_ID, List.of(SEAT_A1), "key-1")
        );

        BookingResponse second = bookingService.confirmBooking(
                new BookingConfirmRequest(TEST_USER_ID, List.of(SEAT_B1), "key-2")
        );


        assertThat(bookingRepository.count()).as("Two different idempotency keys must produce two separate bookings")
                .isEqualTo(2L);

        assertThat(first.id()).as("Each booking must have a unique ID")
                .isNotEqualTo(second.id());

        assertThat(first.seatIds())
                .as("First booking must contain seat A1 only")
                .containsExactly(SEAT_A1);

        assertThat(second.seatIds())
                .as("Second booking must contain seat B only")
                .containsExactly(SEAT_B1);

        
        long confirmedCount = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.CONFIRMED)
                .count();

        assertThat(confirmedCount)
                .as("Both seats must be CONFIRMED in Postgres")
                .isEqualTo(2L);
    }
}
