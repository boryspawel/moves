#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(cd -- "$script_dir/.." && pwd)
env_file=${DEPLOY_ENV_FILE:-"$deploy_dir/.env"}
compose=(docker compose --env-file "$env_file" -f "$deploy_dir/compose.prod.yml")

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
[[ $# -eq 1 ]] || die "usage: $0 <full-release-sha>"
sha=$1
[[ $sha =~ ^[0-9a-f]{40,64}$ ]] || die "release SHA must be a full lowercase hexadecimal Git SHA"
[[ -f $env_file ]] || die "missing $env_file; copy deploy/env.example and set real secrets"
[[ $(stat -c '%a' "$env_file") == "600" ]] || die "$env_file must have mode 600"
grep -q '^RELEASE_SHA=' "$env_file" || die "$env_file must contain RELEASE_SHA"

lock_file="$deploy_dir/.deploy.lock"
exec 9>"$lock_file"
flock -n 9 || die "another deployment is running"

tmp=$(mktemp "$deploy_dir/.env.XXXXXX")
trap 'rm -f "$tmp"' EXIT
sed "s/^RELEASE_SHA=.*/RELEASE_SHA=$sha/" "$env_file" >"$tmp"
chmod 600 "$tmp"
mv "$tmp" "$env_file"
trap - EXIT

"${compose[@]}" config -q
"${compose[@]}" pull backend web
"${compose[@]}" up -d --wait --wait-timeout 180

set -a
. "$env_file"
set +a
for url in "https://${APP_DOMAIN}/healthz" "https://${AUTH_DOMAIN}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration"; do
  curl --fail --silent --show-error --retry 12 --retry-delay 5 "$url" >/dev/null || die "public health check failed: $url"
done
printf 'Deployment of %s is healthy. Record the previous SHA before the next deployment.\n' "$sha"
