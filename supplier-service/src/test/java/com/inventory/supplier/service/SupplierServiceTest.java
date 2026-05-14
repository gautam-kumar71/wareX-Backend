package com.inventory.supplier.service;

import com.inventory.supplier.dto.request.CreateSupplierRequest;
import com.inventory.supplier.dto.request.UpdateSupplierRequest;
import com.inventory.supplier.dto.response.SupplierDeactivationCheckResponse;
import com.inventory.supplier.dto.response.SupplierResponse;
import com.inventory.supplier.entity.Supplier;
import com.inventory.supplier.exception.DuplicateEmailException;
import com.inventory.supplier.exception.SupplierDeactivationBlockedException;
import com.inventory.supplier.exception.SupplierDependencyUnavailableException;
import com.inventory.supplier.exception.SupplierNotFoundException;
import com.inventory.supplier.feign.PurchaseOrderClient;
import com.inventory.supplier.mapper.SupplierMapper;
import com.inventory.supplier.repository.SupplierRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepo;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private PurchaseOrderClient purchaseOrderClient;

    @Mock
    private SupplierMailService supplierMailService;

    @InjectMocks
    private SupplierService supplierService;

    private Supplier supplier;
    private SupplierResponse response;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
        supplier = Supplier.builder()
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
        response = toResponse(supplier);
    }

    @Test
    void create_normalizesFieldsAndDefaultsPaymentTerms() {
        CreateSupplierRequest request = new CreateSupplierRequest(
                "  Acme   Supplies  ",
                "  Jane   Buyer ",
                "  OPS@ACME.COM ",
                " 9999999999 ",
                " Main  Street ",
                " Mumbai ",
                " india ",
                "27abcde1234f1z5",
                null,
                new BigDecimal("5000"),
                "  Preferred   supplier ",
                "raw materials"
        );

        given(supplierRepo.existsByContactEmail("ops@acme.com")).willReturn(false);
        given(supplierRepo.save(any(Supplier.class))).willAnswer(invocation -> {
            Supplier saved = invocation.getArgument(0);
            saved.setId(11L);
            saved.setActive(true);
            return saved;
        });
        given(supplierMapper.toResponse(any(Supplier.class))).willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        SupplierResponse created = supplierService.create(request);

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        then(supplierRepo).should().save(captor.capture());
        Supplier saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Acme Supplies");
        assertThat(saved.getContactPerson()).isEqualTo("Jane Buyer");
        assertThat(saved.getContactEmail()).isEqualTo("ops@acme.com");
        assertThat(saved.getCountry()).isEqualTo("india");
        assertThat(saved.getGstin()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(saved.getPaymentTerms()).isEqualTo(30);
        assertThat(saved.getCategory()).isEqualTo("RAW_MATERIALS");
        assertThat(saved.getNotes()).isEqualTo("Preferred supplier");
        assertThat(created.name()).isEqualTo("Acme Supplies");
    }

    @Test
    void create_throwsWhenEmailAlreadyExists() {
        given(supplierRepo.existsByContactEmail("ops@acme.com")).willReturn(true);

        assertThatThrownBy(() -> supplierService.create(new CreateSupplierRequest(
                "Acme", "Jane", "ops@acme.com", "9999999999", "Street", "Mumbai", "India",
                null, 30, BigDecimal.ONE, null, "raw materials"
        )))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("ops@acme.com");
    }

    @Test
    void search_blankQueryFallsBackToFindAll() {
        Page<Supplier> page = new PageImpl<>(List.of(supplier), pageable, 1);
        given(supplierRepo.findAll(pageable)).willReturn(page);
        given(supplierMapper.toResponse(supplier)).willReturn(response);

        Page<SupplierResponse> result = supplierService.search("   ", null, pageable);

        assertThat(result.getContent()).containsExactly(response);
        then(supplierRepo).should().findAll(pageable);
    }

    @Test
    void search_blankQueryWithActiveFilterUsesFilteredLookup() {
        Page<Supplier> page = new PageImpl<>(List.of(supplier), pageable, 1);
        given(supplierRepo.findByActive(true, pageable)).willReturn(page);
        given(supplierMapper.toResponse(supplier)).willReturn(response);

        Page<SupplierResponse> result = supplierService.search(null, true, pageable);

        assertThat(result.getContent()).containsExactly(response);
        then(supplierRepo).should().findByActive(true, pageable);
    }

    @Test
    void search_numericQueryWithActiveFilterReturnsSingleResultPage() {
        given(supplierRepo.findFirstByIdAndActive(7L, true)).willReturn(Optional.of(supplier));
        given(supplierMapper.toResponse(supplier)).willReturn(response);

        Page<SupplierResponse> result = supplierService.search("7", true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(response);
        then(supplierRepo).should().findFirstByIdAndActive(7L, true);
    }

    @Test
    void search_numericQueryWithoutMatchReturnsEmptyPage() {
        given(supplierRepo.findById(404L)).willReturn(Optional.empty());

        Page<SupplierResponse> result = supplierService.search("404", null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void search_textQueryDelegatesToActiveSearch() {
        Page<Supplier> page = new PageImpl<>(List.of(supplier), pageable, 1);
        given(supplierRepo.searchByActive("acme", true, pageable)).willReturn(page);
        given(supplierMapper.toResponse(supplier)).willReturn(response);

        Page<SupplierResponse> result = supplierService.search("acme", true, pageable);

        assertThat(result.getContent()).containsExactly(response);
        then(supplierRepo).should().searchByActive("acme", true, pageable);
    }

    @Test
    void getAllActive_mapsRepositoryResults() {
        given(supplierRepo.findByActiveTrue()).willReturn(List.of(supplier));
        given(supplierMapper.toResponse(supplier)).willReturn(response);

        List<SupplierResponse> result = supplierService.getAllActive();

        assertThat(result).containsExactly(response);
    }

    @Test
    void update_throwsWhenChangingToDuplicateEmail() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(supplierRepo.existsByContactEmailAndIdNot("duplicate@acme.com", 7L)).willReturn(true);

        assertThatThrownBy(() -> supplierService.update(7L, new UpdateSupplierRequest(
                null, null, "duplicate@acme.com", null, null, null, null, null,
                null, null, null, null, null
        )))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void update_reactivatesSupplierAndSendsReactivationNotice() {
        supplier.setActive(false);
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(supplierRepo.save(any(Supplier.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(supplierMapper.toResponse(any(Supplier.class))).willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        SupplierResponse updated = supplierService.update(7L, new UpdateSupplierRequest(
                "  Acme   Prime ", null, null, null, null, null, null, null,
                null, null, "  Reactivated vendor ", "contract supplier", true
        ));

        assertThat(updated.name()).isEqualTo("Acme Prime");
        assertThat(updated.active()).isTrue();
        assertThat(updated.notes()).isEqualTo("Reactivated vendor");
        assertThat(updated.category()).isEqualTo("CONTRACT_SUPPLIER");
        then(supplierMailService).should().sendReactivationNotice(any(Supplier.class));
    }

    @Test
    void update_deactivatesSupplierAndSendsSuspensionNotice() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(supplierRepo.save(any(Supplier.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(supplierMapper.toResponse(any(Supplier.class))).willAnswer(invocation -> toResponse(invocation.getArgument(0)));

        SupplierResponse updated = supplierService.update(7L, new UpdateSupplierRequest(
                null, null, null, null, null, null, null, null,
                45, null, "", null, false
        ));

        assertThat(updated.active()).isFalse();
        assertThat(updated.paymentTerms()).isEqualTo(45);
        assertThat(updated.notes()).isNull();
        then(supplierMailService).should().sendSuspensionNotice(any(Supplier.class));
    }

    @Test
    void deactivate_returnsEarlyWhenAlreadyInactive() {
        supplier.setActive(false);
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));

        supplierService.deactivate(7L);

        then(supplierRepo).should(never()).save(any(Supplier.class));
        then(supplierMailService).should(never()).sendSuspensionNotice(any(Supplier.class));
    }

    @Test
    void deactivate_throwsWhenPurchaseOrdersBlockDeactivation() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willReturn(new PurchaseOrderClient.ApiResponse<>(
                        new PurchaseOrderClient.SupplierDeactivationCheckResponse(
                                false, 2, List.of("APPROVED"), List.of("PO-1"),
                                1, List.of("PENDING"), List.of("INV-1"),
                                "Supplier has active purchasing activity"
                        )
                ));

        assertThatThrownBy(() -> supplierService.deactivate(7L))
                .isInstanceOf(SupplierDeactivationBlockedException.class)
                .hasMessageContaining("active purchasing activity");
    }

    @Test
    void deactivate_suspendsSupplierAndSendsSuspensionNotice() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willReturn(new PurchaseOrderClient.ApiResponse<>(
                        new PurchaseOrderClient.SupplierDeactivationCheckResponse(
                                true, 0, List.of(), List.of(), 0, List.of(), List.of(), "OK"
                        )
                ));
        given(supplierRepo.save(any(Supplier.class))).willAnswer(invocation -> invocation.getArgument(0));

        supplierService.deactivate(7L);

        then(supplierRepo).should().save(any(Supplier.class));
        then(supplierMailService).should().sendSuspensionNotice(any(Supplier.class));
    }

    @Test
    void getDeactivationCheck_mapsDependencyResponse() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willReturn(new PurchaseOrderClient.ApiResponse<>(
                        new PurchaseOrderClient.SupplierDeactivationCheckResponse(
                                false, 3, List.of("APPROVED"), List.of("PO-9"),
                                1, List.of("OVERDUE"), List.of("INV-7"),
                                "Blocked"
                        )
                ));

        SupplierDeactivationCheckResponse result = supplierService.getDeactivationCheck(7L);

        assertThat(result.canDeactivate()).isFalse();
        assertThat(result.blockingOrderCount()).isEqualTo(3);
        assertThat(result.blockingInvoiceNumbers()).containsExactly("INV-7");
    }

    @Test
    void getDeactivationCheck_throwsFriendlyErrorWhenDependencyReturnsNull() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willReturn(new PurchaseOrderClient.ApiResponse<>(null));

        assertThatThrownBy(() -> supplierService.getDeactivationCheck(7L))
                .isInstanceOf(SupplierDependencyUnavailableException.class)
                .hasMessageContaining("Unable to verify");
    }

    @Test
    void getDeactivationCheck_throwsFriendlyErrorForServiceUnavailable() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willThrow(mock(FeignException.ServiceUnavailable.class));

        assertThatThrownBy(() -> supplierService.getDeactivationCheck(7L))
                .isInstanceOf(SupplierDependencyUnavailableException.class)
                .hasMessageContaining("Purchase order service is unavailable");
    }

    @Test
    void getDeactivationCheck_throwsFriendlyErrorForAuthorizationPropagationFailure() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willThrow(mock(FeignException.Forbidden.class));

        assertThatThrownBy(() -> supplierService.getDeactivationCheck(7L))
                .isInstanceOf(SupplierDependencyUnavailableException.class)
                .hasMessageContaining("Permission context");
    }

    @Test
    void getDeactivationCheck_throwsFriendlyErrorForGenericFeignFailures() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(purchaseOrderClient.getSupplierDeactivationCheck(7L))
                .willThrow(mock(FeignException.class));

        assertThatThrownBy(() -> supplierService.getDeactivationCheck(7L))
                .isInstanceOf(SupplierDependencyUnavailableException.class)
                .hasMessageContaining("Unable to verify");
    }

    @Test
    void reactivate_returnsEarlyWhenAlreadyActive() {
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));

        supplierService.reactivate(7L);

        then(supplierRepo).should(never()).save(any(Supplier.class));
        then(supplierMailService).should(never()).sendReactivationNotice(any(Supplier.class));
    }

    @Test
    void reactivate_turnsSupplierBackOnAndSendsMail() {
        supplier.setActive(false);
        given(supplierRepo.findById(7L)).willReturn(Optional.of(supplier));
        given(supplierRepo.save(any(Supplier.class))).willAnswer(invocation -> invocation.getArgument(0));

        supplierService.reactivate(7L);

        assertThat(supplier.getActive()).isTrue();
        then(supplierMailService).should().sendReactivationNotice(eq(supplier));
    }

    @Test
    void getById_throwsWhenSupplierDoesNotExist() {
        given(supplierRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supplierService.getById(99L))
                .isInstanceOf(SupplierNotFoundException.class)
                .hasMessageContaining("99");
    }

    private SupplierResponse toResponse(Supplier value) {
        return new SupplierResponse(
                value.getId(),
                value.getName(),
                value.getContactPerson(),
                value.getContactEmail(),
                value.getContactPhone(),
                value.getAddress(),
                value.getCity(),
                value.getCountry(),
                value.getGstin(),
                value.getPaymentTerms(),
                value.getCreditLimit(),
                value.getNotes(),
                value.getCategory(),
                Boolean.TRUE.equals(value.getActive()),
                value.getCreatedAt(),
                value.getUpdatedAt()
        );
    }
}
