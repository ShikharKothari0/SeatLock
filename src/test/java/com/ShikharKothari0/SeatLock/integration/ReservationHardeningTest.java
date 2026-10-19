package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.dto.request.BookingConfirmRequest;
import com.ShikharKothari0.SeatLock.dto.response.BookingResponse;
import com.ShikharKothari0.SeatLock.dto.response.SeatResponse;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.kafka.consumer.SeatConfirmedConsumer;
import com.ShikharKothari0.SeatLock.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=false", "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=500ms", "logging.level.org.hibernate.SQL=WARN"})
@ActiveProfiles("test") // No scheduled expiry/outbox work; tests invoke workers deterministically.
@Testcontainers
class ReservationHardeningTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redisClient;
    @Autowired SeatHoldService holds;
    @Autowired BookingService bookings;
    @Autowired HoldExpiryService expiry;
    @Autowired OutboxWorker outbox;
    @Autowired SeatCacheService cache;
    @Autowired SeatService listings;
    @Autowired SeatConfirmedConsumer notifications;
    @Autowired PlatformTransactionManager transactions;
    UUID event, owner, seat;

    @BeforeEach void fixture() {
        // These connections point exclusively to this class's disposable containers.
        jdbc.update("DELETE FROM reservation_outbox");
        jdbc.update("DELETE FROM consumed_message");
        event=UUID.randomUUID(); owner=UUID.randomUUID(); seat=UUID.randomUUID(); UUID venue=UUID.randomUUID();
        jdbc.update("INSERT INTO venue VALUES (?, 'Hardening test', 'Test', 1)", venue);
        jdbc.update("INSERT INTO app_user VALUES (?, ?, 'Test')", owner, owner+"@example.invalid");
        jdbc.update("INSERT INTO event(id,name,venue_id,sale_start_time,event_time) VALUES (?, 'Test', ?, CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP + INTERVAL '1 day')", event, venue);
        jdbc.update("INSERT INTO seat(id,event_id,seat_number,section,status,version) VALUES (?,?,'T1','Test','AVAILABLE',0)", seat, event);
    }
    BookingConfirmRequest request(String key) { return new BookingConfirmRequest(owner, List.of(seat), key); }
    String status() { return jdbc.queryForObject("SELECT status FROM seat WHERE id=?", String.class, seat); }
    String lock() { return redisClient.opsForValue().get("seat:lock:"+seat); }
    int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
    void drain() { for(int i=0;i<100;i++) if(!outbox.dispatchOne()) return; fail("Outbox did not drain"); }

    @Test void rollbackAfterHoldRemovesOnlyItsLockAndWritesNoEvent() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(s -> {
            holds.holdSeat(seat, owner); throw new IllegalStateException("Injected rollback");
        }));
        assertEquals("AVAILABLE",status()); assertNull(lock());
        assertEquals(0,count("SELECT count(*) FROM reservation_outbox"));
        assertEquals(0,cache.revision(event));
    }
    @Test void actualCommitFailureDoesNotPublishConfirmOrReleaseHold() {
        holds.holdSeat(seat,owner); String token=lock();
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_test_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected commit failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER test_commit_failure AFTER INSERT ON booking DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fail_test_commit()");
        try { assertThrows(RuntimeException.class, () -> bookings.confirmBooking(request(UUID.randomUUID().toString()))); }
        finally { jdbc.execute("DROP TRIGGER test_commit_failure ON booking"); jdbc.execute("DROP FUNCTION fail_test_commit()"); }
        assertEquals("HELD",status()); assertEquals(token,lock());
        assertEquals(0,count("SELECT count(*) FROM reservation_outbox WHERE destination='seat-confirmed'"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,event));
    }
    @Test void holdCommitFailureCompensatesRedisAndRollsBackOutbox() {
        jdbc.execute("CREATE OR REPLACE FUNCTION fail_hold_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected hold commit failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER test_hold_commit_failure AFTER UPDATE ON seat DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fail_hold_commit()");
        try { assertThrows(RuntimeException.class, () -> holds.holdSeat(seat,owner)); }
        finally { jdbc.execute("DROP TRIGGER test_hold_commit_failure ON seat"); jdbc.execute("DROP FUNCTION fail_hold_commit()"); }
        assertEquals("AVAILABLE",status()); assertNull(lock());
        assertEquals(0,count("SELECT count(*) FROM reservation_outbox"));
    }
    @Test void concurrentIdenticalRequestsReturnOneBooking() throws Exception {
        holds.holdSeat(seat,owner); String key=UUID.randomUUID().toString();
        try(var pool=Executors.newFixedThreadPool(6)) {
            CountDownLatch start=new CountDownLatch(1); List<Future<BookingResponse>> results=new ArrayList<>();
            for(int i=0;i<6;i++) results.add(pool.submit(() -> { start.await(); return bookings.confirmBooking(request(key)); }));
            start.countDown(); Set<UUID> ids=new HashSet<>();
            for(var result:results) ids.add(result.get(20,TimeUnit.SECONDS).id());
            assertEquals(1,ids.size());
        }
        assertEquals(1,count("SELECT count(*) FROM reservation_outbox WHERE destination='seat-confirmed'"));
    }
    @Test void differentConfirmationKeysCannotAllocateTheSeatTwice() throws Exception {
        holds.holdSeat(seat,owner);
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1); Callable<Boolean> attempt=() -> {
                start.await(); try { bookings.confirmBooking(request(UUID.randomUUID().toString())); return true; }
                catch(com.ShikharKothari0.SeatLock.exception.SeatNotAvailableException expected) { return false; }
            };
            var a=pool.submit(attempt); var b=pool.submit(attempt); start.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,event));
    }
    @Test void deferredCleanupCannotRemoveNewHoldBySameUser() {
        holds.holdSeat(seat,owner); String first=lock(); holds.release(seat,owner);
        holds.holdSeat(seat,owner); String second=lock(); assertNotEquals(first,second);
        drain(); assertEquals(second,lock()); assertEquals("HELD",status());
    }
    @Test void staleCacheLoaderCannotRepopulateCurrentRevision() {
        long oldRevision=cache.revision(event);
        var oldSeats=List.of(new SeatResponse(seat,"T1","Test",SeatStatus.AVAILABLE));
        holds.holdSeat(seat,owner);
        cache.cacheSeats(event,oldRevision,oldSeats); // Old DB read finishes after the writer commits.
        assertTrue(cache.revision(event)>oldRevision);
        assertEquals(SeatStatus.HELD,listings.getSeats(event,null).getFirst().status());
    }
    @Test void redisOutageFailsClosedAndCleanupRecovers() {
        holds.holdSeat(seat,owner);
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        try {
            assertThrows(RuntimeException.class, () -> bookings.confirmBooking(request(UUID.randomUUID().toString())));
            assertEquals("HELD",status());
            holds.release(seat,owner); // DB release commits; cleanup is durably queued.
            assertEquals("AVAILABLE",status());
            assertThrows(RuntimeException.class, () -> holds.holdSeat(seat,owner));
            assertEquals("AVAILABLE",status());
            outbox.dispatchOne();
        } finally { redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec(); }
        jdbc.update("UPDATE reservation_outbox SET available_at=CURRENT_TIMESTAMP");
        drain(); assertNull(lock());
    }
    @Test void kafkaOutageRetainsCommittedEventsAndRecoversWithDuplicateSafeConsumer() throws Exception {
        holds.holdSeat(seat,owner);
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        BookingResponse booking;
        try {
            booking=bookings.confirmBooking(request(UUID.randomUUID().toString()));
            assertEquals("CONFIRMED",status());
            outbox.dispatchOne();
            assertTrue(count("SELECT count(*) FROM reservation_outbox WHERE delivered_at IS NULL AND kind='KAFKA'")>0);
            assertTrue(count("SELECT count(*) FROM reservation_outbox WHERE kind='KAFKA' AND attempts>0")>0);
        } finally { kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec(); }
        jdbc.update("UPDATE reservation_outbox SET available_at=CURRENT_TIMESTAMP"); drain();
        assertEquals(0,count("SELECT count(*) FROM reservation_outbox WHERE delivered_at IS NULL"));
        String json=jdbc.queryForObject("SELECT payload FROM reservation_outbox WHERE destination='seat-confirmed'",String.class);
        notifications.onSeatConfirmed(json,0,1); notifications.onSeatConfirmed(json,0,2);
        assertEquals(1,count("SELECT count(*) FROM consumed_message WHERE consumer_name='booking-notification'"));
        Properties props=new Properties(); props.put("bootstrap.servers",kafka.getBootstrapServers());
        props.put("group.id",UUID.randomUUID().toString()); props.put("auto.offset.reset","earliest");
        props.put("key.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        boolean found=false;
        try(var consumer=new KafkaConsumer<String,String>(props)) {
            consumer.subscribe(List.of("seat-confirmed")); long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!found && System.nanoTime()<deadline) for(var record:consumer.poll(Duration.ofSeconds(1))) {
                if(record.value().contains(booking.id().toString())) { assertNotNull(record.headers().lastHeader("outbox-id")); found=true; }
            }
        }
        assertTrue(found,"Committed confirmation must reach Kafka after recovery");
    }
    @Test void expiryRacingConfirmationCannotConfirmAnExpiredHold() throws Exception {
        holds.holdSeat(seat,owner);
        jdbc.update("UPDATE seat SET hold_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",seat);
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            var a=pool.submit(() -> { start.await(); expiry.releaseExpiredHolds(); return true; });
            var b=pool.submit(() -> { start.await(); assertThrows(RuntimeException.class, () -> bookings.confirmBooking(request(UUID.randomUUID().toString()))); return true; });
            start.countDown(); a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        }
        assertEquals("AVAILABLE",status()); assertNull(lock());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM booking WHERE event_id=?",Integer.class,event));
    }
}
