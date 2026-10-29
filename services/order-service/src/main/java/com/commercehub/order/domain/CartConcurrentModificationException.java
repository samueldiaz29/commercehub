package com.commercehub.order.domain;

public class CartConcurrentModificationException extends RuntimeException {

    public CartConcurrentModificationException() {
        super("El carrito cambió durante la operación. Vuelve a intentarlo.");
    }
}
