package com.inventory.supplier.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierTest {

    @Test
    void builder_appliesDefaults() {
        Supplier supplier = Supplier.builder()
                .name("Acme")
                .contactEmail("ops@acme.com")
                .build();

        assertThat(supplier.getCountry()).isEqualTo("India");
        assertThat(supplier.getPaymentTerms()).isEqualTo(30);
        assertThat(supplier.getActive()).isTrue();
    }

    @Test
    void lifecycleCallbacks_manageTimestamps() {
        Supplier supplier = Supplier.builder()
                .name("Acme")
                .contactEmail("ops@acme.com")
                .build();

        supplier.onCreate();
        Instant createdAt = supplier.getCreatedAt();
        Instant updatedAt = supplier.getUpdatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();

        supplier.onUpdate();
        assertThat(supplier.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }
}
