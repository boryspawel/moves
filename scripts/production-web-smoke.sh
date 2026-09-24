#!/usr/bin/env bash
set -Eeuo pipefail

project="moves-production-web-smoke-$RANDOM-$RANDOM"
temp_dir="$(mktemp -d)"
env_file="$temp_dir/compose.env"
override_file="$temp_dir/compose.override.yml"
image="moves-production-web-smoke:$RANDOM-$RANDOM"
compose=(docker compose --project-name "$project" --env-file "$env_file" -f deploy/compose.prod.yml -f "$override_file")

cleanup() {
  status=$?
  if (( status != 0 )); then
    "${compose[@]}" logs --no-color web caddy || true
  fi
  "${compose[@]}" down --volumes --remove-orphans || true
  docker image rm "$image" >/dev/null 2>&1 || true
  rm -rf "$temp_dir"
  exit "$status"
}
trap cleanup EXIT

cat > "$env_file" <<EOF
APP_DOMAIN=app.localhost
AUTH_DOMAIN=auth.localhost
ACME_EMAIL=ops@localhost
WEB_IMAGE_REPOSITORY=unused/web
BACKEND_IMAGE_REPOSITORY=unused/api
RELEASE_SHA=smoke
MOVES_DB_NAME=moves
MOVES_DB_USER=moves
MOVES_DB_PASSWORD=smoke-only-not-for-production
KEYCLOAK_DB_NAME=keycloak
KEYCLOAK_DB_USER=keycloak
KEYCLOAK_DB_PASSWORD=smoke-only-not-for-production
KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME=admin
KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD=smoke-only-not-for-production
KEYCLOAK_REALM=smoke
KEYCLOAK_CLIENT_ID=smoke-web
OIDC_AUDIENCE=smoke-api
PARTICIPANT_INVITATION_DELIVERY_SENDER=no-reply@smoke.invalid
PARTICIPANT_INVITATION_DELIVERY_SMTP_HOST=smtp.smoke.invalid
EOF

write_override() {
  local url=$1 realm=$2 client_id=$3
  cat > "$override_file" <<EOF
services:
  caddy:
    ports: !override
      - "127.0.0.1::443"
  web:
    image: $image
    environment:
      KEYCLOAK_URL: $url
      KEYCLOAK_REALM: $realm
      KEYCLOAK_CLIENT_ID: $client_id
    ports:
      - "127.0.0.1::8080"
    # The focused smoke does not start backend; this only satisfies nginx's
    # startup-time upstream name resolution. nginx listens only on IPv4, so
    # the IPv6 loopback keeps absent backend requests from reaching web itself.
    # The full Compose smoke exercises the real backend flow.
    extra_hosts:
      backend: "::1"
EOF
}

run_entrypoint() {
  local url=$1 realm=$2 client_id=$3
  docker run --rm --read-only --tmpfs /tmp --tmpfs /var/cache/nginx --tmpfs /var/run \
    -e KEYCLOAK_URL="$url" \
    -e KEYCLOAK_REALM="$realm" \
    -e KEYCLOAK_CLIENT_ID="$client_id" \
    --entrypoint /bin/sh "$image" \
    -c '/docker-entrypoint.d/40-runtime-config.sh; cat /var/run/moves/runtime-config.js'
}

expect_entrypoint_failure() {
  local label=$1 url=$2 realm=$3 client_id=$4
  if run_entrypoint "$url" "$realm" "$client_id" >/dev/null 2>&1; then
    echo "expected runtime configuration validation to fail: $label" >&2
    return 1
  fi
}

first_url=http://runtime-one.invalid:8180
first_realm=runtime-one
first_client=runtime-one-client
second_url=http://runtime-two.invalid:8280
second_realm=runtime-two
second_client=runtime-two-client
expected_first='window.__MOVES_RUNTIME_CONFIG__ = Object.freeze({keycloak: {url: "http://runtime-one.invalid:8180", realm: "runtime-one", clientId: "runtime-one-client"}});'
expected_second='window.__MOVES_RUNTIME_CONFIG__ = Object.freeze({keycloak: {url: "http://runtime-two.invalid:8280", realm: "runtime-two", clientId: "runtime-two-client"}});'

