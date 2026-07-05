package com.ShikharKothari0.SeatLock.kafka.consumer;

import com.ShikharKothari0.SeatLock.config.KafkaTopicConfig;
import com.ShikharKothari0.SeatLock.entity.Seat;
import com.ShikharKothari0.SeatLock.entity.SeatStatus;
import com.ShikharKothari0.SeatLock.kafka.event.SeatReleasedEvent;
import com.ShikharKothari0.SeatLock.repository.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
public class SeatReleasedConsumer {
    private static final Logger log = LoggerFactory.getLogger(SeatReleasedConsumer.class);

    private final ObjectMapper objectMapper;
    private final SeatRepository seatRepository;

    public SeatReleasedConsumer(
            ObjectMapper objectMapper,
            SeatRepository seatRepository
    ) {
        this.objectMapper = objectMapper;
        this.seatRepository = seatRepository;
    }

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_SEAT_RELEASED,
            groupId = KafkaTopicConfig.GROUP_RECONCILIATION,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onSeatReleased(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        try {
            SeatReleasedEvent event = objectMapper.readValue(message, SeatReleasedEvent.class);

            log.info(
                    "[RECONCILIATION] Seat released event received — seatId={} reason={} " +
                            "partition={} offset={}",
                    event.seatId(),
                    event.reason(),
                    partition,
                    offset
            );

            reconcileSeatState(event.seatId(), event.reason());

        } catch (Exception e) {
            log.error(
                    "[RECONCILIATION] Failed to process seat-released message " +
                            "at partition={} offset={}: {}",
                    partition, offset, e.getMessage(), e
            );
            // re-throw so Spring Kafka retries — reconciliation failures
            // are infrastructure problems worth retrying, unlike
            // notification failures which risk duplicates
            throw new RuntimeException("Reconciliation failed for seat-released event", e);
        }
    }

    private void reconcileSeatState(UUID seatId, String reason) {
        Optional<Seat> seatOptional = seatRepository.findById(seatId);

        if (seatOptional.isEmpty()) {
            log.warn("[RECONCILIATION] Seat {} not found in DB during reconciliation — skipping",
                    seatId);
            return;
        }

        Seat seat = seatOptional.get();

        if (seat.getStatus() == SeatStatus.AVAILABLE) {
            // happy path: HoldExpiryService already flipped it — nothing to do
            log.info(
                    "[RECONCILIATION] Seat {} already AVAILABLE in DB — consistent state confirmed",
                    seatId
            );
            return;
        }

        if (seat.getStatus() == SeatStatus.CONFIRMED) {
            // seat was confirmed before the release event was processed —
            // this is fine, the release event arrived after a confirm.
            // Log it as info, not a warning, since this is a valid race.
            log.info(
                    "[RECONCILIATION] Seat {} is CONFIRMED — release event arrived after confirm, " +
                            "no action needed",
                    seatId
            );
            return;
        }

        if (seat.getStatus() == SeatStatus.HELD) {
            // unexpected: seat is still HELD in Postgres even though a
            // released event was published. HoldExpiryService should have
            // caught this — but it didn't. Force the correction here.
            log.warn(
                    "[RECONCILIATION] Seat {} is still HELD in DB despite release event (reason={}). " +
                            "Forcing status to AVAILABLE.",
                    seatId, reason
            );
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHoldExpiresAt(null);
            seat.setBooking(null);
            seatRepository.save(seat);
            log.info("[RECONCILIATION] Seat {} forcefully released to AVAILABLE", seatId);
        }
    }
}
