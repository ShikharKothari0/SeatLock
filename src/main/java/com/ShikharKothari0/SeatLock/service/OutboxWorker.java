package com.ShikharKothari0.SeatLock.service;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxWorker {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final RedisLockService locks;
    public OutboxWorker(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, RedisLockService locks) {
        this.jdbc = jdbc; this.kafka = kafka; this.locks = locks;
    }
    @Transactional
    public boolean dispatchOne() {
        var rows = jdbc.queryForList("SELECT * FROM reservation_outbox WHERE delivered_at IS NULL AND available_at<=CURRENT_TIMESTAMP ORDER BY available_at,created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED");
        if (rows.isEmpty()) return false;
        var row = rows.getFirst();
        try {
            if ("KAFKA".equals(row.get("kind"))) {
                var record = new ProducerRecord<String,String>((String) row.get("destination"), (String) row.get("message_key"), (String) row.get("payload"));
                record.headers().add("outbox-id", row.get("id").toString().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(5, TimeUnit.SECONDS);
            } else if ("REDIS_RELEASE".equals(row.get("kind"))) {
                locks.releaseOwnedLock((String) row.get("destination"), (String) row.get("payload"));
            } else throw new IllegalStateException("Unknown outbox kind");
            jdbc.update("UPDATE reservation_outbox SET delivered_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=?", row.get("id"));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            int delay = Math.min(60, 1 << Math.min(6, ((Number) row.get("attempts")).intValue()));
            jdbc.update("UPDATE reservation_outbox SET attempts=attempts+1,available_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),last_error=? WHERE id=?",
                    delay, error.getClass().getSimpleName(), row.get("id"));
        }
        return true;
    }
}
