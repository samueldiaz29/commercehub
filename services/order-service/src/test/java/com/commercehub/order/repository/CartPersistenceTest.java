package com.commercehub.order.repository;

import com.commercehub.order.domain.Cart;
import com.commercehub.order.application.CartLine;
import com.commercehub.order.application.CartPersistenceService;
import com.commercehub.order.domain.CartConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(CartPersistenceService.class)
class CartPersistenceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired
    CartRepository carts;

    @Autowired
    CartPersistenceService persistence;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void flywayPersistsCartItemsAndEnforcesIntegrity() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version='1'", Integer.class))
                .isEqualTo(1);

        Cart cart = new Cart("customer-1");
        cart.addProduct(42L, 2);
        carts.saveAndFlush(cart);

        assertThat(carts.findByCustomerIdWithItems("customer-1").orElseThrow().getItems())
                .hasSize(1);
        assertThat(jdbc.queryForObject(
                "select quantity from cart_items where cart_id=? and product_id=42",
                Integer.class,
                cart.getId()))
                .isEqualTo(2);
    }

    @Test
    void postgresRejectsInvalidQuantity() {
        Long cartId = carts.saveAndFlush(new Cart("customer-2")).getId();

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into cart_items(cart_id,product_id,quantity) values (?, ?, ?)",
                cartId, 43L, 0));
    }

    @Test
    void postgresRejectsDuplicateProduct() {
        Cart cart = new Cart("customer-3");
        cart.addProduct(44L, 1);
        carts.saveAndFlush(cart);

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into cart_items(cart_id,product_id,quantity) values (?, ?, ?)",
                cart.getId(), 44L, 1));
    }

    @Test
    void persistsQuantityUpdatesAndDeletesArticles() {
        persistence.add("customer-mutations", 101L, 2, List.of());
        persistence.update("customer-mutations", 101L, 5, List.of(new CartLine(101L, 2)));

        assertThat(persistence.getItems("customer-mutations"))
                .containsExactly(new CartLine(101L, 5));

        persistence.remove("customer-mutations", 101L, List.of(new CartLine(101L, 5)));
        assertThat(persistence.getItems("customer-mutations")).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAddsToAnExistingCartAreSerialized() throws Exception {
        persistence.add("customer-concurrent", 77L, 1, List.of());
        List<CartLine> expected = List.of(new CartLine(77L, 1));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                persistence.add("customer-concurrent", 77L, 2, expected);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                persistence.add("customer-concurrent", 77L, 3, expected);
                return null;
            });

            start.countDown();
            List<Exception> failures = new java.util.ArrayList<>();
            for (var operation : List.of(first, second)) {
                try {
                    operation.get();
                } catch (java.util.concurrent.ExecutionException exception) {
                    failures.add((Exception) exception.getCause());
                }
            }
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst()).isInstanceOf(CartConcurrentModificationException.class);

            List<CartLine> items = persistence.getItems("customer-concurrent");
            assertThat(items).hasSize(1);
            assertThat(items.getFirst().quantity()).isIn(3, 4);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void postgresRejectsDuplicateCustomerCart() {
        carts.saveAndFlush(new Cart("customer-duplicate"));

        assertThrows(DataAccessException.class,
                () -> carts.saveAndFlush(new Cart("customer-duplicate")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void postgresRejectsItemsWithoutACart() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into cart_items(cart_id,product_id,quantity) values (?, ?, ?)",
                Long.MAX_VALUE, 90L, 1));
    }
}
