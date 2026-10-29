package com.commercehub.order.api;

import com.commercehub.order.application.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return carts.get(customerId(jwt));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public CartResponse add(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CartItemRequest request) {
        return carts.add(customerId(jwt), request.productId(), request.quantity());
    }

    @PatchMapping("/items/{productId}")
    public CartResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") Long productId,
            @Valid @RequestBody QuantityRequest request) {
        return carts.update(customerId(jwt), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("productId") Long productId) {
        return carts.remove(customerId(jwt), productId);
    }

    private String customerId(Jwt jwt) {
        return jwt.getSubject();
    }

    public record QuantityRequest(
            @NotNull @Min(1) Integer quantity) {
    }
}
