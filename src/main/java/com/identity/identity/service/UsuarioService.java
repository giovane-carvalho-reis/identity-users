package com.identity.identity.service;

import com.identity.identity.dto.CriarUsuarioRequest;
import com.identity.identity.repository.UsuarioEntity;
import com.identity.identity.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final KeycloakService keycloakService;

    @Transactional
    public UsuarioEntity criarUsuario(CriarUsuarioRequest usuario) {
        String keycloakId = keycloakService.criarUsuario(usuario);
        UsuarioEntity entity = UsuarioEntity.builder()
                .nome(usuario.nome())
                .email(usuario.email())
                .cpf(usuario.cpf())
                .telefone(usuario.telefone())
                .endereco(usuario.endereco())
                .ativo(Boolean.TRUE)
                .keycloakId(keycloakId)
                .build();

        return usuarioRepository.save(entity);
    }

    @Transactional
    public List<UsuarioEntity> listarTodos() {
        return usuarioRepository.findAll();
    }
}
