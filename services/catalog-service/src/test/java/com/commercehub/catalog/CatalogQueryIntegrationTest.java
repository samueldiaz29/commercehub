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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

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

    @Test
    void adminCanCreateProductAndItAppearsInPublicCatalog() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        String request = """
                {"sku":"ADMIN-1","name":"Admin headphones","description":"Wireless",
                 "category_id":%d,"price":49.90,"currency":"PEN","published":true}
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/admin/products")
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ADMIN-1"))
                .andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(get("/api/v1/products").param("search", "Admin headphones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("ADMIN-1"));
    }

    @Test
    void adminCreationRejectsDuplicateSkuAndMissingCategory() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        productRepository.saveAndFlush(product("ADMIN-DUP", "Existing", category, false));
        String duplicate = """
                {"sku":"ADMIN-DUP","name":"Duplicate","category_id":%d,
                 "price":10.00,"currency":"PEN"}
                """.formatted(category.getId());
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://commercehub.local/errors/product-conflict"));

        String missingCategory = """
                {"sku":"ADMIN-NO-CATEGORY","name":"Missing category","category_id":999999,
                 "price":10.00,"currency":"PEN"}
                """;
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(missingCategory))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://commercehub.local/errors/category-not-found"));
    }

    @Test
    void adminUpdatePersistsEditableFieldsAndKeepsPublicationState() throws Exception {
        Category oldCategory = categoryRepository.saveAndFlush(new Category("Old category", null));
        Category newCategory = categoryRepository.saveAndFlush(new Category("New category", null));
        Product product = productRepository.saveAndFlush(product("UPDATE-1", "Original", oldCategory, true));
        String request = """
                {"sku":"UPDATE-2","name":"Updated product","description":"Updated description",
                 "category_id":%d,"price":65.25,"currency":"PEN","image_url":"https://example.test/new.webp"}
                """.formatted(newCategory.getId());

        mockMvc.perform(put("/api/v1/admin/products/{id}", product.getId())
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("UPDATE-2"))
                .andExpect(jsonPath("$.category.name").value("New category"))
                .andExpect(jsonPath("$.price").value(65.25))
                .andExpect(jsonPath("$.published").value(true));

        Product persisted = productRepository.findById(product.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Updated product");
        assertThat(persisted.getCategory().getId()).isEqualTo(newCategory.getId());
        assertThat(persisted.getPrice()).isEqualByComparingTo("65.25");
    }

    @Test
    void adminDisablePersistsAndImmediatelyHidesProductFromSearchAndDetailIdempotently() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        Product product = productRepository.saveAndFlush(product("DISABLE-1", "Disable me", category, true));
        String request = "{\"published\":false}";

        mockMvc.perform(patch("/api/v1/admin/products/{id}", product.getId())
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
        mockMvc.perform(patch("/api/v1/admin/products/{id}", product.getId())
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        assertThat(productRepository.findById(product.getId()).orElseThrow().isPublished()).isFalse();
        mockMvc.perform(get("/api/v1/products").param("search", "Disable me"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(0));
        mockMvc.perform(get("/api/v1/products/{id}", product.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminUpdateRejectsSkuOwnedByAnotherProductWithoutChangingEitherProduct() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        Product first = productRepository.saveAndFlush(product("FIRST-SKU", "First", category, true));
        productRepository.saveAndFlush(product("TAKEN-SKU", "Second", category, true));
        String update = """
                {"sku":"TAKEN-SKU","name":"Changed","category_id":%d,
                 "price":20.00,"currency":"PEN"}
                """.formatted(category.getId());

        mockMvc.perform(put("/api/v1/admin/products/{id}", first.getId())
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());

        assertThat(productRepository.findById(first.getId()).orElseThrow().getSku()).isEqualTo("FIRST-SKU");
        assertThat(productRepository.findBySku("TAKEN-SKU")).isPresent();
    }

    @Test
    void rejectsCustomerAndAnonymousAdministrativeRequestsButKeepsPublicApiOpen() throws Exception {
        String request = """
                {"sku":"SEC-1","name":"Security product","category_id":1,
                 "price":10.00,"currency":"PEN"}
                """;

        mockMvc.perform(post("/api/v1/admin/products")
                        .with(jwt().authorities(createAuthorityList("ROLE_CUSTOMER")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/products").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanUpdateOrDisableProducts() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        Product product = productRepository.saveAndFlush(product("SEC-2", "Security product", category, true));
        String update = """
                {"sku":"SEC-3","name":"Security product updated","category_id":%d,
                 "price":10.00,"currency":"PEN"}
                """.formatted(category.getId());
        String disable = "{\"published\":false}";

        mockMvc.perform(put("/api/v1/admin/products/{id}", product.getId())
                        .with(jwt().authorities(createAuthorityList("ROLE_CUSTOMER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/products/{id}", product.getId())
                        .with(jwt().authorities(createAuthorityList("ROLE_CUSTOMER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(disable))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/products/{id}", product.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/admin/products/{id}", product.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(disable))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidAdminRequestWithProblemDetails() throws Exception {
        String request = """
                {"sku":"","name":"Bad price","category_id":1,
                 "price":0,"currency":"USD"}
                """;
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(jwt().authorities(createAuthorityList("ROLE_ADMIN")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private Product product(String sku, String name, Category category, boolean published) {
        return new Product(sku, name, null, category, new BigDecimal("19.90"), null, published);
    }
}
