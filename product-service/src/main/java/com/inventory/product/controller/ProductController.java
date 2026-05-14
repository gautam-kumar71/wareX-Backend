package com.inventory.product.controller;

import com.inventory.product.dto.request.ProductUpsertRequest;
import com.inventory.product.dto.response.ApiResponse;
import com.inventory.product.entity.Product;
import com.inventory.product.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Set;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    private static final Set<String> WEIGHT_UNITS = Set.of("mg", "g", "kg", "lb", "oz");
    private static final Set<String> DIMENSION_UNITS = Set.of("mm", "cm", "m", "in", "ft");

    private final ProductRepository productRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<Product>>> getAllProducts(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            Pageable pageable
    ) {
        String normalizedQuery = Optional.ofNullable(q).map(String::trim).orElse("");
        Page<Product> page;

        if (!normalizedQuery.isEmpty()) {
            page = productRepository.searchProducts(normalizedQuery, activeOnly, pageable);
        } else if (activeOnly) {
            page = productRepository.findByActive(true, pageable);
        } else {
            page = productRepository.findAll(pageable);
        }

        page.forEach(this::normalizeUnitsForRead);
        log.debug("Returning {} products for query='{}', activeOnly={}", page.getNumberOfElements(), normalizedQuery, activeOnly);

        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Long id) {
        Product product = productRepository.findById(id).orElseThrow();
        normalizeUnitsForRead(product);
        log.debug("Returning product details for id={}", id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Product>> createProduct(@Valid @RequestBody ProductUpsertRequest request) {
        Product product = new Product();
        applyValidatedFields(product, request);
        Product saved = productRepository.save(product);
        normalizeUnitsForRead(saved);
        log.info("Product created: sku={}, name={}", saved.getSku(), saved.getName());
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest productDetails) {
        Product product = productRepository.findById(id).orElseThrow();

        applyValidatedFields(product, productDetails);

        Product updatedProduct = productRepository.save(product);
        normalizeUnitsForRead(updatedProduct);
        log.info("Product updated: id={}, sku={}", id, updatedProduct.getSku());
        return ResponseEntity.ok(ApiResponse.success(updatedProduct));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable Long id) {
        Product product = productRepository.findById(id).orElseThrow();
        product.setActive(false);
        productRepository.save(product);
        log.info("Product deactivated: id={}", id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deactivated successfully"));
    }

    private void applyValidatedFields(Product target, ProductUpsertRequest source) {
        validateProductMeasurements(source);

        target.setName(requireText(source.name(), "Product name is required"));
        target.setSku(requireText(source.sku(), "SKU is required"));
        target.setDescription(trimNullableText(source.description()));
        target.setCategory(trimNullableText(source.category()));
        target.setPrice(validateNonNegative(source.price(), "Selling price"));
        target.setCostPrice(validateNonNegative(source.costPrice(), "Cost price"));
        target.setTaxRate(validateNonNegative(source.taxRate(), "Tax rate"));
        target.setWeight(validateNonNegative(source.weight(), "Weight"));
        target.setLength(validateNonNegative(source.length(), "Length"));
        target.setWidth(validateNonNegative(source.width(), "Width"));
        target.setHeight(validateNonNegative(source.height(), "Height"));
        target.setWeightUnit(normalizeWeightUnit(source));
        target.setDimensionUnit(normalizeDimensionUnit(source));
        target.setUnit(null);
        if (source.active() != null) {
            target.setActive(source.active());
        }
        target.setReorderLevel(source.reorderLevel());
        target.setMaxStockLevel(source.maxStockLevel());
        if (source.totalStock() != null) {
            target.setTotalStock(source.totalStock());
        }
        if (source.allocatedStock() != null) {
            target.setAllocatedStock(source.allocatedStock());
        }
    }

    private void validateProductMeasurements(ProductUpsertRequest product) {
        validateNonNegative(product.price(), "Selling price");
        validateNonNegative(product.costPrice(), "Cost price");
        validateNonNegative(product.taxRate(), "Tax rate");
        validateNonNegative(product.weight(), "Weight");
        validateNonNegative(product.length(), "Length");
        validateNonNegative(product.width(), "Width");
        validateNonNegative(product.height(), "Height");

        String weightUnit = normalizeWeightUnit(product);
        String dimensionUnit = normalizeDimensionUnit(product);

        if (product.weight() != null && weightUnit == null) {
            throw new IllegalArgumentException("Select a valid weight unit when weight is provided");
        }
        if (product.weight() == null && weightUnit != null) {
            throw new IllegalArgumentException("Weight unit cannot be set without a weight value");
        }

        boolean hasAnyDimension = product.length() != null || product.width() != null || product.height() != null;
        if (hasAnyDimension && dimensionUnit == null) {
            throw new IllegalArgumentException("Select a valid dimension unit when length, width, or height is provided");
        }
        if (!hasAnyDimension && dimensionUnit != null) {
            throw new IllegalArgumentException("Dimension unit cannot be set without length, width, or height");
        }
    }

    private void normalizeUnitsForRead(Product product) {
        String legacyUnit = normalizeUnit(product.getUnit());
        if (product.getWeightUnit() == null && legacyUnit != null && WEIGHT_UNITS.contains(legacyUnit)) {
            product.setWeightUnit(legacyUnit);
        }
        if (product.getDimensionUnit() == null && legacyUnit != null && DIMENSION_UNITS.contains(legacyUnit)) {
            product.setDimensionUnit(legacyUnit);
        }
    }

    private String normalizeWeightUnit(ProductUpsertRequest product) {
        String explicitUnit = normalizeUnit(product.weightUnit());
        if (explicitUnit != null) {
            if (!WEIGHT_UNITS.contains(explicitUnit)) {
                throw new IllegalArgumentException("Weight unit must be one of: " + String.join(", ", WEIGHT_UNITS));
            }
            return explicitUnit;
        }

        String legacyUnit = normalizeUnit(product.unit());
        if (legacyUnit != null) {
            if (WEIGHT_UNITS.contains(legacyUnit)) {
                return legacyUnit;
            }
        }

        return null;
    }

    private String normalizeDimensionUnit(ProductUpsertRequest product) {
        String explicitUnit = normalizeUnit(product.dimensionUnit());
        if (explicitUnit != null) {
            if (!DIMENSION_UNITS.contains(explicitUnit)) {
                throw new IllegalArgumentException("Dimension unit must be one of: " + String.join(", ", DIMENSION_UNITS));
            }
            return explicitUnit;
        }

        String legacyUnit = normalizeUnit(product.unit());
        if (legacyUnit != null) {
            if (DIMENSION_UNITS.contains(legacyUnit)) {
                return legacyUnit;
            }
        }

        return null;
    }

    private Double validateNonNegative(Double value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value;
    }

    private String requireText(String value, String message) {
        String normalized = trimNullableText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeUnit(String value) {
        String normalized = trimNullableText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
