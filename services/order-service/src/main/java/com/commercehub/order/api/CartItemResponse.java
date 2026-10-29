package com.commercehub.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CartItemResponse(
        @JsonProperty("product_id") Long productId,
        int quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("line_subtotal") BigDecimal lineSubtotal,
        String currency) {
}
