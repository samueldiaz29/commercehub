package com.commercehub.catalog.api;

import com.commercehub.catalog.domain.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
