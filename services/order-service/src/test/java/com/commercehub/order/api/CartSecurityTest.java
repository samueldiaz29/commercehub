package com.commercehub.order.api;

import com.commercehub.order.application.CartService;
import com.commercehub.order.config.OrderSecurityConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import(OrderSecurityConfiguration.class)
class CartSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CartService carts;

    @Test
    void customerCanAccessTheirJWTScopedCart() throws Exception {
        when(carts.get("alice")).thenReturn(new CartResponse(
                "PEN",
                List.of(),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")));

        mvc.perform(get("/api/v1/cart").with(SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.subject("alice"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());

        verify(carts).get("alice");
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanAddUsingJWTIdentityWithoutCSRFToken() throws Exception {
        when(carts.add("alice", 42L, 2)).thenReturn(new CartResponse(
                "PEN",
                List.of(),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")));

        mvc.perform(post("/api/v1/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"product_id":42,"quantity":2,"customer_id":"bob"}
                                """))
                .andExpect(status().isOk());

        verify(carts).add("alice", 42L, 2);
    }

    @Test
    void anonymousCannotAddItems() throws Exception {
        mvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":42,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanRemoveAnItemAndReceivesUpdatedCart() throws Exception {
        when(carts.remove("alice", 42L)).thenReturn(new CartResponse(
                "PEN",
                List.of(),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")));

        mvc.perform(delete("/api/v1/cart/items/42")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());

        verify(carts).remove("alice", 42L);
    }

    @Test
    void customerCanUpdateQuantityForAnItem() throws Exception {
        when(carts.update("alice", 42L, 3)).thenReturn(new CartResponse(
                "PEN",
                List.of(),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/cart/items/42")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk());

        verify(carts).update("alice", 42L, 3);
    }

    @Test
    void rejectsInvalidQuantitiesWithProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":42,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        org.mockito.Mockito.verifyNoInteractions(carts);
    }

    @Test
    void authenticatedUserWithoutCustomerRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/cart").with(SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(jwt -> jwt.subject("admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void mapsKeycloakRealmRolesToSpringAuthorities() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("alice")
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                .build();
        var authorities = new OrderSecurityConfiguration().keycloakRoles().convert(token);

        Assertions.assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }
}