docker build --tag "$image" web

if docker run --rm --entrypoint /bin/sh "$image" \
  -c 'test ! -e /usr/share/nginx/html/assets/runtime-config.js'; then
  :
else
  echo 'runtime configuration must not be baked into static frontend assets' >&2
  exit 1
fi
if docker run --rm --entrypoint /bin/sh "$image" \
  -c "grep -R -F -e '$first_url' -e '$first_realm' -e '$first_client' -e '$second_url' -e '$second_realm' -e '$second_client' /usr/share/nginx/html"; then
  echo 'runtime configuration values must not be baked into static frontend assets' >&2
  exit 1
fi

test "$(run_entrypoint "$first_url" "$first_realm" "$first_client")" = "$expected_first"

invalid_url_lf=$'http://runtime.invalid/\ninjection'
invalid_realm_lf=$'runtime\ninjection'
invalid_client_lf=$'runtime\ninjection'
expect_entrypoint_failure missing-url '' "$first_realm" "$first_client"
expect_entrypoint_failure missing-realm "$first_url" '' "$first_client"
expect_entrypoint_failure missing-client "$first_url" "$first_realm" ''
expect_entrypoint_failure invalid-url-scheme 'javascript:alert(1)' "$first_realm" "$first_client"
expect_entrypoint_failure invalid-url-quote 'http://runtime.invalid/"injection' "$first_realm" "$first_client"
expect_entrypoint_failure invalid-url-backslash 'http://runtime.invalid/\injection' "$first_realm" "$first_client"
expect_entrypoint_failure invalid-url-line-feed "$invalid_url_lf" "$first_realm" "$first_client"
expect_entrypoint_failure invalid-realm-quote "$first_url" 'runtime"injection' "$first_client"
expect_entrypoint_failure invalid-realm-backslash "$first_url" 'runtime\injection' "$first_client"
expect_entrypoint_failure invalid-realm-line-feed "$first_url" "$invalid_realm_lf" "$first_client"
expect_entrypoint_failure invalid-client-quote "$first_url" "$first_realm" 'runtime"injection'
expect_entrypoint_failure invalid-client-backslash "$first_url" "$first_realm" 'runtime\injection'
expect_entrypoint_failure invalid-client-line-feed "$first_url" "$first_realm" "$invalid_client_lf"

caddy_request() {
  local path=$1 headers=$2 body=$3
  curl --silent --show-error --insecure \
    --resolve "app.localhost:$caddy_port:127.0.0.1" \
    --dump-header "$headers" --output "$body" --write-out '%{http_code}' \
    "https://app.localhost:$caddy_port$path"
}

assert_x_frame_options_deny() {
  grep -Eiq '^x-frame-options:[[:space:]]*DENY[[:space:]]*$' "$1"
}

write_override "$first_url" "$first_realm" "$first_client"
"${compose[@]}" config -q
"${compose[@]}" up --no-deps --wait --wait-timeout 60 web

container_id="$("${compose[@]}" ps -q web)"
test -n "$container_id"
docker inspect --format '{{.Config.User}}' "$container_id" | grep -qx '101:101'
docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$container_id" | grep -qx true
cap_drop="$(docker inspect --format '{{json .HostConfig.CapDrop}}' "$container_id")"
grep -Eq '^\["(ALL|CAP_ALL)"\]$' <<<"$cap_drop"
docker inspect --format '{{json .HostConfig.SecurityOpt}}' "$container_id" | grep -Fqx '["no-new-privileges:true"]'
tmpfs_mounts="$(docker inspect --format '{{range $path, $_ := .HostConfig.Tmpfs}}{{$path}}{{"\n"}}{{end}}' "$container_id")"
grep -Fqx /tmp <<<"$tmpfs_mounts"
grep -Fqx /var/cache/nginx <<<"$tmpfs_mounts"
grep -Fqx /var/run <<<"$tmpfs_mounts"
cache_tmpfs="$(docker inspect --format '{{index .HostConfig.Tmpfs "/var/cache/nginx"}}' "$container_id")"
run_tmpfs="$(docker inspect --format '{{index .HostConfig.Tmpfs "/var/run"}}' "$container_id")"
grep -Eq 'uid=101.*gid=101.*mode=0?755|gid=101.*uid=101.*mode=0?755' <<<"$cache_tmpfs"
grep -Eq 'uid=101.*gid=101.*mode=0?755|gid=101.*uid=101.*mode=0?755' <<<"$run_tmpfs"

