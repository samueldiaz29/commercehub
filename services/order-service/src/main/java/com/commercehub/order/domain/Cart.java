package com.commercehub.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carts_customer_id", columnNames = "customer_id")
})
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 200)
    private String customerId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Cart(String customerId) {
        this.customerId = customerId;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public CartItem addProduct(Long productId, int quantity) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .map(item -> {
                    try {
                        item.changeQuantity(Math.addExact(item.getQuantity(), quantity));
                    } catch (ArithmeticException exception) {
                        throw new InvalidCartQuantityException(
                                "La cantidad acumulada supera el máximo permitido.");
                    }
                    return item;
                })
                .orElseGet(() -> {
                    CartItem item = new CartItem(this, productId, quantity);
                    items.add(item);
                    return item;
                });
    }

    public CartItem requireItem(Long productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(productId));
    }

    public void remove(CartItem item) {
        items.remove(item);
    }
}
