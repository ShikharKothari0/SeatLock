package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @org.springframework.data.jpa.repository.Query(value="SELECT * FROM event WHERE publication<>'DRAFT' ORDER BY event_time", nativeQuery=true)
    java.util.List<Event> findPublished();
}
