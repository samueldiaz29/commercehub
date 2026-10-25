package com.commercehub.catalog.api;

import com.commercehub.catalog.domain.Product;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        CategoryResponse category,
        BigDecimal price,
        String currency,
        @JsonProperty("image_url") String imageUrl,
        boolean published) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getDescription(), CategoryResponse.from(product.getCategory()),
                product.getPrice(), product.getCurrency().name(), product.getImageUrl(), product.isPublished());
    }
}
