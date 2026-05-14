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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

    private final SupplierRepository supplierRepo;
    private final SupplierMapper     supplierMapper;
    private final PurchaseOrderClient purchaseOrderClient;
    private final SupplierMailService supplierMailService;

    @Transactional
    public SupplierResponse create(CreateSupplierRequest req) {
        String email = normalizeEmail(req.contactEmail());
        if (supplierRepo.existsByContactEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Supplier supplier = Supplier.builder()
                .name(normalizeText(req.name()))
                .contactPerson(normalizeNullableText(req.contactPerson()))
                .contactEmail(email)
                .contactPhone(normalizeNullableText(req.contactPhone()))
                .address(normalizeNullableText(req.address()))
                .city(normalizeNullableText(req.city()))
                .country(normalizeCountry(req.country()))
                .gstin(normalizeGstin(req.gstin()))
                .paymentTerms(req.paymentTerms() != null ? req.paymentTerms() : 30)
                .creditLimit(req.creditLimit())
                .notes(normalizeNullableText(req.notes()))
                .category(normalizeCategory(req.category()))
                .build();

        Supplier saved = supplierRepo.save(supplier);
        log.info("Supplier created: id={}, name={}", saved.getId(), saved.getName());
        return supplierMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getById(Long id) {
        return supplierRepo.findById(id)
                .map(supplierMapper::toResponse)
                .orElseThrow(() -> new SupplierNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAll(Boolean active, Pageable pageable) {
        Page<Supplier> page = active == null
                ? supplierRepo.findAll(pageable)
                : supplierRepo.findByActive(active, pageable);
        return page.map(supplierMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllActive() {
        return supplierRepo.findByActiveTrue()
                .stream().map(supplierMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();

        if (!StringUtils.hasText(normalizedQuery)) {
            if (active == null) {
                return supplierRepo.findAll(pageable).map(supplierMapper::toResponse);
            }

            return supplierRepo.findByActive(active, pageable).map(supplierMapper::toResponse);
        }

        if (normalizedQuery.chars().allMatch(Character::isDigit)) {
            Long supplierId = Long.parseLong(normalizedQuery);
            if (active == null) {
                return supplierRepo.findById(supplierId)
                        .map(supplierMapper::toResponse)
                        .map(response -> singleResultPage(response, pageable))
                        .orElseGet(() -> Page.empty(pageable));
            }

            return supplierRepo.findFirstByIdAndActive(supplierId, active)
                    .map(supplierMapper::toResponse)
                    .map(response -> singleResultPage(response, pageable))
                    .orElseGet(() -> Page.empty(pageable));
        }

        Page<Supplier> result = active == null
                ? supplierRepo.search(normalizedQuery, pageable)
                : supplierRepo.searchByActive(normalizedQuery, active, pageable);

        return result.map(supplierMapper::toResponse);
    }

    @Transactional
    public SupplierResponse update(Long id, UpdateSupplierRequest req) {
        Supplier supplier = supplierRepo.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
        boolean wasActive = Boolean.TRUE.equals(supplier.getActive());

        if (StringUtils.hasText(req.contactEmail())) {
            String newEmail = normalizeEmail(req.contactEmail());
            if (!newEmail.equals(supplier.getContactEmail()) &&
                    supplierRepo.existsByContactEmailAndIdNot(newEmail, id)) {
                throw new DuplicateEmailException(newEmail);
            }
            supplier.setContactEmail(newEmail);
        }

        if (StringUtils.hasText(req.name()))          supplier.setName(normalizeText(req.name()));
        if (StringUtils.hasText(req.contactPerson())) supplier.setContactPerson(normalizeText(req.contactPerson()));
        if (StringUtils.hasText(req.contactPhone()))  supplier.setContactPhone(normalizeText(req.contactPhone()));
        if (StringUtils.hasText(req.address()))       supplier.setAddress(normalizeText(req.address()));
        if (StringUtils.hasText(req.city()))          supplier.setCity(normalizeText(req.city()));
        if (StringUtils.hasText(req.country()))       supplier.setCountry(normalizeCountry(req.country()));
        if (StringUtils.hasText(req.gstin()))         supplier.setGstin(normalizeGstin(req.gstin()));
        if (req.paymentTerms() != null)               supplier.setPaymentTerms(req.paymentTerms());
        if (req.creditLimit()  != null)               supplier.setCreditLimit(req.creditLimit());
        if (req.notes()        != null)               supplier.setNotes(normalizeNullableText(req.notes()));
        if (req.category()     != null)               supplier.setCategory(normalizeCategory(req.category()));
        if (req.active()       != null)               supplier.setActive(req.active());

        Supplier saved = supplierRepo.save(supplier);
        notifyStatusChange(saved, wasActive);
        log.info("Supplier updated: id={}", saved.getId());
        return supplierMapper.toResponse(saved);
    }

    @Transactional
    public void deactivate(Long id) {
        Supplier supplier = supplierRepo.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));

        if (Boolean.FALSE.equals(supplier.getActive())) {
            log.info("Supplier already inactive: id={}", id);
            return;
        }

        assertDeactivationAllowed(id, supplier.getName());

        supplier.setActive(false);
        Supplier saved = supplierRepo.save(supplier);
        notifyStatusChange(saved, true);
        log.info("Supplier deactivated: id={}", id);
    }

    @Transactional(readOnly = true)
    public SupplierDeactivationCheckResponse getDeactivationCheck(Long id) {
        Supplier supplier = supplierRepo.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));

        PurchaseOrderClient.SupplierDeactivationCheckResponse data =
                fetchDeactivationCheck(id, supplier.getName());

        return new SupplierDeactivationCheckResponse(
                data.canDeactivate(),
                data.blockingOrderCount(),
                data.blockingStatuses(),
                data.blockingOrderNumbers(),
                data.blockingInvoiceCount(),
                data.blockingInvoiceStatuses(),
                data.blockingInvoiceNumbers(),
                data.message()
        );
    }

    @Transactional
    public void reactivate(Long id) {
        Supplier supplier = supplierRepo.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));

        if (Boolean.TRUE.equals(supplier.getActive())) {
            log.info("Supplier already active: id={}", id);
            return;
        }

        supplier.setActive(true);
        Supplier saved = supplierRepo.save(supplier);
        notifyStatusChange(saved, false);
        log.info("Supplier reactivated: id={}", id);
    }

    private String normalizeEmail(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private void notifyStatusChange(Supplier supplier, boolean wasPreviouslyActive) {
        if (wasPreviouslyActive && Boolean.FALSE.equals(supplier.getActive())) {
            supplierMailService.sendSuspensionNotice(supplier);
            return;
        }

        if (!wasPreviouslyActive && Boolean.TRUE.equals(supplier.getActive())) {
            supplierMailService.sendReactivationNotice(supplier);
        }
    }

    private void assertDeactivationAllowed(Long supplierId, String supplierName) {
        PurchaseOrderClient.SupplierDeactivationCheckResponse data = fetchDeactivationCheck(supplierId, supplierName);
        if (!data.canDeactivate()) {
            throw new SupplierDeactivationBlockedException(
                    data.message() != null && !data.message().isBlank()
                            ? data.message()
                            : "Supplier has active purchasing activity and cannot be deactivated.");
        }
    }

    private PurchaseOrderClient.SupplierDeactivationCheckResponse fetchDeactivationCheck(Long supplierId, String supplierName) {
        try {
            PurchaseOrderClient.ApiResponse<PurchaseOrderClient.SupplierDeactivationCheckResponse> response =
                    purchaseOrderClient.getSupplierDeactivationCheck(supplierId);

            if (response == null || response.data() == null) {
                throw new SupplierDependencyUnavailableException(
                        "Unable to verify purchase-order usage for supplier \"%s\" right now. Try again in a moment."
                                .formatted(supplierName));
            }

            return response.data();
        } catch (FeignException.ServiceUnavailable ex) {
            throw new SupplierDependencyUnavailableException(
                    "Purchase order service is unavailable. Cannot verify whether supplier \"%s\" is safe to deactivate."
                            .formatted(supplierName));
        } catch (FeignException.Unauthorized | FeignException.Forbidden ex) {
            throw new SupplierDependencyUnavailableException(
                    "Permission context could not be forwarded to verify supplier usage. Please retry from the app.");
        } catch (FeignException ex) {
            throw new SupplierDependencyUnavailableException(
                    "Unable to verify purchase-order usage for supplier \"%s\" right now. Try again in a moment."
                            .formatted(supplierName));
        }
    }

    private Page<SupplierResponse> singleResultPage(SupplierResponse response, Pageable pageable) {
        return new PageImpl<>(List.of(response), pageable, 1);
    }

    private String normalizeCountry(String value) {
        String normalized = normalizeNullableText(value);
        return StringUtils.hasText(normalized) ? normalized : "India";
    }

    private String normalizeGstin(String value) {
        String normalized = normalizeNullableText(value);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeCategory(String value) {
        String normalized = normalizeText(value);
        return normalized.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
