package com.inventory.product.controller;

import com.inventory.product.dto.request.ProductUpsertRequest;
import com.inventory.product.entity.Product;
import com.inventory.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductController productController;

    @Test
    void getAllProducts_searchesWhenQueryPresent() {
        Product product = Product.builder().id(1L).name("Widget").sku("W-1").active(true).build();
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.searchProducts("widget", true, PageRequest.of(0, 5))).thenReturn(page);

        Page<Product> response = productController
                .getAllProducts("  widget  ", true, PageRequest.of(0, 5))
                .getBody().data();

        assertThat(response.getContent()).hasSize(1);
        verify(productRepository).searchProducts("widget", true, PageRequest.of(0, 5));
    }

    @Test
    void getAllProducts_filtersByActiveWhenRequested() {
        when(productRepository.findByActive(true, PageRequest.of(0, 5)))
                .thenReturn(Page.empty(PageRequest.of(0, 5)));

        productController.getAllProducts(null, true, PageRequest.of(0, 5));

        verify(productRepository).findByActive(true, PageRequest.of(0, 5));
    }

    @Test
    void getAllProducts_returnsAllWhenNoQueryOrFilter() {
        when(productRepository.findAll(PageRequest.of(0, 5)))
                .thenReturn(Page.empty(PageRequest.of(0, 5)));

        productController.getAllProducts(" ", false, PageRequest.of(0, 5));

        verify(productRepository).findAll(PageRequest.of(0, 5));
    }

    @Test
    void getProductById_normalizesLegacyUnitForRead() {
        Product product = Product.builder()
                .id(2L)
                .name("Scale")
                .sku("S-1")
                .unit(" KG ")
                .build();
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        Product response = productController.getProductById(2L).getBody().data();

        assertThat(response.getWeightUnit()).isEqualTo("kg");
    }

    @Test
    void getProductById_normalizesLegacyDimensionUnitForRead() {
        Product product = Product.builder()
                .id(3L)
                .name("Box")
                .sku("B-1")
                .unit(" CM ")
                .build();
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));

        Product response = productController.getProductById(3L).getBody().data();

        assertThat(response.getDimensionUnit()).isEqualTo("cm");
    }

    @Test
    void createProduct_trimsFieldsAndNormalizesUnits() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "  Widget  ", "  SKU-1 ", "  sample ", " tools ",
                12.0, 8.0, 1.5, 2.0, 10.0, 4.0, 3.0,
                " KG ", " CM ", null, true, null, null, null, null);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product response = productController.createProduct(input).getBody().data();

        assertThat(response.getName()).isEqualTo("Widget");
        assertThat(response.getSku()).isEqualTo("SKU-1");
        assertThat(response.getDescription()).isEqualTo("sample");
        assertThat(response.getWeightUnit()).isEqualTo("kg");
        assertThat(response.getDimensionUnit()).isEqualTo("cm");
        assertThat(response.getUnit()).isNull();
    }

    @Test
    void createProduct_usesLegacyUnitsWhenExplicitUnitsAreMissing() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Legacy", "LEG-1", null, null,
                null, null, null, null, 4.0, 2.0, null,
                null, null, " CM ", true, null, null, null, null);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product response = productController.createProduct(input).getBody().data();

        assertThat(response.getWeightUnit()).isNull();
        assertThat(response.getDimensionUnit()).isEqualTo("cm");
    }

    @Test
    void createProduct_rejectsInvalidMeasurements() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, 2.0, null, null, null,
                "stone", null, null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weight unit must be one of");
    }

    @Test
    void createProduct_rejectsMissingWeightUnitWhenWeightExists() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, 2.0, null, null, null,
                null, null, null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Select a valid weight unit when weight is provided");
    }

    @Test
    void createProduct_rejectsWeightUnitWithoutWeight() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, null, null, null, null,
                "kg", null, null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Weight unit cannot be set without a weight value");
    }

    @Test
    void createProduct_rejectsMissingDimensionUnitWhenDimensionsExist() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, null, 2.0, null, null,
                null, null, null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Select a valid dimension unit when length, width, or height is provided");
    }

    @Test
    void createProduct_rejectsDimensionUnitWithoutDimensions() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, null, null, null, null,
                null, "cm", null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dimension unit cannot be set without length, width, or height");
    }

    @Test
    void createProduct_rejectsInvalidLegacyDimensionUnit() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, null, 2.0, null, null,
                null, "yard", null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dimension unit must be one of");
    }

    @Test
    void createProduct_rejectsInvalidLegacyWeightUnit() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                null, null, null, 2.0, null, null, null,
                null, null, "stone", true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Select a valid weight unit when weight is provided");
    }

    @Test
    void createProduct_rejectsNegativeNumericFields() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Widget", "SKU-1", null, null,
                -1.0, null, null, null, null, null, null,
                null, null, null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selling price cannot be negative");
    }

    @Test
    void createProduct_rejectsBlankRequiredText() {
        ProductUpsertRequest input = new ProductUpsertRequest(
                "   ", "SKU-1", null, null,
                null, null, null, null, null, null, null,
                null, null, null, true, null, null, null, null);

        assertThatThrownBy(() -> productController.createProduct(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name is required");
    }

    @Test
    void updateProduct_appliesValidatedFieldsToExistingEntity() {
        Product existing = Product.builder().id(7L).name("Old").sku("OLD").active(true).build();
        ProductUpsertRequest input = new ProductUpsertRequest(
                " New Name ", " NEW ", null, null,
                22.0, 10.0, 2.5, null, null, null, null,
                null, null, null, false, 5, 50, null, null);
        when(productRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Product updated = productController.updateProduct(7L, input).getBody().data();

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getSku()).isEqualTo("NEW");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getReorderLevel()).isEqualTo(5);
    }

    @Test
    void updateProduct_clearsBlankOptionalTextFields() {
        Product existing = Product.builder().id(8L).name("Old").sku("OLD").active(true).build();
        ProductUpsertRequest input = new ProductUpsertRequest(
                "Updated", "SKU-8", "   ", "   ",
                null, null, null, null, null, null, null,
                null, null, null, true, null, null, null, null);
        when(productRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Product updated = productController.updateProduct(8L, input).getBody().data();

        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getCategory()).isNull();
    }

    @Test
    void updateProduct_throwsWhenMissing() {
        when(productRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productController.updateProduct(9L,
                new ProductUpsertRequest(null, null, null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, true, null, null, null, null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deactivateProduct_marksEntityInactive() {
        Product existing = Product.builder().id(11L).name("Item").sku("I-1").active(true).build();
        when(productRepository.findById(11L)).thenReturn(Optional.of(existing));

        String message = productController.deactivateProduct(11L).getBody().message();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(message).isEqualTo("Product deactivated successfully");
    }
}
