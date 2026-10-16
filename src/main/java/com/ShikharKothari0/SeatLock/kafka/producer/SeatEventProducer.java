package com.ShikharKothari0.SeatLock.kafka.producer;

import com.ShikharKothari0.SeatLock.config.KafkaTopicConfig;
import com.ShikharKothari0.SeatLock.kafka.event.*;
import com.ShikharKothari0.SeatLock.service.TransactionEffects;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SeatEventProducer {
    private final TransactionEffects effects;
    private final ObjectMapper mapper;
    public SeatEventProducer(TransactionEffects effects, ObjectMapper mapper) { this.effects=effects; this.mapper=mapper; }
    public void publishSeatHeld(SeatHeldEvent event) { publish(KafkaTopicConfig.TOPIC_SEAT_HELD, event.seatId().toString(), event); }
    public void publishSeatConfirmed(SeatConfirmedEvent event) { publish(KafkaTopicConfig.TOPIC_SEAT_CONFIRMED, event.bookingId().toString(), event); }
    public void publishSeatReleased(SeatReleasedEvent event) { publish(KafkaTopicConfig.TOPIC_SEAT_RELEASED, event.seatId().toString(), event); }
    private void publish(String topic, String key, Object event) {
        try { effects.enqueue("KAFKA", topic, key, mapper.writeValueAsString(event)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Cannot serialize reservation event", error); }
    }
}
