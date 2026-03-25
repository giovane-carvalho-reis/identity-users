package com.identity.identity.controller;

import com.identity.identity.dto.CriarUsuarioRequest;
import com.identity.identity.repository.UsuarioEntity;
import com.identity.identity.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioEntity> salvar(@RequestBody CriarUsuarioRequest usuario) {
        return ResponseEntity.ok(usuarioService.criarUsuario(usuario));
    }

    @GetMapping
    public ResponseEntity<UsuarioEntity> obterPerfil(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return ResponseEntity.ok(usuarioService.buscarPorEmail(email));
    }

}
