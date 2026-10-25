package com.commercehub.catalog.application;

import com.commercehub.catalog.domain.Category;
import com.commercehub.catalog.domain.Product;
import com.commercehub.catalog.repository.CategoryRepository;
import com.commercehub.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;
    @InjectMocks CatalogQueryService service;

    @Test
    void listsPublishedProductsWithDocumentedFiltersPagingAndStableDefaultOrder() {
        Category category = new Category("Audio", null);
        Product product = new Product("AUD-1", "Headphones", null, category,
                new BigDecimal("99.90"), null, true);
        PageRequest pageable = PageRequest.of(2, 5, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        when(productRepository.findPublished(7L, "head", pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 11));

        var response = service.findProducts(2, 5, 7L, " head ");

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.published()).isTrue();
            assertThat(item.category().name()).isEqualTo("Audio");
            assertThat(item.currency()).isEqualTo("PEN");
        });
        verify(productRepository).findPublished(7L, "head", pageable);
    }

    @Test
    void returnsOnlyCategoriesWithPublishedProductsFromRepository() {
        Category visible = new Category("Audio", null);
        when(categoryRepository.findAllWithPublishedProducts()).thenReturn(List.of(visible));

        assertThat(service.findCategories()).extracting("name").containsExactly("Audio");
    }

    @Test
    void hidesMissingAndUnpublishedProductsBehindNotFound() {
        when(productRepository.findPublishedById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findProduct(42L)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void rejectsInvalidPageAndSizeBeforeQueryingRepository() {
        assertThatThrownBy(() -> service.findProducts(-1, 20, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findProducts(0, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesDefaultPageSizeAndEmptySearchWhenFiltersAreOmitted() {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        when(productRepository.findPublished(null, "", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var response = service.findProducts(0, 20, null, "  ");

        assertThat(response.content()).isEmpty();
        verify(productRepository).findPublished(null, "", pageable);
    }
}
