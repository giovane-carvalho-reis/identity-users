package com.identity.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.keycloak")
public record KeycloakProperties(
        String baseUrl,
        String realm,
        Admin admin,
        Token token
) {
    public record Admin(
            String realm,
            String username,
            String password,
            String clientId
    ) {
    }

    public record Token(
            String clientId
    ) {
    }
}

