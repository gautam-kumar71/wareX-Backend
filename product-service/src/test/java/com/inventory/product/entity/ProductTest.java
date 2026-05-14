package com.inventory.product.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void builder_defaultsAreApplied() {
        Product product = Product.builder()
                .name("Widget")
                .sku("SKU-1")
                .build();

        assertThat(product.isActive()).isTrue();
        assertThat(product.getTotalStock()).isZero();
        assertThat(product.getAllocatedStock()).isZero();
        assertThat(product.getReorderLevel()).isEqualTo(10);
        assertThat(product.getMaxStockLevel()).isEqualTo(100);
    }

    @Test
    void constructorsAndAccessors_roundTripValues() {
        Product product = new Product(
                9L,
                "SKU-9",
                "Widget Pro",
                "Detailed description",
                "Electronics",
                99.0,
                70.0,
                18.0,
                1.5,
                10.0,
                5.0,
                2.0,
                "kg",
                "cm",
                "legacy",
                false,
                12,
                3,
                4,
                80
        );

        assertThat(product.getId()).isEqualTo(9L);
        assertThat(product.getSku()).isEqualTo("SKU-9");
        assertThat(product.getName()).isEqualTo("Widget Pro");
        assertThat(product.getDescription()).isEqualTo("Detailed description");
        assertThat(product.getCategory()).isEqualTo("Electronics");
        assertThat(product.getPrice()).isEqualTo(99.0);
        assertThat(product.getCostPrice()).isEqualTo(70.0);
        assertThat(product.getTaxRate()).isEqualTo(18.0);
        assertThat(product.getWeight()).isEqualTo(1.5);
        assertThat(product.getLength()).isEqualTo(10.0);
        assertThat(product.getWidth()).isEqualTo(5.0);
        assertThat(product.getHeight()).isEqualTo(2.0);
        assertThat(product.getWeightUnit()).isEqualTo("kg");
        assertThat(product.getDimensionUnit()).isEqualTo("cm");
        assertThat(product.getUnit()).isEqualTo("legacy");
        assertThat(product.isActive()).isFalse();
        assertThat(product.getTotalStock()).isEqualTo(12);
        assertThat(product.getAllocatedStock()).isEqualTo(3);
        assertThat(product.getReorderLevel()).isEqualTo(4);
        assertThat(product.getMaxStockLevel()).isEqualTo(80);
    }

    @Test
    void setters_allowMutation() {
        Product product = new Product();

        product.setId(5L);
        product.setSku("SKU-5");
        product.setName("Updated");
        product.setDescription("desc");
        product.setCategory("tools");
        product.setPrice(40.0);
        product.setCostPrice(30.0);
        product.setTaxRate(5.0);
        product.setWeight(0.8);
        product.setLength(8.0);
        product.setWidth(4.0);
        product.setHeight(2.5);
        product.setWeightUnit("g");
        product.setDimensionUnit("mm");
        product.setUnit("legacy");
        product.setActive(true);
        product.setTotalStock(7);
        product.setAllocatedStock(2);
        product.setReorderLevel(6);
        product.setMaxStockLevel(60);

        assertThat(product.getId()).isEqualTo(5L);
        assertThat(product.getSku()).isEqualTo("SKU-5");
        assertThat(product.getName()).isEqualTo("Updated");
        assertThat(product.getDescription()).isEqualTo("desc");
        assertThat(product.getCategory()).isEqualTo("tools");
        assertThat(product.getPrice()).isEqualTo(40.0);
        assertThat(product.getCostPrice()).isEqualTo(30.0);
        assertThat(product.getTaxRate()).isEqualTo(5.0);
        assertThat(product.getWeight()).isEqualTo(0.8);
        assertThat(product.getLength()).isEqualTo(8.0);
        assertThat(product.getWidth()).isEqualTo(4.0);
        assertThat(product.getHeight()).isEqualTo(2.5);
        assertThat(product.getWeightUnit()).isEqualTo("g");
        assertThat(product.getDimensionUnit()).isEqualTo("mm");
        assertThat(product.getUnit()).isEqualTo("legacy");
        assertThat(product.isActive()).isTrue();
        assertThat(product.getTotalStock()).isEqualTo(7);
        assertThat(product.getAllocatedStock()).isEqualTo(2);
        assertThat(product.getReorderLevel()).isEqualTo(6);
        assertThat(product.getMaxStockLevel()).isEqualTo(60);
    }
}
