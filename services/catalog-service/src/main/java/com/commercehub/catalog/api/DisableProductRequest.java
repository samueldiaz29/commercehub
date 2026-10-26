package com.commercehub.catalog.api;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;

public record DisableProductRequest(@NotNull @AssertFalse Boolean published) {
}
