#!/usr/bin/env bash
set -Eeuo pipefail
root_dir="$(cd "$(dirname "$0")/.." && pwd)"
run_dir="$(mktemp -d)"; project="moves-p3-$RANDOM-$RANDOM"
frontend_port=$((20000 + RANDOM % 10000)); backend_port=$((30000 + RANDOM % 10000)); keycloak_port=$((40000 + RANDOM % 10000)); mailpit_port=$((50000 + RANDOM % 10000))
admin_password="$(openssl rand -hex 24)"; specialist_password="$(openssl rand -hex 24)"; participant_password="$(openssl rand -hex 24)"
specialist="p3-specialist-$RANDOM"; participant="p3-participant-$RANDOM"; email="$participant@local.test"
cleanup() { local code=$?; docker compose -p "$project" --env-file "$run_dir/env" -f "$root_dir/compose.yaml" -f "$run_dir/compose.override.yaml" down --volumes --remove-orphans >/dev/null 2>&1 || true; rm -rf "$run_dir"; exit "$code"; }; trap cleanup EXIT
origin="http://localhost:$frontend_port"
sed "s#http://localhost:4200#$origin#g" "$root_dir/infra/keycloak/motion-local-realm.json" > "$run_dir/realm.json"
cat > "$run_dir/env" <<EOF
POSTGRES_DB=motion_p3
POSTGRES_USER=motion
POSTGRES_PASSWORD=$(openssl rand -hex 24)
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=$admin_password
FRONTEND_HOST_PORT=$frontend_port
BACKEND_HOST_PORT=$backend_port
KEYCLOAK_HOST_PORT=$keycloak_port
POSTGRES_HOST_PORT=$((10000 + RANDOM % 9000))
MAILPIT_SMTP_HOST_PORT=$((11000 + RANDOM % 9000))
MAILPIT_UI_HOST_PORT=$mailpit_port
EOF
cat > "$run_dir/compose.override.yaml" <<EOF
services:
  keycloak:
    volumes:
      - $run_dir/realm.json:/opt/keycloak/data/import/motion-local-realm.json:ro
EOF
docker compose -p "$project" --env-file "$run_dir/env" -f "$root_dir/compose.yaml" -f "$run_dir/compose.override.yaml" up --build --wait --wait-timeout 420 >/dev/null
kc="http://localhost:$keycloak_port"; token=$(curl -fsS -d 'grant_type=password' -d 'client_id=admin-cli' --data-urlencode 'username=admin' --data-urlencode "password=$admin_password" "$kc/realms/master/protocol/openid-connect/token" | jq -r .access_token)
create_user() { local user=$1 pass=$2 mail=$3 roles=$4 id; id=$(curl -fsS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "{\"username\":\"$user\",\"email\":\"$mail\",\"emailVerified\":true,\"enabled\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"$pass\",\"temporary\":false}]}" "$kc/admin/realms/motion-local/users" -D - -o /dev/null | sed -n 's#.*users/\([^[:space:]]*\).*#\1#p' | tail -1); for role in $roles; do curl -fsS -H "Authorization: Bearer $token" "$kc/admin/realms/motion-local/roles/$role" | jq '[{id:.id,name:.name}]' | curl -fsS -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d @- "$kc/admin/realms/motion-local/users/$id/role-mappings/realm" >/dev/null; done; }
create_user "$specialist" "$specialist_password" "$specialist@local.test" SPECIALIST
create_user "$participant" "$participant_password" "$email" PARTICIPANT
(cd "$root_dir/web" && E2E_BASE_URL="$origin" E2E_API_ORIGIN="http://localhost:$backend_port" P3_MAILPIT_ORIGIN="http://127.0.0.1:$mailpit_port" P3_SPECIALIST_USERNAME="$specialist" P3_SPECIALIST_PASSWORD="$specialist_password" P3_PARTICIPANT_USERNAME="$participant" P3_PARTICIPANT_PASSWORD="$participant_password" P3_PARTICIPANT_EMAIL="$email" npx playwright test e2e/participant-access-real.spec.ts --project=chromium)
