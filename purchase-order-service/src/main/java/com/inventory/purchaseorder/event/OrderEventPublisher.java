package com.inventory.purchaseorder.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void fireEvent(OrderEvent event) {
        log.debug("Firing application event for OrderEvent: {}", event.getEventId());
        applicationEventPublisher.publishEvent(event);
    }
}
