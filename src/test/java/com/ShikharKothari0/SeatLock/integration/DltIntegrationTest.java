package com.ShikharKothari0.SeatLock.integration;

import com.ShikharKothari0.SeatLock.kafka.consumer.SeatConfirmedConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DltIntegrationTest extends IntegrationTestBase{
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private SeatConfirmedConsumer seatConfirmedConsumer;

    @Autowired
    private TestKafkaEventCapture eventCapture;

    @BeforeEach
    void enableFailureSimulation() {
        eventCapture.clearAll();
        seatConfirmedConsumer.setSimulateFailure(true);
    }

    @AfterEach
    void disableFailureSimulation() {
        seatConfirmedConsumer.setSimulateFailure(false);
    }

    @Test
    void failedConsumerMessageLandsInDltAfterRetryExhaustion()
            throws InterruptedException
    {
        // publish a real-looking seat-confirmed message
        String payload = """
            {
                "bookingId": "00000000-0000-0000-0000-000000000099",
                "userId": "33333333-3333-3333-3333-333333333333",
                "eventId": "22222222-2222-2222-2222-222222222222",
                "seatIds": ["aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"],
                "confirmedAt": "2026-07-01T13:00:00Z"
            }
            """;

        kafkaTemplate.send(
                "seat-confirmed",
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                payload
        );

        // wait for DLT message (4 delivery attempts (initial attempt + 3 retries) × 1s backoff + processing time)
        // total: ~10 seconds for retries + consumer processing so 15s timeout gives comfortable buffer
        ConsumerRecord<String, String> dltRecord =
                eventCapture.dltEvents().poll(15, TimeUnit.SECONDS);

        // assert: message landed in DLT
        assertThat(dltRecord)
                .as("Failed message must land in seat-confirmed.DLT after retries exhausted")
                .isNotNull();

        assertThat(dltRecord.topic())
                .as("DLT topic name must be seat-confirmed.DLT")
                .isEqualTo("seat-confirmed.DLT");

        assertThat(dltRecord.value())
                .as("DLT message must preserve original payload intact")
                .contains("00000000-0000-0000-0000-000000000099");

        // assert: DLT headers contain forensic information
        var exceptionClassHeader =
                dltRecord.headers().lastHeader("kafka_dlt-exception-fqcn");

        assertThat(exceptionClassHeader)
                .as("DLT message must carry the exception class header")
                .isNotNull();

        String exceptionClass =
                new String(exceptionClassHeader.value(), StandardCharsets.UTF_8);

        assertThat(exceptionClass)
                .as("Spring Kafka wraps listener exceptions in " +
                        "ListenerExecutionFailedException — not the raw RuntimeException")
                .contains("ListenerExecutionFailedException");

        // assert: exception message header
        var exceptionMessageHeader =
                dltRecord.headers().lastHeader("kafka_dlt-exception-message");

        assertThat(exceptionMessageHeader)
                .as("DLT message must carry the exception message header")
                .isNotNull();

        String exceptionMessage =
                new String(exceptionMessageHeader.value(), StandardCharsets.UTF_8);

        assertThat(exceptionMessage)
                .as("Exception message must confirm simulated failure was the cause")
                .contains("Simulated processing failure");

        // check the original topic header
        var originalTopicHeader =
                dltRecord.headers().lastHeader("kafka_dlt-original-topic");

        assertThat(originalTopicHeader)
                .as("DLT message must carry original topic header")
                .isNotNull();

        assertThat(new String(originalTopicHeader.value(), java.nio.charset.StandardCharsets.UTF_8))
                .as("Original topic must be seat-confirmed")
                .isEqualTo("seat-confirmed");

        // ── assert: original partition header ─────────────────────────────────
        // partition/offset headers are stored as serialized int/long byte arrays
        // NOT as UTF-8 strings — must use ByteBuffer to read them
        var originalPartitionHeader =
                dltRecord.headers().lastHeader("kafka_dlt-original-partition");

        assertThat(originalPartitionHeader)
                .as("DLT message must carry original partition header")
                .isNotNull();

        int originalPartition =
                ByteBuffer.wrap(originalPartitionHeader.value()).getInt();

        assertThat(originalPartition)
                .as("Original partition must be a valid partition number")
                .isGreaterThanOrEqualTo(0);

        // ── assert: original offset header ────────────────────────────────────
        var originalOffsetHeader =
                dltRecord.headers().lastHeader("kafka_dlt-original-offset");

        assertThat(originalOffsetHeader)
                .as("DLT message must carry original offset header")
                .isNotNull();

        long originalOffset =
                ByteBuffer.wrap(originalOffsetHeader.value()).getLong();

        assertThat(originalOffset)
                .as("Original offset must be a valid non-negative offset")
                .isGreaterThanOrEqualTo(0L);
    }
}
