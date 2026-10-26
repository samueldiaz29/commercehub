package com.commercehub.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class CatalogSecurityConfiguration {

    @Bean
    SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http,
                                                   Converter<Jwt, Collection<GrantedAuthority>> authorities) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authorities);
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)));
        return http.build();
    }

    @Bean
    Converter<Jwt, Collection<GrantedAuthority>> keycloakAuthoritiesConverter() {
        return jwt -> {
            Object realmAccessClaim = jwt.getClaims().get("realm_access");
            if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)
                    || !(realmAccess.get("roles") instanceof List<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        };
    }
}
