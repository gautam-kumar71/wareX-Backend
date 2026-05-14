package com.inventory.product.kafka;

import com.inventory.product.entity.Product;
import com.inventory.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventConsumerTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private StockEventConsumer stockEventConsumer;

    @Test
    void consume_updatesExistingProductStock() {
        Product product = Product.builder().id(5L).name("Widget").totalStock(7).build();
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        stockEventConsumer.consume(StockEventPayload.builder().productId(5L).quantityDelta(3).build());

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalStock()).isEqualTo(10);
    }

    @Test
    void consume_usesZeroWhenCurrentStockIsNull() {
        Product product = Product.builder().id(6L).name("Widget").totalStock(null).build();
        when(productRepository.findById(6L)).thenReturn(Optional.of(product));

        stockEventConsumer.consume(StockEventPayload.builder().productId(6L).quantityDelta(4).build());

        verify(productRepository).save(product);
        assertThat(product.getTotalStock()).isEqualTo(4);
    }

    @Test
    void consume_skipsMissingProduct() {
        when(productRepository.findById(8L)).thenReturn(Optional.empty());

        stockEventConsumer.consume(StockEventPayload.builder().productId(8L).quantityDelta(1).build());

        verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any(Product.class));
    }
}
