package com.commercehub.catalog.application;

import com.commercehub.catalog.api.CreateProductRequest;
import com.commercehub.catalog.api.ProductResponse;
import com.commercehub.catalog.api.UpdateProductRequest;
import com.commercehub.catalog.domain.Category;
import com.commercehub.catalog.domain.Product;
import com.commercehub.catalog.repository.CategoryRepository;
import com.commercehub.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ProductAdminServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @InjectMocks ProductAdminService service;

    @Test
    void createsProductInPENWithRequestedStatusAndValidCategory() {
        Category category = new Category("Audio", null);
        when(productRepository.findBySku("AUD-1")).thenReturn(Optional.empty());
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse created = service.createProduct(request("AUD-1", 7L, "25.90", true));

        assertThat(created.sku()).isEqualTo("AUD-1");
        assertThat(created.category().name()).isEqualTo("Audio");
        assertThat(created.price()).isEqualByComparingTo("25.90");
        assertThat(created.currency()).isEqualTo("PEN");
        assertThat(created.published()).isTrue();
    }

    @Test
    void createsUnpublishedByDefaultWhenStatusIsOmitted() {
        when(productRepository.findBySku("AUD-1")).thenReturn(Optional.empty());
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(new Category("Audio", null)));
        when(productRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createProduct(request("AUD-1", 7L, "25.90", null)).published()).isFalse();
    }

    @Test
    void rejectsDuplicateSkuAndUnknownCategory() {
        when(productRepository.findBySku("AUD-1")).thenReturn(Optional.of(
                new Product("AUD-1", "Old", null, new Category("Audio", null),
                        new BigDecimal("1.00"), null, false)));
        assertThatThrownBy(() -> service.createProduct(request("AUD-1", 7L, "25.90", false)))
                .isInstanceOf(ProductConflictException.class);

        when(productRepository.findBySku("AUD-2")).thenReturn(Optional.empty());
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createProduct(request("AUD-2", 999L, "25.90", false)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void rejectsNonPositivePriceThroughDomainInvariant() {
        when(productRepository.findBySku("AUD-1")).thenReturn(Optional.empty());
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(new Category("Audio", null)));

        assertThatThrownBy(() -> service.createProduct(request("AUD-1", 7L, "0.00", false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatesEditableDetailsAndCategoryWithoutChangingPublicationState() {
        Category oldCategory = new Category("Old", null);
        Category newCategory = new Category("New", null);
        Product existing = new Product("OLD-SKU", "Old name", "Old description", oldCategory,
                new BigDecimal("10.00"), null, false);
        when(productRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(productRepository.findBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(newCategory));
        when(productRepository.saveAndFlush(existing)).thenReturn(existing);

        ProductResponse updated = service.updateProduct(4L, updateRequest("NEW-SKU", 9L, "35.50"));

        assertThat(updated.sku()).isEqualTo("NEW-SKU");
        assertThat(updated.name()).isEqualTo("Updated name");
        assertThat(updated.category().name()).isEqualTo("New");
        assertThat(updated.price()).isEqualByComparingTo("35.50");
        assertThat(updated.published()).isFalse();
    }

    @Test
    void updateRejectsMissingProductCategoryAndDuplicateSku() {
        assertThatThrownBy(() -> service.updateProduct(88L, updateRequest("SKU", 9L, "10.00")))
                .isInstanceOf(ProductNotFoundException.class);

        Product existing = new Product("OLD-SKU", "Old", null, new Category("Old", null),
                new BigDecimal("10.00"), null, true);
        when(productRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(productRepository.findBySku("SKU")).thenReturn(Optional.empty());
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProduct(4L, updateRequest("SKU", 999L, "10.00")))
                .isInstanceOf(CategoryNotFoundException.class);

        when(productRepository.findBySku("TAKEN")).thenReturn(Optional.of(
                new Product("TAKEN", "Other", null, new Category("Other", null),
                        new BigDecimal("10.00"), null, true)));
        assertThatThrownBy(() -> service.updateProduct(4L, updateRequest("TAKEN", 9L, "10.00")))
                .isInstanceOf(ProductConflictException.class);
    }

    @Test
    void updateRejectsInvalidPriceWithoutPartiallyChangingProduct() {
        Product existing = new Product("OLD-SKU", "Old", null, new Category("Audio", null),
                new BigDecimal("10.00"), null, true);
        when(productRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(productRepository.findBySku("NEW-SKU")).thenReturn(Optional.empty());
        when(categoryRepository.findById(9L)).thenReturn(Optional.of(new Category("New", null)));

        assertThatThrownBy(() -> service.updateProduct(4L, updateRequest("NEW-SKU", 9L, "0.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(existing.getSku()).isEqualTo("OLD-SKU");
        assertThat(existing.getName()).isEqualTo("Old");
    }

    @Test
    void disableIsLogicalAndIdempotent() {
        Product product = new Product("SKU", "Name", null, new Category("Audio", null),
                new BigDecimal("10.00"), null, true);
        when(productRepository.findById(4L)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        assertThat(service.disableProduct(4L).published()).isFalse();
        assertThat(service.disableProduct(4L).published()).isFalse();
        verify(productRepository, times(2)).saveAndFlush(product);
    }

    private CreateProductRequest request(String sku, Long categoryId, String price, Boolean published) {
        return new CreateProductRequest(sku, "Headphones", null, categoryId,
                new BigDecimal(price), "PEN", null, published);
    }

    private UpdateProductRequest updateRequest(String sku, Long categoryId, String price) {
        return new UpdateProductRequest(sku, "Updated name", "Updated description", categoryId,
                new BigDecimal(price), "PEN", "https://example.test/image.webp");
    }
}
