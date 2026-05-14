package com.inventory.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String country = "India";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Logistics & Capacity Constraints
    @Column(name = "total_storage_capacity")
    private Integer totalStorageCapacity;

    @Column(name = "current_capacity_utilization")
    @Builder.Default
    private Integer currentCapacityUtilization = 0;

    @Column(name = "manager_name", length = 100)
    private String managerName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // One warehouse has many stock level entries (one per product)
    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StockLevel> stockLevels = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}