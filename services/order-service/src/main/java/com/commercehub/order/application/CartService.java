package com.commercehub.order.application;

import com.commercehub.order.api.CartItemResponse;
import com.commercehub.order.api.CartResponse;
import com.commercehub.order.client.CatalogClient;
import com.commercehub.order.client.CatalogProduct;
import com.commercehub.order.domain.CartItemNotFoundException;
import com.commercehub.order.domain.InvalidCartQuantityException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    static final BigDecimal SHIPPING = new BigDecimal("10.00");

    private final CartPersistenceService persistence;
    private final CatalogClient catalog;

    public CartService(CartPersistenceService persistence, CatalogClient catalog) {
        this.persistence = persistence;
        this.catalog = catalog;
    }

    public CartResponse get(String customerId) {
        List<CartLine> lines = persistence.getItems(customerId);
        return render(lines, loadProducts(lines, null));
    }

    public CartResponse add(String customerId, Long productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidCartQuantityException();
        }

        List<CartLine> existingItems = persistence.getItems(customerId);
        Map<Long, CatalogProduct> products = loadProducts(existingItems, productId);
        List<CartLine> updatedItems = persistence.add(
                customerId, productId, quantity, existingItems);
        return render(updatedItems, products);
    }

    public CartResponse update(String customerId, Long productId, int quantity) {
        if (quantity < 1) {
            throw new InvalidCartQuantityException();
        }

        List<CartLine> existingItems = persistence.getItems(customerId);
        if (existingItems.stream().noneMatch(line -> line.productId().equals(productId))) {
            throw new CartItemNotFoundException(productId);
        }
        Map<Long, CatalogProduct> products = loadProducts(existingItems, null);
        List<CartLine> updatedItems = persistence.update(
                customerId, productId, quantity, existingItems);
        return render(updatedItems, products);
    }

    public CartResponse remove(String customerId, Long productId) {
        List<CartLine> existingItems = persistence.getItems(customerId);
        if (existingItems.stream().noneMatch(line -> line.productId().equals(productId))) {
            throw new CartItemNotFoundException(productId);
        }
        List<CartLine> remainingItems = existingItems.stream()
                .filter(line -> !line.productId().equals(productId))
                .toList();
        Map<Long, CatalogProduct> products = loadProducts(remainingItems, null);
        persistence.remove(customerId, productId, existingItems);
        return render(remainingItems, products);
    }

    private Map<Long, CatalogProduct> loadProducts(List<CartLine> lines, Long additionalProductId) {
        Map<Long, CatalogProduct> products = new LinkedHashMap<>();
        for (CartLine line : lines) {
            products.computeIfAbsent(
                    line.productId(), catalog::getPublishedProduct);
        }
        if (additionalProductId != null) {
            products.computeIfAbsent(
                    additionalProductId, catalog::getPublishedProduct);
        }
        return products;
    }

    private CartResponse render(List<CartLine> lines, Map<Long, CatalogProduct> products) {
        List<CartItemResponse> items = lines.stream()
                .map(line -> toResponse(line, products.get(line.productId())))
                .toList();
        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::lineSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal shipping = items.isEmpty() ? BigDecimal.ZERO.setScale(2) : SHIPPING;
        return new CartResponse("PEN", items, subtotal, shipping, subtotal.add(shipping));
    }

    private CartItemResponse toResponse(CartLine line, CatalogProduct product) {
        BigDecimal price = product.price().setScale(2);
        BigDecimal lineSubtotal = price.multiply(BigDecimal.valueOf(line.quantity()));
        return new CartItemResponse(line.productId(), line.quantity(), price, lineSubtotal, "PEN");
    }
}
