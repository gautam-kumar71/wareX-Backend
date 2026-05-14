package com.inventory.stockmovement.config;

import com.inventory.stockmovement.kafka.StockEventPayload;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void consumerFactory_containsExpectedProperties() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        ConsumerFactory<String, StockEventPayload> factory = config.consumerFactory();
        Map<String, Object> properties = factory.getConfigurationProperties();

        assertThat(properties.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(properties.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("stock-movement-service");
        assertThat(properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        assertThat(properties.get(JsonDeserializer.VALUE_DEFAULT_TYPE)).isEqualTo(StockEventPayload.class.getName());
    }

    @Test
    void kafkaListenerContainerFactory_setsAckModeAndConcurrency() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        ConcurrentKafkaListenerContainerFactory<String, StockEventPayload> factory =
                config.kafkaListenerContainerFactory();

        assertThat(factory.getConsumerFactory()).isNotNull();
        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(ReflectionTestUtils.getField(factory, "concurrency")).isEqualTo(3);
    }
}
