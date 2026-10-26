package com.commercehub.catalog.application;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("No se encontró un producto con identificador " + id);
    }
}
