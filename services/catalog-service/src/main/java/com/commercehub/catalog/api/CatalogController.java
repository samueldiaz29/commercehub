package com.commercehub.catalog.api;

import com.commercehub.catalog.application.CatalogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping("/products")
    public ProductPageResponse products(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "search", required = false) String search) {
        return catalogQueryService.findProducts(page, size, categoryId, search);
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable("id") Long id) {
        return catalogQueryService.findProduct(id);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogQueryService.findCategories();
    }
}