endpoint="$("${compose[@]}" port web 8080)"
runtime_config="$(curl --fail --silent "http://$endpoint/assets/runtime-config.js")"
test "$runtime_config" = "$expected_first"
curl --fail --silent "http://$endpoint/healthz" | grep -qx ok
headers="$temp_dir/runtime-config.headers"
curl --fail --silent --dump-header "$headers" --output /dev/null "http://$endpoint/assets/runtime-config.js"
grep -Eiq '^content-type: application/javascript' "$headers"
grep -Eiq '^cache-control: no-store' "$headers"
if "${compose[@]}" exec -T web sh -c 'touch /usr/share/nginx/html/runtime-smoke-write' >/dev/null 2>&1; then
  echo 'static frontend assets must remain read-only' >&2
  exit 1
fi

"${compose[@]}" run --rm --no-deps --entrypoint caddy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
"${compose[@]}" up --no-deps --detach caddy
caddy_id="$("${compose[@]}" ps -q caddy)"
test -n "$caddy_id"
caddy_bindings="$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$caddy_id")"
grep -Fq '"443/tcp"' <<<"$caddy_bindings"
! grep -Fq '"80/tcp"' <<<"$caddy_bindings"
grep -Fq '"HostIp":"127.0.0.1"' <<<"$caddy_bindings"
grep -Fq '"HostPort":""' <<<"$caddy_bindings"
caddy_endpoint="$("${compose[@]}" port caddy 443)"
caddy_port="${caddy_endpoint##*:}"
test -n "$caddy_port"
for attempt in {1..30}; do
  if curl --silent --show-error --insecure --output /dev/null \
    --resolve "app.localhost:$caddy_port:127.0.0.1" \
    "https://app.localhost:$caddy_port/healthz"; then
    break
  fi
  sleep 1
done
test "$attempt" -le 30

for path in / /login /assets/runtime-config.js; do
  headers="$temp_dir/caddy$(tr '/' '_' <<<"$path").headers"
  body="$temp_dir/caddy$(tr '/' '_' <<<"$path").body"
  test "$(caddy_request "$path" "$headers" "$body")" = 200
  assert_x_frame_options_deny "$headers"
done
test "$(cat "$temp_dir/caddy_assets_runtime-config.js.body")" = "$expected_first"

headers="$temp_dir/caddy_api.headers"
body="$temp_dir/caddy_api.body"
caddy_request /api/test "$headers" "$body" >/dev/null
assert_x_frame_options_deny "$headers"

headers="$temp_dir/caddy_silent.headers"
body="$temp_dir/caddy_silent.body"
test "$(caddy_request /silent-check-sso.html "$headers" "$body")" = 200
! grep -Eiq '^x-frame-options:' "$headers"
grep -Eiq "^content-security-policy:[[:space:]]*frame-ancestors 'self'[[:space:]]*$" "$headers"
test "$(cat "$body")" = '<!doctype html><html><body><script>parent.postMessage(location.href, location.origin)</script></body></html>'

for path in /silent-check-sso.html/ /silent-check-sso.html.suffix; do
  headers="$temp_dir/caddy$(tr '/' '_' <<<"$path").headers"
  body="$temp_dir/caddy$(tr '/' '_' <<<"$path").body"
  test "$(caddy_request "$path" "$headers" "$body")" = 200
  assert_x_frame_options_deny "$headers"
done

write_override "$second_url" "$second_realm" "$second_client"
"${compose[@]}" up --no-deps --force-recreate --wait --wait-timeout 60 web
endpoint="$("${compose[@]}" port web 8080)"
runtime_config="$(curl --fail --silent "http://$endpoint/assets/runtime-config.js")"
test "$runtime_config" = "$expected_second"
