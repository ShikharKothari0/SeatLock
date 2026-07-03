package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class RepositoryWiringTest {
    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AppUserRepository appUserRepository;

    @Test
    void allRepositoriesWireUpAndPersistCorrectly() {
        Venue venue = venueRepository.save(new Venue(null, "Wiring Test Arena", "Mumbai", 50));
        Event event = eventRepository.save(
                new Event(null, "Wiring Test Event", venue, Instant.now(), Instant.now().plusSeconds(3600))
        );
        Seat seat = seatRepository.save(
                new Seat(null, event, "Z1", "Section Z", SeatStatus.AVAILABLE, null, null, null)
        );
        AppUser user = appUserRepository.save(
                new AppUser(null, "wiretest@example.com", "Wire Test User")
        );
        Booking booking = bookingRepository.save(
                new Booking(null, user, event, BookingStatus.HELD, Instant.now(), null, "wiring-test-key-" + UUID.randomUUID())
        );

        assertThat(venueRepository.findById(venue.getId())).isPresent();        // Checking if the venueRepository is wiring up correctly and persisting the venue entity
        assertThat(eventRepository.findById(event.getId())).isPresent();        // Checking if the eventRepository is wiring up correctly and persisting the event entity
        assertThat(seatRepository.findById(seat.getId())).isPresent();          // Checking if the seatRepository is wiring up correctly and persisting the seat entity
        assertThat(appUserRepository.findById(user.getId())).isPresent();       // Checking if the appUserRepository is wiring up correctly and persisting the appUser entity
        assertThat(bookingRepository.findById(booking.getId())).isPresent();    // Checking if the bookingRepository is wiring up correctly and persisting the booking entity
    }
}
