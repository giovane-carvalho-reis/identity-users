#!/bin/bash
# =============================================================================
#  keycloak-config.sh
#  Executado pelo container keycloak-config apos o Keycloak estar healthy.
#  Usa arquivo .sh separado para evitar problemas de CRLF do Windows
#  quando o script e embutido em YAML multiline.
# =============================================================================

KCADM=/opt/keycloak/bin/kcadm.sh
KC_URL=http://keycloak:8080
KC_REALM=identity

echo "[keycloak-config] autenticando..."
"$KCADM" config credentials \
  --server  "$KC_URL" \
  --realm   master    \
  --user    admin     \
  --password admin123

echo "[keycloak-config] ajustando realm $KC_REALM..."
"$KCADM" update "realms/$KC_REALM" \
  -s verifyEmail=false \
  -s loginWithEmailAllowed=true \
  -s duplicateEmailsAllowed=false

echo "[keycloak-config] desabilitando VERIFY_PROFILE..."
"$KCADM" update "authentication/required-actions/VERIFY_PROFILE" \
  -r "$KC_REALM" -s enabled=false -s defaultAction=false

"$KCADM" update "authentication/required-actions/UPDATE_PASSWORD" \
  -r "$KC_REALM" -s enabled=true -s defaultAction=false

"$KCADM" update "authentication/required-actions/VERIFY_EMAIL" \
  -r "$KC_REALM" -s enabled=true -s defaultAction=false

echo "[keycloak-config] tornando lastName opcional no User Profile..."
cat > /tmp/user-profile.json << 'EOF'
{"attributes":[{"name":"username","displayName":"${username}","validations":{"length":{"min":3,"max":255},"username-prohibited-characters":{},"up-username-not-idn-homograph":{}},"annotations":{},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"email","displayName":"${email}","validations":{"email":{},"length":{"max":255}},"annotations":{},"required":{"roles":["user"]},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"firstName","displayName":"${firstName}","required":{"roles":["user"]},"validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"annotations":{},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"lastName","displayName":"${lastName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"annotations":{},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false}],"groups":[{"name":"user-metadata","displayHeader":"User metadata","displayDescription":""}]}
EOF

"$KCADM" update "users/profile" -r "$KC_REALM" -f /tmp/user-profile.json

echo "[keycloak-config] concluido."

