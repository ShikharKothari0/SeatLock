package com.ShikharKothari0.SeatLock.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    public static final String TOPIC_SEAT_HELD = "seat-held";
    public static final String TOPIC_SEAT_CONFIRMED = "seat-confirmed";
    public static final String TOPIC_SEAT_RELEASED = "seat-released";
    public static final String GROUP_NOTIFICATION = "seatlock-notification-group";     // Consumer group IDs - separate groups so both consumers
    public static final String GROUP_RECONCILIATION = "seatlock-reconciliation-group"; // process every message independently, not compete for messages

    @Bean
    public NewTopic seatHeldTopic() {
        return TopicBuilder.name(TOPIC_SEAT_HELD)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seatConfirmedTopic() {
        return TopicBuilder.name(TOPIC_SEAT_CONFIRMED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seatReleasedTopic() {
        return TopicBuilder.name(TOPIC_SEAT_RELEASED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
