#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

show_failure_diagnostics() {
  status=$?
  if (( status == 0 )); then
    return
  fi

  echo >&2
  echo "Local development environment did not start (exit $status)." >&2
  echo "Current Compose status:" >&2
  docker compose ps >&2 || true
  echo "Recent service logs:" >&2
  docker compose logs --tail=100 --no-color postgres keycloak backend frontend >&2 || true
  echo "Inspect a service with: docker compose logs -f <service>" >&2
  echo "After fixing configuration, retry: bash scripts/dev-env-up.sh" >&2
}

trap show_failure_diagnostics ERR

docker compose config --quiet
docker compose up --build --wait --wait-timeout 300

compose_host_port() {
  docker compose port "$1" "$2" | head -n 1 | sed 's/.*://'
}

frontend_port="$(compose_host_port frontend 8080)"
backend_port="$(compose_host_port backend 8080)"
keycloak_port="$(compose_host_port keycloak 8080)"

echo
echo "Local development environment is ready:"
docker compose ps
echo
echo "Application: http://localhost:${frontend_port}"
echo "Backend:     http://localhost:${backend_port}/actuator/health"
echo "OpenAPI:     http://localhost:${backend_port}/swagger-ui.html"
echo "Keycloak:    http://localhost:${keycloak_port}"
