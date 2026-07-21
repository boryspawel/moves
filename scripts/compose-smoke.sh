#!/usr/bin/env bash
set -Eeuo pipefail

project="moves-smoke-$RANDOM-$RANDOM"
env_file="$(mktemp)"
cleanup() {
  status=$?
  if (( status != 0 )); then
    docker compose --project-name "$project" --env-file "$env_file" logs --no-color || true
  fi
  docker compose --project-name "$project" --env-file "$env_file" down --volumes --remove-orphans || true
  rm -f "$env_file"
  exit "$status"
}
trap cleanup EXIT

cat > "$env_file" <<EOF
POSTGRES_DB=motion_smoke
POSTGRES_USER=motion
POSTGRES_PASSWORD=smoke-only-not-for-production
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=smoke-only-not-for-production
FRONTEND_HOST_PORT=14200
BACKEND_HOST_PORT=18080
KEYCLOAK_HOST_PORT=18180
POSTGRES_HOST_PORT=15432
EOF

docker compose --project-name "$project" --env-file "$env_file" up --build --wait --wait-timeout 300
curl --fail --silent http://localhost:14200/ >/dev/null
curl --fail --silent http://localhost:14200/plan >/dev/null
curl --fail --silent http://localhost:18080/actuator/health >/dev/null
curl --fail --silent http://localhost:18180/realms/motion-local/.well-known/openid-configuration >/dev/null
curl --fail --silent http://localhost:14200/api/actuator/health >/dev/null
curl --fail --silent -o /dev/null -w '%{http_code}' http://localhost:18080/api/v1/identity/current | grep -qx '401'
