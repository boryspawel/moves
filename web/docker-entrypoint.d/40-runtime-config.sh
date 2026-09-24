#!/bin/sh
set -eu

if [ -z "${KEYCLOAK_URL:-}" ] || [ -z "${KEYCLOAK_REALM:-}" ] || [ -z "${KEYCLOAK_CLIENT_ID:-}" ]; then
    echo 'KEYCLOAK_URL, KEYCLOAK_REALM, and KEYCLOAK_CLIENT_ID are required at runtime' >&2
    exit 1
fi

newline='
'
case "$KEYCLOAK_URL" in
    *"$newline"*)
        echo 'KEYCLOAK_URL must be an absolute HTTP(S) URL without executable characters' >&2
        exit 1
        ;;
esac
case "$KEYCLOAK_REALM" in
    *"$newline"*)
        echo 'KEYCLOAK_REALM and KEYCLOAK_CLIENT_ID may contain only letters, digits, dot, underscore, and hyphen' >&2
        exit 1
        ;;
esac
case "$KEYCLOAK_CLIENT_ID" in
    *"$newline"*)
        echo 'KEYCLOAK_REALM and KEYCLOAK_CLIENT_ID may contain only letters, digits, dot, underscore, and hyphen' >&2
        exit 1
        ;;
esac

if ! printf '%s' "$KEYCLOAK_URL" | grep -Eq '^https?://[][A-Za-z0-9._~:/?@!$&()*+,;=%#-]+$'; then
    echo 'KEYCLOAK_URL must be an absolute HTTP(S) URL without executable characters' >&2
    exit 1
fi
if ! printf '%s' "$KEYCLOAK_REALM" | grep -Eq '^[A-Za-z0-9._-]+$' || ! printf '%s' "$KEYCLOAK_CLIENT_ID" | grep -Eq '^[A-Za-z0-9._-]+$'; then
    echo 'KEYCLOAK_REALM and KEYCLOAK_CLIENT_ID may contain only letters, digits, dot, underscore, and hyphen' >&2
    exit 1
fi

mkdir -p /var/run/moves
printf 'window.__MOVES_RUNTIME_CONFIG__ = Object.freeze({keycloak: {url: "%s", realm: "%s", clientId: "%s"}});\n' \
    "$KEYCLOAK_URL" "$KEYCLOAK_REALM" "$KEYCLOAK_CLIENT_ID" \
    > /var/run/moves/runtime-config.js
