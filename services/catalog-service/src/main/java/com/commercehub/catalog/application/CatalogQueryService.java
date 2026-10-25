package com.commercehub.catalog.application;

import com.commercehub.catalog.api.CategoryResponse;
import com.commercehub.catalog.api.ProductPageResponse;
import com.commercehub.catalog.api.ProductResponse;
import com.commercehub.catalog.repository.CategoryRepository;
import com.commercehub.catalog.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public CatalogQueryService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductPageResponse findProducts(int page, int size, Long categoryId, String search) {
        if (page < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual a 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size debe ser mayor o igual a 1");
        }
        String normalizedSearch = search == null || search.isBlank() ? "" : search.trim();
        Page<ProductResponse> products = productRepository
                .findPublished(categoryId, normalizedSearch, PageRequest.of(page, size, DEFAULT_SORT))
                .map(ProductResponse::from);
        return ProductPageResponse.from(products);
    }

    public ProductResponse findProduct(Long id) {
        return productRepository.findPublishedById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public List<CategoryResponse> findCategories() {
        return categoryRepository.findAllWithPublishedProducts().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
