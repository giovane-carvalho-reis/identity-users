package com.identity.identity.service;

import com.identity.identity.config.KeycloakProperties;
import com.identity.identity.dto.CriarUsuarioRequest;
import com.identity.identity.dto.TokenRequest;
import com.identity.identity.dto.TokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakService {

    private static final String DEFAULT_REALM_ROLE = "USER";

    private final RestClient.Builder restClientBuilder;
    private final KeycloakProperties keycloakProperties;

    public KeycloakService(RestClient.Builder restClientBuilder, KeycloakProperties keycloakProperties) {
        this.restClientBuilder = restClientBuilder;
        this.keycloakProperties = keycloakProperties;
    }

    public String criarUsuario(CriarUsuarioRequest request) {
        String adminToken = obterTokenAdministrador();

        Map<String, Object> payload = Map.of(
                "username", request.email(),
                "email", request.email(),
                "enabled", Boolean.TRUE,
                "firstName", request.nome(),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", request.senha(),
                        "temporary", Boolean.FALSE
                ))
        );

        try {
            URI location = keycloakClient().post()
                    .uri("/admin/realms/{realm}/users", keycloakProperties.realm())
                    .headers(headers -> headers.setBearerAuth(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();

            if (location == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak não retornou o identificador do usuário criado.");
            }

            String userId = extrairId(location);
            atribuirRolePadrao(adminToken, userId);
            return userId;
        } catch (RestClientResponseException ex) {
            throw tratarErroKeycloak(ex, "Não foi possível criar o usuário no Keycloak.");
        }
    }

    public TokenResponse gerarToken(TokenRequest request) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.token().clientId());
        formData.add("username", request.username());
        formData.add("password", request.password());

        try {
            TokenResponse response = keycloakClient().post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", keycloakProperties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak não retornou um token válido.");
            }

            return response;
        } catch (RestClientResponseException ex) {
            throw tratarErroKeycloak(ex, "Não foi possível gerar o token no Keycloak.");
        }
    }

    private void atribuirRolePadrao(String adminToken, String userId) {
        try {
            Map<String, Object> role = keycloakClient().get()
                    .uri("/admin/realms/{realm}/roles/{roleName}", keycloakProperties.realm(), DEFAULT_REALM_ROLE)
                    .headers(headers -> headers.setBearerAuth(adminToken))
                    .retrieve()
                    .body(Map.class);

            if (role == null || role.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A role padrão do Keycloak não está disponível.");
            }

            keycloakClient().post()
                    .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", keycloakProperties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw tratarErroKeycloak(ex, "Não foi possível atribuir a role padrão ao usuário.");
        }
    }

    private String obterTokenAdministrador() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.admin().clientId());
        formData.add("username", keycloakProperties.admin().username());
        formData.add("password", keycloakProperties.admin().password());

        try {
            TokenResponse response = keycloakClient().post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", keycloakProperties.admin().realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível autenticar no Keycloak com o usuário administrador.");
            }

            return response.accessToken();
        } catch (RestClientResponseException ex) {
            throw tratarErroKeycloak(ex, "Não foi possível autenticar o serviço no Keycloak.");
        }
    }

    private RestClient keycloakClient() {
        return restClientBuilder
                .baseUrl(keycloakProperties.baseUrl())
                .build();
    }

    private String extrairId(URI location) {
        String path = location.getPath();
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Keycloak retornou uma URI inválida para o usuário criado.");
        }

        String[] segmentos = path.split("/");
        return segmentos[segmentos.length - 1];
    }

    private ResponseStatusException tratarErroKeycloak(RestClientResponseException ex, String mensagemPadrao) {
        if (HttpStatus.CONFLICT.equals(ex.getStatusCode())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "O usuário já existe no Keycloak.", ex);
        }

        if (HttpStatus.UNAUTHORIZED.equals(ex.getStatusCode()) || HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Credenciais do Keycloak inválidas ou sem permissão suficiente.", ex);
        }

        if (ex.getStatusCode().is4xxClientError()) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagemPadrao, ex);
        }

        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagemPadrao, ex);
    }

}

