package com.inventory.purchaseorder.kafka;

import com.inventory.purchaseorder.event.OrderEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer producer;

    @Test
    void publishOrderEvent_sendsToConfiguredTopic() {
        ReflectionTestUtils.setField(producer, "orderEventsTopic", "order-events");
        OrderEvent event = OrderEvent.builder().purchaseOrderId(9L).eventId("evt").eventType("ORDER_CREATED").build();
        SendResult<String, OrderEvent> result = new SendResult<>(
                new ProducerRecord<>("order-events", "9", event),
                new RecordMetadata(new TopicPartition("order-events", 0), 0L, 1, 0L, 0L, 0, 0)
        );
        given(kafkaTemplate.send("order-events", "9", event)).willReturn(CompletableFuture.completedFuture(result));

        producer.publishOrderEvent(event);

        verify(kafkaTemplate).send("order-events", "9", event);
    }
}
