#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(cd -- "$script_dir/.." && pwd)
env_file=${DEPLOY_ENV_FILE:-"$deploy_dir/.env"}
compose=(docker compose --env-file "$env_file" -f "$deploy_dir/compose.prod.yml")
[[ -f $env_file ]] || { printf 'ERROR: missing %s\n' "$env_file" >&2; exit 1; }
[[ $(stat -c '%a' "$env_file") == "600" ]] || { printf 'ERROR: %s must have mode 600\n' "$env_file" >&2; exit 1; }
set -a; . "$env_file"; set +a
[[ ${BOOTSTRAP_CONFIRM:-} == "$KEYCLOAK_REALM" ]] || {
  printf 'Refusing bootstrap. Set BOOTSTRAP_CONFIRM=%s after reviewing the realm/client values.\n' "$KEYCLOAK_REALM" >&2
  exit 1
}

kcadm() { "${compose[@]}" exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kcadm config credentials --server http://127.0.0.1:8080 --realm master --user "$KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME" --password "$KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD"
if kcadm get "realms/$KEYCLOAK_REALM" >/dev/null 2>&1; then
  printf 'ERROR: realm %s already exists; refusing to alter an existing production realm.\n' "$KEYCLOAK_REALM" >&2
  exit 1
fi

kcadm create realms -s "realm=$KEYCLOAK_REALM" -s enabled=true -s registrationAllowed=true -s verifyEmail=true
for role in PARTICIPANT SPECIALIST CONTENT_ADMIN SYSTEM_ADMIN; do
  kcadm create roles -r "$KEYCLOAK_REALM" -s "name=$role"
done
kcadm create clients -r "$KEYCLOAK_REALM" \
  -s "clientId=$KEYCLOAK_CLIENT_ID" -s name="Moves Web" -s enabled=true \
  -s publicClient=true -s standardFlowEnabled=true -s directAccessGrantsEnabled=false \
  -s 'redirectUris=["https://'"$APP_DOMAIN"'/*"]' -s 'webOrigins=["https://'"$APP_DOMAIN"'"]' \
  -s 'attributes={"pkce.code.challenge.method":"S256"}'
client_id=$(kcadm get clients -r "$KEYCLOAK_REALM" -q "clientId=$KEYCLOAK_CLIENT_ID" --fields id --format csv --noquotes | tr -d '\r')
[[ -n $client_id ]] || { printf '%s\n' 'ERROR: created client could not be resolved' >&2; exit 1; }
kcadm create "clients/$client_id/protocol-mappers/models" -r "$KEYCLOAK_REALM" \
  -s name=motion-api-audience -s protocol=openid-connect -s protocolMapper=oidc-audience-mapper \
  -s 'config={"included.custom.audience":"'"$OIDC_AUDIENCE"'","access.token.claim":"true","id.token.claim":"false"}'
printf 'Realm %s and client %s created. Create named users and assign roles in the Keycloak admin console.\n' "$KEYCLOAK_REALM" "$KEYCLOAK_CLIENT_ID"
