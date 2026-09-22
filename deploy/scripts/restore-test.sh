#!/usr/bin/env bash
set -Eeuo pipefail

[[ $# -eq 1 ]] || { printf 'usage: %s <local-backup-directory>\n' "$0" >&2; exit 2; }
backup_dir=$(cd -- "$1" && pwd)
[[ -f $backup_dir/SHA256SUMS && -f $backup_dir/moves.dump && -f $backup_dir/keycloak.dump ]] || { printf '%s\n' 'ERROR: backup directory is incomplete' >&2; exit 1; }
[[ $backup_dir != /var/lib/postgresql* && $backup_dir != /var/lib/docker* ]] || { printf '%s\n' 'ERROR: refusing a production data path' >&2; exit 1; }
(cd "$backup_dir" && sha256sum -c SHA256SUMS)
pg_restore --list "$backup_dir/moves.dump" >/dev/null
pg_restore --list "$backup_dir/keycloak.dump" >/dev/null

suffix="restore-$(date +%s)-$$"
network="moves-$suffix"
moves_container="moves-db-$suffix"
keycloak_container="keycloak-db-$suffix"
tmp_root=$(mktemp -d)
cleanup() {
  docker rm -f "$moves_container" "$keycloak_container" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tmp_root"
}
trap cleanup EXIT
docker network create "$network" >/dev/null
docker run -d --name "$moves_container" --network "$network" -e POSTGRES_DB=moves_restore -e POSTGRES_USER=moves_restore -e POSTGRES_PASSWORD=restore-only postgres:18.1-alpine >/dev/null
docker run -d --name "$keycloak_container" --network "$network" -e POSTGRES_DB=keycloak_restore -e POSTGRES_USER=keycloak_restore -e POSTGRES_PASSWORD=restore-only postgres:18.1-alpine >/dev/null
for pair in "$moves_container:moves_restore" "$keycloak_container:keycloak_restore"; do
  container=${pair%%:*}
  db_user=${pair#*:}
  ready=false
  for _ in $(seq 1 30); do
    if docker exec "$container" pg_isready -U "$db_user" -d "$db_user" >/dev/null 2>&1; then ready=true; break; fi
    sleep 1
  done
  [[ $ready == true ]] || { printf 'ERROR: restore database did not become ready\n' >&2; exit 1; }
done
docker cp "$backup_dir/moves.dump" "$moves_container:/tmp/moves.dump"
docker cp "$backup_dir/keycloak.dump" "$keycloak_container:/tmp/keycloak.dump"
docker exec "$moves_container" pg_restore -U moves_restore -d moves_restore --exit-on-error /tmp/moves.dump
docker exec "$keycloak_container" pg_restore -U keycloak_restore -d keycloak_restore --exit-on-error /tmp/keycloak.dump
docker exec "$moves_container" psql -U moves_restore -d moves_restore -Atc "select count(*) from flyway_schema_history" | grep -Eq '^[1-9][0-9]*$'
docker exec "$keycloak_container" psql -U keycloak_restore -d keycloak_restore -Atc "select count(*) from information_schema.tables where table_schema = 'public'" | grep -Eq '^[1-9][0-9]*$'
[[ -d $backup_dir/import-artifacts ]] || { printf '%s\n' 'ERROR: import artifacts are missing' >&2; exit 1; }
find "$backup_dir/import-artifacts" -type f -print -quit >/dev/null
printf '%s\n' 'Isolated restore test passed: checksums, custom dumps, Flyway history, Keycloak tables, and artifacts verified.'
