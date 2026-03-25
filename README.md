# Identity Users - Demo simples

Projeto de demo para cadastro de usuarios, geracao de token no Keycloak e validacao JWT entre microsservicos.

## Subir ambiente

```bash
docker compose up -d --build
```

## Links de acesso

- API: `http://localhost:9292`
- Swagger (OpenAPI): `http://localhost:9292/swagger-ui.html`
- Keycloak: `http://localhost:8093`
- Realm issuer (JWT): `http://localhost:8093/realms/identity`

## Base de dados (PostgreSQL)

- Host: `localhost`
- Porta: `5432`
- Usuario: `postgres`
- Senha: `senha123`
- Banco da aplicacao: `usuario`
- Banco do Keycloak: `keycloak`

String de conexao da aplicacao:

`jdbc:postgresql://localhost:5432/usuario`

## Endpoints

- `POST /usuarios` - cadastra usuario no banco local e no Keycloak
- `POST /auth/token` - gera access token no Keycloak
- `GET /usuarios` - endpoint protegido (requer Bearer token)

## cURL - Criar usuario

```bash
curl --request POST "http://localhost:9292/usuarios" \
  --header "Content-Type: application/json" \
  --data "{\
	\"nome\": \"Giovane Carvalho Reis\",\
	\"email\": \"giovane.reis@outlook.com\",\
	\"cpf\": \"12345678901\",\
	\"telefone\": \"11999999999\",\
	\"senha\": \"Admin@123\",\
	\"endereco\": {\
	  \"cep\": \"01001000\",\
	  \"logradouro\": \"Rua A\",\
	  \"numero\": \"10\",\
	  \"complemento\": \"\",\
	  \"bairro\": \"Centro\",\
	  \"cidade\": \"Sao Paulo\",\
	  \"estado\": \"SP\"\
	}\
  }"
```

## cURL - Gerar token

```bash
curl --request POST "http://localhost:9292/auth/token" \
  --header "Content-Type: application/json" \
  --data "{\
	\"username\": \"giovane.reis@outlook.com\",\
	\"password\": \"Admin@123\"\
  }"
```

## cURL - Listar usuarios (protegido)

```bash
curl --request GET "http://localhost:9292/usuarios" \
  --header "Authorization: Bearer SEU_ACCESS_TOKEN"
```

