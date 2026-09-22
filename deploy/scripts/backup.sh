#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(cd -- "$script_dir/.." && pwd)
env_file=${DEPLOY_ENV_FILE:-"$deploy_dir/.env"}
compose=(docker compose --env-file "$env_file" -f "$deploy_dir/compose.prod.yml")
[[ -f $env_file ]] || { printf 'ERROR: missing %s\n' "$env_file" >&2; exit 1; }
[[ $(stat -c '%a' "$env_file") == "600" ]] || { printf 'ERROR: %s must have mode 600\n' "$env_file" >&2; exit 1; }
set -a; . "$env_file"; set +a
command -v restic >/dev/null || { printf '%s\n' 'ERROR: restic is required for encrypted off-host backup' >&2; exit 1; }

backup_root=${BACKUP_ROOT:-/var/backups/moves}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="$backup_root/$timestamp"
umask 077
mkdir -p "$backup_dir/import-artifacts"

"${compose[@]}" exec -T moves-db pg_dump -U "$MOVES_DB_USER" -Fc "$MOVES_DB_NAME" >"$backup_dir/moves.dump"
"${compose[@]}" exec -T keycloak-db pg_dump -U "$KEYCLOAK_DB_USER" -Fc "$KEYCLOAK_DB_NAME" >"$backup_dir/keycloak.dump"
"${compose[@]}" cp backend:/var/lib/moves/exercise-import/. "$backup_dir/import-artifacts"
{
  printf 'created_at_utc=%s\n' "$timestamp"
  printf 'release_sha=%s\n' "$RELEASE_SHA"
  printf 'moves_database=%s\nkeycloak_database=%s\n' "$MOVES_DB_NAME" "$KEYCLOAK_DB_NAME"
} >"$backup_dir/release-metadata.env"
(cd "$backup_dir" && sha256sum moves.dump keycloak.dump release-metadata.env >SHA256SUMS)
restic backup --tag moves --tag "$RELEASE_SHA" "$backup_dir"
printf 'Encrypted off-host backup completed: %s\n' "$backup_dir"
