package com.inventory.warehouse.config;

import com.inventory.warehouse.event.StockEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, StockEvent> stockEventProducerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Wait for all in-sync replicas to acknowledge — prevents data loss
        config.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retry up to 3 times on transient failures (network blip, leader election)
        config.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Idempotent producer — prevents duplicate messages on retry
        // Requires acks=all and max.in.flight.requests.per.connection=1
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        // Batch small messages together for efficiency (16 KB)
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);

        // Wait up to 5ms for more records before sending a batch
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // Fail fast if Kafka is completely unreachable instead of hanging the API for 60 seconds
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);

        // Don't include Spring type headers — keeps events schema-agnostic
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, StockEvent> kafkaTemplate() {
        return new KafkaTemplate<>(stockEventProducerFactory());
    }
}