package com.inventory.product.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;
    private String category;
    private Double price;
    private Double costPrice;
    private Double taxRate;

    // Physical dimensions & Logistics
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private String weightUnit;
    private String dimensionUnit;
    @Deprecated
    private String unit;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Integer totalStock = 0;

    @Builder.Default
    private Integer allocatedStock = 0;

    @Column(name = "reorder_level")
    @Builder.Default
    private Integer reorderLevel = 10;

    @Column(name = "max_stock_level")
    @Builder.Default
    private Integer maxStockLevel = 100;
}
