package com.commercehub.catalog.application;

public class ProductConflictException extends RuntimeException {

    public ProductConflictException(String message) {
        super(message);
    }
}
