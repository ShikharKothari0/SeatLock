package com.ShikharKothari0.SeatLock;

import com.ShikharKothari0.SeatLock.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class EntitySchemaTest {
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void entitiesPersistSuccessfully() {
        Venue venue = new Venue(null, "Test Arena", "Mumbai", 500);
        entityManager.persist(venue);

        Event event = new Event(null, "Test Concert", venue, Instant.now(), Instant.now().plusSeconds(3600));
        entityManager.persist(event);

        Seat seat = new Seat(null, event, "A1", "Section A", SeatStatus.AVAILABLE, null, null);
        entityManager.persist(seat);

        entityManager.flush();

        assertThat(seat.getId()).isNotNull();              // Checks if the data in the seat table is persisted successfully
        assertThat(event.getVenue()).isEqualTo(venue);     // Checks if the relationship between Event and Venue is correctly established
        assertThat(seat.getEvent()).isEqualTo(event);      // Checks if the relationship between Seat and Event is correctly established
    }
}
