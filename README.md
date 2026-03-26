# Identity Service — Demo Pós-Graduação Arquitetura de Software

Demonstração de autenticação centralizada com **Spring Boot + Keycloak + PostgreSQL**,  
totalmente containerizada via Docker Compose.

---

## Arquitetura

```
┌──────────────┐   REST    ┌──────────────────┐   Admin API  ┌──────────────┐
│   Cliente    │ ────────► │ Identity Service  │ ───────────► │  Keycloak    │
│ (Postman/    │           │  Spring Boot :9292│              │  (IAM) :8093 │
│  curl)       │ ◄──────── │                  │ ◄─────────── │              │
└──────────────┘   JWT     └────────┬─────────┘   JWT JWKS   └──────────────┘
                                    │
                                    │ JPA / Hibernate
                                    ▼
                           ┌──────────────────┐
                           │   PostgreSQL      │
                           │   :5432           │
                           └──────────────────┘
```

**Fluxo principal**
1. `POST /usuarios` → cria usuário no Keycloak via Admin API e persiste no Postgres
2. `POST /auth/token` → delega ao Keycloak e retorna `access_token` JWT
3. `GET  /auth/validate` → valida o JWT localmente (chave pública JWKS) e retorna claims
4. `GET  /usuarios` → endpoint protegido, retorna perfil do usuário autenticado

---

## Subir o ambiente

```bash
# 1. Limpar volumes antigos (necessário ao trocar realm-identity.json)
docker compose down -v

# 2. Subir tudo (build incluso)
docker compose up --build
```

Aguarde as mensagens:
- `[keycloak-config] concluido.` → Keycloak configurado
- `Started IdentityApplication` → API pronta

---

## Portas e credenciais

| Serviço             | URL                                | Credenciais         |
|---------------------|------------------------------------|---------------------|
| Identity Service    | http://localhost:9292              | —                   |
| Keycloak Console    | http://localhost:8093/admin        | admin / admin123    |
| PostgreSQL          | localhost:5432                     | postgres / senha123 |
| Health check        | http://localhost:9292/actuator/health | —                |

---

## Endpoints da API

### 1. Criar usuário
```http
POST http://localhost:9292/usuarios
Content-Type: application/json

{
  "nome": "Eduardo",
  "sobrenome": "Silva",
  "email": "eduardo@outlook.com",
  "cpf": "12345678901",
  "telefone": "11999999999",
  "senha": "Senha@123",
  "endereco": {
    "logradouro": "Av. Paulista",
    "numero": "1000",
    "cidade": "São Paulo",
    "estado": "SP",
    "cep": "01310100"
  }
}
```

### 2. Gerar token JWT
```http
POST http://localhost:9292/auth/token
Content-Type: application/json

{
  "username": "eduardo@outlook.com",
  "password": "Senha@123"
}
```

### 3. Validar token (endpoint protegido)
```http
GET http://localhost:9292/auth/validate
Authorization: Bearer <access_token>
```

### 4. Obter perfil do usuário autenticado
```http
GET http://localhost:9292/usuarios
Authorization: Bearer <access_token>
```

### 5. Health check da aplicação
```http
GET http://localhost:9292/actuator/health
```

---

## Pontos de discussão para a aula

- **Separação de responsabilidades** — o serviço nunca armazena senhas; toda autenticação é delegada ao Keycloak
- **JWT stateless** — o resource server valida o token apenas com a chave pública (JWKS), sem consultar o Keycloak a cada requisição
- **Admin API vs. fluxo do usuário** — criação de usuário usa o fluxo administrativo (client_credentials via admin-cli); geração de token usa o fluxo do usuário (password grant via identity-public-client)
- **Health checks encadeados** — o Docker Compose garante a ordem: Postgres → Keycloak → Identity Service
- **Idempotência** — o `keycloak-config` roda a cada `up` e aplica configurações sem falhar se já estiverem corretas
