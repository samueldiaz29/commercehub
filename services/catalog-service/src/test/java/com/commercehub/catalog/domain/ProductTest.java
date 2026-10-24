package com.commercehub.catalog.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTest {

    private final Category category = new Category("Audio", null);

    @Test
    void requiresSkuNameCategoryAndPositivePrice() {
        assertThrows(NullPointerException.class,
                () -> new Product(null, "Headphones", null, category, new BigDecimal("10.00"), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("SKU-1", " ", null, category, new BigDecimal("10.00"), null, false));
        assertThrows(NullPointerException.class,
                () -> new Product("SKU-1", "Headphones", null, null, new BigDecimal("10.00"), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("SKU-1", "Headphones", null, category, BigDecimal.ZERO, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("SKU-1", "Headphones", null, category, new BigDecimal("-0.01"), null, false));
    }

    @Test
    void limitsPriceToThePersistedNumericPrecisionAndScale() {
        Product product = product("99999999999999999.99");
        assertEquals(new BigDecimal("99999999999999999.99"), product.getPrice());

        assertThrows(IllegalArgumentException.class, () -> product("100000000000000000.00"));
        assertThrows(IllegalArgumentException.class, () -> product("10.001"));
    }

    @Test
    void fixesCurrencyToPenAndAllowsPublicationStateChanges() {
        Product product = new Product("SKU-1", "Headphones", null, category,
                new BigDecimal("10.00"), null, false);

        assertEquals(Currency.PEN, product.getCurrency());
        assertFalse(product.isPublished());
        product.publish();
        assertTrue(product.isPublished());
        product.unpublish();
        assertFalse(product.isPublished());
    }

    @Test
    void invalidPriceChangeDoesNotReplaceCurrentPrice() {
        Product product = product("10.00");

        assertThrows(IllegalArgumentException.class, () -> product.changePrice(BigDecimal.ZERO));
        assertEquals(new BigDecimal("10.00"), product.getPrice());
    }

    private Product product(String price) {
        return new Product("SKU-1", "Headphones", null, category,
                new BigDecimal(price), null, true);
    }
}
