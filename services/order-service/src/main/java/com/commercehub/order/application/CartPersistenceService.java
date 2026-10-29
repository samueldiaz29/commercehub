package com.commercehub.order.application;

import com.commercehub.order.domain.Cart;
import com.commercehub.order.domain.CartConcurrentModificationException;
import com.commercehub.order.domain.CartItem;
import com.commercehub.order.domain.CartItemNotFoundException;
import com.commercehub.order.repository.CartRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartPersistenceService {

    private final CartRepository carts;

    public CartPersistenceService(CartRepository carts) {
        this.carts = carts;
    }

    @Transactional(readOnly = true)
    public List<CartLine> getItems(String customerId) {
        return carts.findByCustomerIdWithItems(customerId)
                .map(CartPersistenceService::toLines)
                .orElseGet(List::of);
    }

    @Transactional
    public List<CartLine> add(
            String customerId,
            Long productId,
            int quantity,
            List<CartLine> expectedItems) {
        Cart cart = carts.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> new Cart(customerId));
        ensureExpectedItems(cart, expectedItems);
        cart.addProduct(productId, quantity);
        return toLines(carts.saveAndFlush(cart));
    }

    @Transactional
    public List<CartLine> update(
            String customerId,
            Long productId,
            int quantity,
            List<CartLine> expectedItems) {
        Cart cart = requireCartForUpdate(customerId, productId);
        ensureExpectedItems(cart, expectedItems);
        cart.requireItem(productId).changeQuantity(quantity);
        return toLines(cart);
    }

    @Transactional
    public void remove(String customerId, Long productId, List<CartLine> expectedItems) {
        Cart cart = requireCartForUpdate(customerId, productId);
        ensureExpectedItems(cart, expectedItems);
        CartItem item = cart.requireItem(productId);
        cart.remove(item);
    }

    private Cart requireCartForUpdate(String customerId, Long productId) {
        return carts.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
    }

    private void ensureExpectedItems(Cart cart, List<CartLine> expectedItems) {
        if (!sameItems(toLines(cart), expectedItems)) {
            throw new CartConcurrentModificationException();
        }
    }

    private static boolean sameItems(List<CartLine> current, List<CartLine> expected) {
        Map<Long, Integer> currentQuantities = current.stream()
                .collect(Collectors.toMap(CartLine::productId, CartLine::quantity));
        Map<Long, Integer> expectedQuantities = expected.stream()
                .collect(Collectors.toMap(CartLine::productId, CartLine::quantity));
        return currentQuantities.equals(expectedQuantities);
    }

    private static List<CartLine> toLines(Cart cart) {
        return cart.getItems().stream()
                .map(item -> new CartLine(item.getProductId(), item.getQuantity()))
                .toList();
    }
}
