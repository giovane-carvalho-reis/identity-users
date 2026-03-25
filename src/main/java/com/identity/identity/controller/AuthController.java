package com.identity.identity.controller;

import com.identity.identity.dto.TokenRequest;
import com.identity.identity.dto.TokenResponse;
import com.identity.identity.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakService keycloakService;

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> gerarToken(@RequestBody TokenRequest request) {
        return ResponseEntity.ok(keycloakService.gerarToken(request));
    }
}

