package com.commercehub.order.domain;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(Long id) {
        super("El producto " + id + " no existe o no está publicado.");
    }
}
