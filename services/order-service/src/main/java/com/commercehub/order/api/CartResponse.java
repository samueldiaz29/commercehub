package com.commercehub.order.api;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        String currency,
        List<CartItemResponse> items,
        BigDecimal subtotal,
        BigDecimal shipping,
        BigDecimal total) {
}
