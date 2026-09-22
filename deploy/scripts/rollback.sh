#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
[[ $# -eq 1 ]] || { printf 'usage: %s <previous-full-release-sha>\n' "$0" >&2; exit 2; }
printf '%s\n' 'Rollback changes application image SHA only. It never reverses Flyway migrations.' >&2
exec "$script_dir/deploy.sh" "$1"
