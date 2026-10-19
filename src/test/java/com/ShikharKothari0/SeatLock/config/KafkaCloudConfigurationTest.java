package com.ShikharKothari0.SeatLock.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.apache.kafka.clients.admin.NewTopic;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaCloudConfigurationTest {
    @Test void failurePublisherRetainsAuthenticationAndTls() {
        var properties = new KafkaProperties();
        properties.getProperties().put("security.protocol", "SASL_SSL");
        properties.getProperties().put("sasl.mechanism", "SCRAM-SHA-256");
        properties.getProperties().put("sasl.jaas.config", "test-jaas-value");
        properties.getProperties().put("ssl.truststore.certificates", "test-ca");
        var factory = new KafkaConsumerConfig().dltProducerFactory(properties);
        assertThat(factory.getConfigurationProperties()).containsAllEntriesOf(properties.getProperties());
        assertThat(factory.getConfigurationProperties()).containsEntry("enable.idempotence", true);
    }
    @Test void managedProviderDoesNotAttemptTopicCreation() {
        new ApplicationContextRunner().withUserConfiguration(KafkaTopicConfig.class)
            .withPropertyValues("seatlock.kafka.manage-topics=false")
            .run(context -> assertThat(context.getBeansOfType(NewTopic.class)).isEmpty());
    }
    @Test void configurablePartitionCountFitsFreeProvider() {
        new ApplicationContextRunner().withUserConfiguration(KafkaTopicConfig.class)
            .withPropertyValues("seatlock.kafka.partitions=2")
            .run(context -> {
                assertThat(context.getBeansOfType(NewTopic.class)).hasSize(5);
                context.getBeansOfType(NewTopic.class).values().forEach(topic -> assertThat(topic.numPartitions()).isEqualTo(2));
            });
    }
}
