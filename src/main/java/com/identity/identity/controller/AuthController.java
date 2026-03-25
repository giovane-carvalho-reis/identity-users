package com.identity.identity.controller;

import com.identity.identity.dto.TokenRequest;
import com.identity.identity.dto.TokenResponse;
import com.identity.identity.dto.TokenValidationResponse;
import com.identity.identity.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakService keycloakService;

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> gerarToken(@RequestBody TokenRequest request) {
        return ResponseEntity.ok(keycloakService.gerarToken(request));
    }

    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validarToken(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");

        @SuppressWarnings("unchecked")
        List<String> roles = realmAccess != null
                ? (List<String>) realmAccess.get("roles")
                : List.of();

        return ResponseEntity.ok(new TokenValidationResponse(
                true,
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                roles,
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        ));
    }
}
