package com.commercehub.catalog.application;

import com.commercehub.catalog.api.CreateProductRequest;
import com.commercehub.catalog.api.ProductResponse;
import com.commercehub.catalog.api.UpdateProductRequest;
import com.commercehub.catalog.application.ProductNotFoundException;
import com.commercehub.catalog.domain.Category;
import com.commercehub.catalog.domain.Product;
import com.commercehub.catalog.repository.CategoryRepository;
import com.commercehub.catalog.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductAdminService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.findBySku(request.sku()).isPresent()) {
            throw new ProductConflictException("Ya existe un producto con el SKU indicado");
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
        Product product = new Product(request.sku(), request.name(), request.description(), category,
                request.price(), request.imageUrl(), Boolean.TRUE.equals(request.published()));
        try {
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new ProductConflictException("No se pudo crear el producto por una restricción de unicidad");
        }
    }

    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (productRepository.findBySku(request.sku()).filter(existing -> !id.equals(existing.getId())).isPresent()) {
            throw new ProductConflictException("Ya existe un producto con el SKU indicado");
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));
        product.updateDetails(request.sku(), request.name(), request.description(), category,
                request.price(), request.imageUrl());
        try {
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new ProductConflictException("No se pudo actualizar el producto por una restricción de unicidad");
        }
    }

    @Transactional
    public ProductResponse disableProduct(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.unpublish();
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }
}
