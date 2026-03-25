package com.identity.identity.dto;

import com.identity.identity.repository.Endereco;

public record CriarUsuarioRequest(
        String nome,

        String email,

        String cpf,

        String telefone,

        String senha,

        Endereco endereco
) {
}

