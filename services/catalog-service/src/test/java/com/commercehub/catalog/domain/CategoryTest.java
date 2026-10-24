package com.commercehub.catalog.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryTest {

    @Test
    void requiresNonBlankName() {
        assertThrows(NullPointerException.class, () -> new Category(null, null));
        assertThrows(IllegalArgumentException.class, () -> new Category("  ", null));
    }

    @Test
    void acceptsNameAndOptionalDescription() {
        Category category = new Category("Audio", "Audio devices");

        assertEquals("Audio", category.getName());
        assertEquals("Audio devices", category.getDescription());
    }
}
