package com.inventory.supplier.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Size(max = 100)
    @Column(name = "contact_person")
    private String contactPerson;

    @NotBlank
    @Email
    @Column(name = "contact_email", unique = true, nullable = false)
    private String contactEmail;

    @Size(max = 20)
    @Column(name = "contact_phone")
    private String contactPhone;

    @Size(max = 500)
    private String address;

    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    @Builder.Default
    private String country = "India";

    @Size(max = 20)
    private String gstin;

    @Min(0)
    @Max(365)
    @Column(name = "payment_terms", nullable = false)
    @Builder.Default
    private Integer paymentTerms = 30;

    @DecimalMin("0.0")
    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Size(max = 50)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
