package com.commercehub.catalog.application;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long id) {
        super("No se encontró una categoría con identificador " + id);
    }
}
