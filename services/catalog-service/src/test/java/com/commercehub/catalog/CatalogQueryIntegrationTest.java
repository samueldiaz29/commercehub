package com.commercehub.catalog;

import com.commercehub.catalog.application.CatalogQueryService;
import com.commercehub.catalog.application.ProductNotFoundException;
import com.commercehub.catalog.domain.Category;
import com.commercehub.catalog.domain.Product;
import com.commercehub.catalog.repository.CategoryRepository;
import com.commercehub.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogQueryIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired CatalogQueryService catalogQueryService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearCatalog() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void queriesPublishedProductsWithFiltersPagingAndCategoriesAgainstPostgres() {
        Category audio = categoryRepository.saveAndFlush(new Category("Audio", null));
        Category computing = categoryRepository.saveAndFlush(new Category("Computing", null));
        productRepository.saveAndFlush(product("A-1", "Wireless Headphones", audio, true));
        productRepository.saveAndFlush(product("A-2", "Wired Headphones", audio, true));
        productRepository.saveAndFlush(product("A-3", "Hidden Headphones", audio, false));
        productRepository.saveAndFlush(product("C-1", "Laptop", computing, true));

        var firstPage = catalogQueryService.findProducts(0, 1, audio.getId(), "headphones");
        var secondPage = catalogQueryService.findProducts(1, 1, audio.getId(), "headphones");
        var allPublished = catalogQueryService.findProducts(0, 20, null, null);

        assertThat(firstPage.content()).extracting("sku").containsExactly("A-2");
        assertThat(secondPage.content()).extracting("sku").containsExactly("A-1");
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(allPublished.content()).extracting("sku").containsExactly("C-1", "A-2", "A-1");
        assertThat(allPublished.totalElements()).isEqualTo(3);
        assertThat(catalogQueryService.findCategories()).extracting("name")
                .containsExactly("Audio", "Computing");
    }

    @Test
    void retrievesPublishedProductButReturnsNotFoundForHiddenOrUnknownIds() {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        Product visible = productRepository.saveAndFlush(product("VISIBLE", "Visible", category, true));
        Product hidden = productRepository.saveAndFlush(product("HIDDEN", "Hidden", category, false));

        assertThat(catalogQueryService.findProduct(visible.getId()).sku()).isEqualTo("VISIBLE");
        assertThatThrownBy(() -> catalogQueryService.findProduct(hidden.getId()))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> catalogQueryService.findProduct(Long.MAX_VALUE))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void excludesCategoriesWithoutPublishedProducts() {
        Category visible = categoryRepository.saveAndFlush(new Category("Visible", null));
        Category hidden = categoryRepository.saveAndFlush(new Category("Hidden", null));
        productRepository.saveAndFlush(product("VISIBLE", "Visible product", visible, true));
        productRepository.saveAndFlush(product("HIDDEN", "Hidden product", hidden, false));

        assertThat(catalogQueryService.findCategories()).extracting("name").containsExactly("Visible");
    }

    @Test
    void httpEndpointsReturnDocumentedDtosAndProblemDetails() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        Product visible = productRepository.saveAndFlush(product("HTTP-1", "HTTP Headphones", category, true));

        mockMvc.perform(get("/api/v1/products").param("category_id", category.getId().toString())
                        .param("search", "headphones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(visible.getId()))
                .andExpect(jsonPath("$.content[0].category.name").value("Audio"))
                .andExpect(jsonPath("$.content[0].image_url").doesNotExist())
                .andExpect(jsonPath("$.total_elements").value(1));

        mockMvc.perform(get("/api/v1/products/{id}", Long.MAX_VALUE)
                        .header("X-Correlation-Id", "trace-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://commercehub.local/errors/product-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlation_id").value("trace-123"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private Product product(String sku, String name, Category category, boolean published) {
        return new Product(sku, name, null, category, new BigDecimal("19.90"), null, published);
    }
}
