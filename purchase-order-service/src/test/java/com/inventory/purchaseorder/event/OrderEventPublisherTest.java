package com.inventory.purchaseorder.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private OrderEventPublisher publisher;

    @Test
    void fireEvent_publishesApplicationEvent() {
        OrderEvent event = OrderEvent.builder().eventId("evt-1").build();

        publisher.fireEvent(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
