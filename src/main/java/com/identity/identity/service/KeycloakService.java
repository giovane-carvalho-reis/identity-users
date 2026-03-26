package com.identity.identity.service;

import com.identity.identity.config.KeycloakProperties;
import com.identity.identity.dto.CriarUsuarioRequest;
import com.identity.identity.dto.TokenRequest;
import com.identity.identity.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KeycloakService {

    private static final String DEFAULT_REALM_ROLE = "USER";

    private final KeycloakProperties keycloakProperties;
    private final RestClient restClient;

    public KeycloakService(RestClient.Builder restClientBuilder, KeycloakProperties keycloakProperties) {
        this.keycloakProperties = keycloakProperties;
        // Constrói uma única instância — o Builder é singleton; não o mute a cada requisição.
        this.restClient = restClientBuilder
                .baseUrl(keycloakProperties.baseUrl())
                .build();
    }

    // -------------------------------------------------------------------------
    // Criação de usuário
    // -------------------------------------------------------------------------

    public String criarUsuario(CriarUsuarioRequest request) {
        String adminToken = obterTokenAdministrador();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", request.email());
        payload.put("email", request.email());
        payload.put("enabled", Boolean.TRUE);
        payload.put("emailVerified", Boolean.TRUE);
        payload.put("firstName", request.nome());
        if (request.sobrenome() != null && !request.sobrenome().isBlank()) {
            payload.put("lastName", request.sobrenome());
        }
        payload.put("requiredActions", new ArrayList<>());
        payload.put("credentials", List.of(Map.of(
                "type", "password",
                "value", request.senha(),
                "temporary", Boolean.FALSE
        )));

        try {
            URI location = restClient.post()
                    .uri("/admin/realms/{realm}/users", keycloakProperties.realm())
                    .headers(h -> h.setBearerAuth(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();

            if (location == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Keycloak não retornou o identificador do usuário criado.");
            }

            String userId = extrairId(location);
            definirSenhaUsuario(adminToken, userId, request.senha());
            atribuirRolePadrao(adminToken, userId);
            atualizarEstadoUsuarioParaLogin(adminToken, userId, request.email(), request.nome(), request.sobrenome());
            return userId;

        } catch (RestClientResponseException ex) {
            log.error("[Keycloak] erro ao criar usuário '{}': HTTP {} — {}",
                    request.email(), ex.getStatusCode(), ex.getResponseBodyAsString());
            throw tratarErroAdmin(ex, "Não foi possível criar o usuário no Keycloak.");
        }
    }

    // -------------------------------------------------------------------------
    // Geração de token (fluxo do usuário)
    // -------------------------------------------------------------------------

    public TokenResponse gerarToken(TokenRequest request) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.token().clientId());
        formData.add("username", request.username());
        formData.add("password", request.password());

        log.debug("[Keycloak] solicitando token para '{}' via client '{}'",
                request.username(), keycloakProperties.token().clientId());

        try {
            TokenResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", keycloakProperties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak não retornou um token válido.");
            }

            log.debug("[Keycloak] token gerado com sucesso para '{}'", request.username());
            return response;

        } catch (RestClientResponseException ex) {
            // Keycloak retorna 400 (invalid_grant) para senha errada / usuário inexistente
            // Keycloak retorna 401 (unauthorized_client) para client inválido
            // Ambos devem aparecer como 401 para o chamador — são erros de credencial.
            log.warn("[Keycloak] falha ao gerar token para '{}': HTTP {} — {}",
                    request.username(), ex.getStatusCode(), ex.getResponseBodyAsString());

            if (ex.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuário ou senha inválidos.", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Erro interno do servidor de autenticação.", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Operações administrativas (privadas)
    // -------------------------------------------------------------------------

    private String obterTokenAdministrador() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.admin().clientId());
        formData.add("username", keycloakProperties.admin().username());
        formData.add("password", keycloakProperties.admin().password());

        try {
            TokenResponse response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", keycloakProperties.admin().realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Não foi possível autenticar no Keycloak com o usuário administrador.");
            }

            return response.accessToken();

        } catch (RestClientResponseException ex) {
            log.error("[Keycloak] falha ao obter token de admin: HTTP {} — {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível autenticar o serviço no Keycloak. Verifique as credenciais de admin.", ex);
        }
    }

    private void definirSenhaUsuario(String adminToken, String userId, String senha) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}/reset-password",
                            keycloakProperties.realm(), userId)
                    .headers(h -> h.setBearerAuth(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", "password", "value", senha, "temporary", Boolean.FALSE))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.error("[Keycloak] erro ao definir senha do usuário {}: HTTP {} — {}",
                    userId, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw tratarErroAdmin(ex, "Não foi possível definir a senha do usuário no Keycloak.");
        }
    }

    private void atribuirRolePadrao(String adminToken, String userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> role = (Map<String, Object>) restClient.get()
                    .uri("/admin/realms/{realm}/roles/{roleName}",
                            keycloakProperties.realm(), DEFAULT_REALM_ROLE)
                    .headers(h -> h.setBearerAuth(adminToken))
                    .retrieve()
                    .body(Map.class);

            if (role == null || role.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A role padrão '" + DEFAULT_REALM_ROLE + "' não foi encontrada no Keycloak.");
            }

            restClient.post()
                    .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm",
                            keycloakProperties.realm(), userId)
                    .headers(h -> h.setBearerAuth(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException ex) {
            log.error("[Keycloak] erro ao atribuir role ao usuário {}: HTTP {} — {}",
                    userId, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw tratarErroAdmin(ex, "Não foi possível atribuir a role padrão ao usuário.");
        }
    }

    private void atualizarEstadoUsuarioParaLogin(String adminToken, String userId,
                                                  String email, String nome, String sobrenome) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", userId);
        update.put("username", email);
        update.put("email", email);
        update.put("firstName", nome);
        update.put("lastName", sobrenome != null ? sobrenome : "");
        update.put("enabled", Boolean.TRUE);
        update.put("emailVerified", Boolean.TRUE);
        update.put("requiredActions", new ArrayList<>());

        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}", keycloakProperties.realm(), userId)
                    .headers(h -> h.setBearerAuth(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(update)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.error("[Keycloak] erro ao finalizar cadastro do usuário {}: HTTP {} — {}",
                    userId, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw tratarErroAdmin(ex, "Não foi possível finalizar o cadastro do usuário no Keycloak.");
        }
    }

    // -------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------

    private String extrairId(URI location) {
        String path = location.getPath();
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O Keycloak retornou uma URI inválida para o usuário criado.");
        }
        String[] segmentos = path.split("/");
        return segmentos[segmentos.length - 1];
    }

    /** Handler para chamadas da Admin API — 401/403 significa problema de configuração, não de credencial do usuário. */
    private ResponseStatusException tratarErroAdmin(RestClientResponseException ex, String mensagem) {
        if (HttpStatus.CONFLICT.equals(ex.getStatusCode())) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "O usuário já existe no Keycloak.", ex);
        }
        if (HttpStatus.UNAUTHORIZED.equals(ex.getStatusCode()) || HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Credencial de admin inválida ou sem permissão no Keycloak.", ex);
        }
        if (ex.getStatusCode().is4xxClientError()) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem, ex);
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem, ex);
    }
}
