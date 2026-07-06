package com.ShikharKothari0.SeatLock.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
public class DeadLetterQueueConsumer {
    private static final Logger log =
            LoggerFactory.getLogger(DeadLetterQueueConsumer.class);

    @KafkaListener(
            topics = {
                    "#{T(com.ShikharKothari0.SeatLock.config.KafkaTopicConfig).TOPIC_SEAT_CONFIRMED_DLT}",
                    "#{T(com.ShikharKothari0.SeatLock.config.KafkaTopicConfig).TOPIC_SEAT_RELEASED_DLT}"
            },
            groupId = "seatlock-dlq-monitor-group"
    )
    public void onDeadLetterMessage(ConsumerRecord<String, String> record) {
        String originalTopic     = extractHeader(record, "kafka_dlt-original-topic");
        String originalPartition = extractHeader(record, "kafka_dlt-original-partition");
        String originalOffset    = extractHeader(record, "kafka_dlt-original-offset");
        String exceptionClass    = extractHeader(record, "kafka_dlt-exception-fqcn");
        String exceptionMessage  = extractHeader(record, "kafka_dlt-exception-message");

        log.error(
                "[DLQ ALERT] Dead letter message received — " +
                        "dltTopic={} key={} originalTopic={} partition={} offset={} " +
                        "exceptionClass={} exceptionMessage={} payload={}",
                record.topic(),
                record.key(),
                originalTopic,
                originalPartition,
                originalOffset,
                exceptionClass,
                exceptionMessage,
                record.value()
        );

    }
    private String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) return "unknown";
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
