package com.commercehub.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
        @JsonProperty("product_id") @NotNull Long productId,
        @NotNull @Min(1) Integer quantity) {
}
