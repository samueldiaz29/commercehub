package com.commercehub.catalog.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @JsonProperty("category_id") @NotNull Long categoryId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal price,
        @NotBlank @Pattern(regexp = "PEN") String currency,
        @JsonProperty("image_url") @Size(max = 2048) String imageUrl) {
}
