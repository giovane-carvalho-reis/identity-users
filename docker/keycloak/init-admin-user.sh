#!/bin/bash

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
KEYCLOAK_MASTER_REALM="${KEYCLOAK_MASTER_REALM:-master}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-identity}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin123}"

INITIAL_ADMIN_USERNAME="${INITIAL_ADMIN_USERNAME:-giovane.reis@outlook.com}"
INITIAL_ADMIN_EMAIL="${INITIAL_ADMIN_EMAIL:-giovane.reis@outlook.com}"
INITIAL_ADMIN_FIRST_NAME="${INITIAL_ADMIN_FIRST_NAME:-Giovane}"
INITIAL_ADMIN_LAST_NAME="${INITIAL_ADMIN_LAST_NAME:-Carvalho Reis}"
INITIAL_ADMIN_PASSWORD="${INITIAL_ADMIN_PASSWORD:-Admin@123}"

KCADM="/opt/keycloak/bin/kcadm.sh"

echo "[keycloak-init] aguardando Keycloak responder..."
until "$KCADM" config credentials \
  --server "$KEYCLOAK_URL" \
  --realm "$KEYCLOAK_MASTER_REALM" \
  --user "$KEYCLOAK_ADMIN_USER" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" > /dev/null 2>&1; do
  echo "[keycloak-init] ainda aguardando... (5s)"
  sleep 5
done

echo "[keycloak-init] Keycloak pronto. Verificando usuario no realm '$KEYCLOAK_REALM'..."

EXISTING=$("$KCADM" get users -r "$KEYCLOAK_REALM" -q "username=$INITIAL_ADMIN_USERNAME" 2>/dev/null || echo "[]")

if echo "$EXISTING" | grep -q '"id"'; then
  echo "[keycloak-init] usuario ja existe: $INITIAL_ADMIN_USERNAME"
else
  echo "[keycloak-init] criando usuario: $INITIAL_ADMIN_USERNAME"
  if "$KCADM" create users -r "$KEYCLOAK_REALM" \
    -s "username=$INITIAL_ADMIN_USERNAME" \
    -s "email=$INITIAL_ADMIN_EMAIL" \
    -s "firstName=$INITIAL_ADMIN_FIRST_NAME" \
    -s "lastName=$INITIAL_ADMIN_LAST_NAME" \
    -s "enabled=true" \
    -s "emailVerified=true"; then
    echo "[keycloak-init] usuario criado com sucesso"
  else
    echo "[keycloak-init] ERRO ao criar usuario" && exit 1
  fi
fi

echo "[keycloak-init] definindo senha..."
if "$KCADM" set-password -r "$KEYCLOAK_REALM" \
  --username "$INITIAL_ADMIN_USERNAME" \
  --new-password "$INITIAL_ADMIN_PASSWORD"; then
  echo "[keycloak-init] senha definida"
else
  echo "[keycloak-init] ERRO ao definir senha" && exit 1
fi

echo "[keycloak-init] atribuindo role ADMIN..."
"$KCADM" add-roles -r "$KEYCLOAK_REALM" \
  --uusername "$INITIAL_ADMIN_USERNAME" \
  --rolename ADMIN > /dev/null 2>&1 \
  && echo "[keycloak-init] role ADMIN atribuida" \
  || echo "[keycloak-init] role ADMIN ja atribuida ou nao encontrada (ignorando)"

echo "[keycloak-init] concluido para $INITIAL_ADMIN_USERNAME"
exit 0
