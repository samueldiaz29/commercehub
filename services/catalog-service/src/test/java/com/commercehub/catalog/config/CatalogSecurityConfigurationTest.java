package com.commercehub.catalog.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSecurityConfigurationTest {

    private final CatalogSecurityConfiguration configuration = new CatalogSecurityConfiguration();

    @Test
    void mapsKeycloakRealmRolesToSpringRoleAuthorities() {
        Jwt adminToken = tokenWithRoles("ADMIN", "CUSTOMER");

        assertThat(configuration.keycloakAuthoritiesConverter().convert(adminToken))
                .map(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_CUSTOMER");
    }

    @Test
    void producesNoAuthoritiesWhenRealmRolesAreMissing() {
        Jwt token = Jwt.withTokenValue("simulated")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of()))
                .build();

        assertThat(configuration.keycloakAuthoritiesConverter().convert(token)).isEmpty();
    }

    private Jwt tokenWithRoles(String... roles) {
        return Jwt.withTokenValue("simulated")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }
}
