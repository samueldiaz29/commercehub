package com.commercehub.order.domain;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(Long productId) {
        super("El producto " + productId + " no está en el carrito.");
    }
}
