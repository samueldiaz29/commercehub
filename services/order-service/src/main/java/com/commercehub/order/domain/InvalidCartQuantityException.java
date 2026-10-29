package com.commercehub.order.domain;

public class InvalidCartQuantityException extends RuntimeException {

    public InvalidCartQuantityException() {
        super("La cantidad debe ser igual o mayor a 1.");
    }

    public InvalidCartQuantityException(String message) {
        super(message);
    }
}
