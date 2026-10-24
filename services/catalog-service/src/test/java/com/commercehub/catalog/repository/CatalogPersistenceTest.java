package com.commercehub.catalog.repository;

import com.commercehub.catalog.domain.Category;
import com.commercehub.catalog.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CatalogPersistenceTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void appliesFlywayMigrationAndPersistsCategoryAndProduct() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1'", Integer.class))
                .isEqualTo(1);

        Category category = categoryRepository.saveAndFlush(new Category("Audio", "Audio devices"));
        Product product = productRepository.saveAndFlush(product("AUD-001", category, "1299.90"));

        Product persisted = productRepository.findBySku("AUD-001").orElseThrow();
        assertThat(persisted.getId()).isEqualTo(product.getId());
        assertThat(persisted.getCategory().getId()).isEqualTo(category.getId());
        assertThat(persisted.getPrice()).isEqualByComparingTo("1299.90");
        assertThat(persisted.getCurrency().name()).isEqualTo("PEN");
        assertThat(persisted.isPublished()).isTrue();
    }

    @Test
    void rejectsDuplicateSkuInPostgres() {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        insertProduct("AUD-DUP", category.getId(), new BigDecimal("10.00"));

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct("AUD-DUP", category.getId(), new BigDecimal("20.00")));
    }

    @Test
    void rejectsNonPositivePriceInDomainAndPostgres() {
        Category category = categoryRepository.saveAndFlush(new Category("Audio", null));
        assertThrows(IllegalArgumentException.class,
                () -> product("AUD-ZERO", category, "0.00"));
        assertThrows(IllegalArgumentException.class,
                () -> product("AUD-NEG", category, "-1.00"));

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct("AUD-DB-NEG", category.getId(), new BigDecimal("-1.00")));
    }

    @Test
    void rejectsProductForMissingCategoryInPostgres() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct("AUD-NO-CATEGORY", Long.MAX_VALUE, new BigDecimal("10.00")));
    }

    private void insertProduct(String sku, Long categoryId, BigDecimal price) {
        jdbcTemplate.update("""
                INSERT INTO products (sku, name, category_id, price, currency, published)
                VALUES (?, 'Test product', ?, ?, 'PEN', FALSE)
                """, sku, categoryId, price);
    }

    private Product product(String sku, Category category, String price) {
        return new Product(sku, "Test product", null, category,
                new BigDecimal(price), null, true);
    }
}
