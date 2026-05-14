package com.inventory.supplier.mapper;

import com.inventory.supplier.dto.response.SupplierResponse;
import com.inventory.supplier.entity.Supplier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierMapperTest {

    private final SupplierMapperImpl mapper = new SupplierMapperImpl();

    @Test
    void toResponse_mapsAllFields() {
        Supplier supplier = Supplier.builder()
                .id(7L)
                .name("Acme Supplies")
                .contactPerson("Jane Buyer")
                .contactEmail("ops@acme.com")
                .contactPhone("9999999999")
                .address("Main Street")
                .city("Mumbai")
                .country("India")
                .gstin("27ABCDE1234F1Z5")
                .paymentTerms(30)
                .creditLimit(new BigDecimal("15000"))
                .notes("Priority vendor")
                .category("RAW_MATERIALS")
                .active(true)
                .createdAt(Instant.parse("2024-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2024-01-02T00:00:00Z"))
                .build();

        SupplierResponse response = mapper.toResponse(supplier);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Acme Supplies");
        assertThat(response.contactPerson()).isEqualTo("Jane Buyer");
        assertThat(response.contactEmail()).isEqualTo("ops@acme.com");
        assertThat(response.contactPhone()).isEqualTo("9999999999");
        assertThat(response.address()).isEqualTo("Main Street");
        assertThat(response.city()).isEqualTo("Mumbai");
        assertThat(response.country()).isEqualTo("India");
        assertThat(response.gstin()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(response.paymentTerms()).isEqualTo(30);
        assertThat(response.creditLimit()).isEqualByComparingTo("15000");
        assertThat(response.notes()).isEqualTo("Priority vendor");
        assertThat(response.category()).isEqualTo("RAW_MATERIALS");
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2024-01-02T00:00:00Z"));
    }
}
