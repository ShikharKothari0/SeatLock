package com.ShikharKothari0.SeatLock.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {
    private static final Logger log =
            LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // dedicated producer factory for DLT publishing
    @Bean("dltProducerFactory")
    public ProducerFactory<String, String> dltProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean("dltKafkaTemplate")
    public KafkaTemplate<String, String> dltKafkaTemplate(
            @Qualifier("dltProducerFactory") ProducerFactory<String, String> dltproducerFactory
    ) {
        return new KafkaTemplate<>(dltProducerFactory());
    }

    // error handler: retry 3×, then publish to DLT
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            @Qualifier("dltKafkaTemplate") KafkaTemplate<String, String> dltKafkaTemplate
    ) {
        // 3 retries, 1 second apart
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        // after retries exhausted → publish failed message to <topic>.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) ->{
                    if (record.topic().equals(KafkaTopicConfig.TOPIC_SEAT_CONFIRMED)) {
                        return new TopicPartition(
                                KafkaTopicConfig.TOPIC_SEAT_CONFIRMED_DLT, -1
                        );
                }
                    if(record.topic().equals(KafkaTopicConfig.TOPIC_SEAT_RELEASED)) {
                        return new TopicPartition(
                                KafkaTopicConfig.TOPIC_SEAT_RELEASED_DLT, -1
                        );
                    }
                    // fallback for any other topic
                    return new TopicPartition(record.topic() + ".DLT", -1);
                }
        );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, backOff);

        // log every retry with full context
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn(
                        "Kafka retry attempt {}/{} — topic={} partition={} " +
                                "offset={} key={} error={}",
                        deliveryAttempt,
                        backOff.getMaxAttempts(),
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        ex.getMessage()
                )
        );

        // malformed JSON will never succeed on retry. So skip straight to DLT
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);

        return errorHandler;
    }
}
