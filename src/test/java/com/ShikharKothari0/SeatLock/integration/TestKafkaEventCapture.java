package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.config.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Profile("test")
public class TestKafkaEventCapture {
    private final BlockingQueue<ConsumerRecord<String, String>> heldEvents =
            new LinkedBlockingQueue<>();
    private final BlockingQueue<ConsumerRecord<String, String>> confirmedEvents =
            new LinkedBlockingQueue<>();
    private final BlockingQueue<ConsumerRecord<String, String>> releasedEvents =
            new LinkedBlockingQueue<>();
    private final BlockingQueue<ConsumerRecord<String, String>> dltEvents =
            new LinkedBlockingQueue<>();

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_SEAT_HELD,
            groupId = "test-capture-held"
    )
    public void captureSeatHeld(ConsumerRecord<String, String> record) {
        heldEvents.add(record);
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_SEAT_CONFIRMED,
            groupId = "test-capture-confirmed"
    )
    public void captureSeatConfirmed(ConsumerRecord<String, String> record) {
        confirmedEvents.add(record);
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_SEAT_RELEASED,
            groupId = "test-capture-released"
    )
    public void captureSeatReleased(ConsumerRecord<String, String> record) {
        releasedEvents.add(record);
    }

    @KafkaListener(
            topics = {
                    KafkaTopicConfig.TOPIC_SEAT_CONFIRMED_DLT,
                    KafkaTopicConfig.TOPIC_SEAT_RELEASED_DLT
            },
            groupId = "test-capture-dlt"
    )
    public void captureDlt(ConsumerRecord<String, String> record) {
        dltEvents.add(record);
    }

    // accessors for test assertions

    public BlockingQueue<ConsumerRecord<String, String>> heldEvents() {
        return heldEvents;
    }

    public BlockingQueue<ConsumerRecord<String, String>> confirmedEvents() {
        return confirmedEvents;
    }

    public BlockingQueue<ConsumerRecord<String, String>> releasedEvents() {
        return releasedEvents;
    }

    public BlockingQueue<ConsumerRecord<String, String>> dltEvents() {
        return dltEvents;
    }

    public void clearAll() {
        heldEvents.clear();
        confirmedEvents.clear();
        releasedEvents.clear();
        dltEvents.clear();
    }
}
