package com.commercehub.order.client;

import java.math.BigDecimal;

public record CatalogProduct(Long id, BigDecimal price, String currency, boolean published) {
}
