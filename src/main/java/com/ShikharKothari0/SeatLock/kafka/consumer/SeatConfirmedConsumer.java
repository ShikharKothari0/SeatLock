package com.ShikharKothari0.SeatLock.kafka.consumer;

import com.ShikharKothari0.SeatLock.config.KafkaTopicConfig;
import com.ShikharKothari0.SeatLock.kafka.event.SeatConfirmedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class SeatConfirmedConsumer {
    private static final Logger log = LoggerFactory.getLogger(SeatConfirmedConsumer.class);

    private final ObjectMapper objectMapper;
    @Setter
    private volatile boolean simulateFailure = false;       // It is a purely testing tool. to be toggled only during testing.

    public SeatConfirmedConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_SEAT_CONFIRMED,
            groupId = KafkaTopicConfig.GROUP_NOTIFICATION,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSeatConfirmed(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        //  the simulation hook must be before the try-catch.
        // If inside the try-catch, the RuntimeException gets swallowed and
        // never reaches the error handler — retries and DLT won't trigger
        if (simulateFailure) {
            throw new RuntimeException(
                    "Simulated processing failure — partition=" + partition + " offset=" + offset
            );
        }

        try {
            SeatConfirmedEvent event = objectMapper.readValue(message, SeatConfirmedEvent.class);

            log.info(
                    "[NOTIFICATION] Booking confirmed — bookingId={} userId={} eventId={} seats={} " +
                            "partition={} offset={}",
                    event.bookingId(),
                    event.userId(),
                    event.eventId(),
                    event.seatIds(),
                    partition,
                    offset
            );
            // In a real system this would call an EmailService or
            // push a notification via FCM/SNS.
            sendConfirmationEmail(event);

        } catch (Exception e) {
            log.error(
                    "[NOTIFICATION] Failed to process seat-confirmed message " +
                            "at partition={} offset={}: {}",
                    partition, offset, e.getMessage(), e
            );
            // intentionally not re-throwing — a failed notification
            // should not cause the consumer to retry and create duplicate
            // notification attempts. Log it, alert (in a real system via
            // a metrics counter), and move on.
        }
    }

    private void sendConfirmationEmail(SeatConfirmedEvent event) {
        // replace with real email/notification logic later
        log.info(
                "[NOTIFICATION] → Confirmation email would be sent to userId={} " +
                        "for booking={} covering {} seat(s)",
                event.userId(),
                event.bookingId(),
                event.seatIds().size()
        );
    }
}
