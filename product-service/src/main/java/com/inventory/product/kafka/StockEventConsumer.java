package com.inventory.product.kafka;

import com.inventory.product.entity.Product;
import com.inventory.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventConsumer {

    private final ProductRepository productRepository;

    @KafkaListener(topics = "stock-events", groupId = "product-service")
    @Transactional
    public void consume(StockEventPayload event) {
        log.info("Received stock event: product={}, delta={}", event.getProductId(), event.getQuantityDelta());

        productRepository.findById(event.getProductId()).ifPresent(product -> {
            int newTotal = (product.getTotalStock() != null ? product.getTotalStock() : 0) + event.getQuantityDelta();
            product.setTotalStock(newTotal);
            productRepository.save(product);
            log.info("Updated product {} total stock to {}", product.getName(), newTotal);
        });
    }
}
