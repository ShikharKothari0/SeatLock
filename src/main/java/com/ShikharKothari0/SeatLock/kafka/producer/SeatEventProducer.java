package com.ShikharKothari0.SeatLock.kafka.producer;

import com.ShikharKothari0.SeatLock.config.KafkaTopicConfig;
import com.ShikharKothari0.SeatLock.kafka.event.SeatConfirmedEvent;
import com.ShikharKothari0.SeatLock.kafka.event.SeatHeldEvent;
import com.ShikharKothari0.SeatLock.kafka.event.SeatReleasedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
public class SeatEventProducer {
    private static final Logger log = LoggerFactory.getLogger(SeatEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SeatEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishSeatHeld(SeatHeldEvent event) {
        publish(KafkaTopicConfig.TOPIC_SEAT_HELD, event.seatId().toString(), event);
    }

    public void publishSeatConfirmed(SeatConfirmedEvent event) {
        publish(KafkaTopicConfig.TOPIC_SEAT_CONFIRMED, event.bookingId().toString(), event);
    }

    public void publishSeatReleased(SeatReleasedEvent event) {
        publish(KafkaTopicConfig.TOPIC_SEAT_RELEASED, event.seatId().toString(), event);
    }

    private void publish(String topic, String key, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka event for topic {}: {}", topic, e.getMessage());
            return;
        }

        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(topic, key, json);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage());
            } else {
                log.debug("Published event to topic {} partition {} offset {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            }
        });
    }
}
