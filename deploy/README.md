# MOVES production deployment (OVH VPS)

This bundle runs immutable application images on one VPS. Caddy is the only service bound to the host (`80` and `443`); the web server and Keycloak are on the `edge` network, while the backend and both PostgreSQL services use the private `backend` network. The web server is the only component connected to both networks so that its existing `/api/` proxy flow remains unchanged. Neither PostgreSQL, the backend actuator, nor Keycloak's management port is publicly exposed.

## Prerequisites and DNS

- Ubuntu LTS VPS with current security updates, Docker Engine plus the Compose plugin, `curl`, `restic`, `shellcheck`, and enough encrypted off-host S3 capacity.
- DNS `A`/`AAAA` records for `APP_DOMAIN` and `AUTH_DOMAIN` must point to the VPS before the first deployment. Caddy obtains and renews ACME certificates automatically.
- OVH/Ubuntu firewall must permit inbound TCP `80` and `443`, and deny all other application/database ports. Keep SSH restricted to administration addresses or a VPN.
- Publish `ghcr.io` images from CI before deployment. The VPS never builds application images; use a full, immutable Git SHA tag, never `latest`.

## Secrets and first setup

Copy `env.example` to `.env`, replace every placeholder, and protect it:

```bash
cd deploy
cp env.example .env
chmod 600 .env
```

`.env` is intentionally untracked and contains database passwords, the Keycloak bootstrap password, SMTP settings, and restic S3 credentials. Keep the restic password file outside the checkout with mode `600`. Do not use local/test Spring profiles, test-consent flags, demo users, or the local realm import in production.

Set `RELEASE_SHA` to a full SHA already published for both GHCR repositories. Validate before starting:

```bash
docker compose --env-file .env -f compose.prod.yml config -q
```

Usługa `web` działa jako użytkownik nginx (UID/GID 101) z read-only root filesystem. Katalogi runtime nginx i konfiguracji frontendowej są osobnymi tmpfs należącymi do tego użytkownika; nie dodawaj capability ani nie uruchamiaj tej usługi jako root.

On the first deployment, start the stack and then perform the one-time realm/client bootstrap. It never imports the demo realm and refuses to modify an existing realm:

```bash
./scripts/deploy.sh <full-release-sha>
BOOTSTRAP_CONFIRM=moves ./scripts/bootstrap-keycloak.sh
```

Replace `moves` with the exact `KEYCLOAK_REALM` value. `AUTH_DOMAIN` is the public Keycloak hostname; production Compose derives `KEYCLOAK_URL` as `https://${AUTH_DOMAIN}` and passes it with `KEYCLOAK_REALM` and `KEYCLOAK_CLIENT_ID` to the frontend runtime configuration. The script creates that public PKCE client, exact HTTPS redirect/origin, audience mapper, and application roles. It deliberately creates no user. Use the Keycloak admin console to create named operators and assign the minimum required roles; keep the bootstrap admin only for break-glass administration and rotate its password afterwards.

## Deploy, health, logs, and rollback

Run `./scripts/deploy.sh <full-release-sha>`. It serializes deployments, requires a mode-600 environment file, changes only `RELEASE_SHA`, validates Compose, pulls the two exact images, waits for container health, and verifies the public application and OIDC discovery URLs over HTTPS.

Useful checks:

```bash
docker compose --env-file .env -f compose.prod.yml ps
docker compose --env-file .env -f compose.prod.yml logs --tail=200 caddy web backend keycloak
curl --fail https://app.example.invalid/healthz
```

Replace the placeholder domain in the last command. `/actuator/**` is deliberately internal-only and must not be added to Caddy routing.

To return to the previous application image, use `./scripts/rollback.sh <previous-full-release-sha>`. This is an image rollback only: Flyway migrations are forward-only and are never reversed automatically. Before a release containing a migration, require a tested forward-compatible application and a restore-tested backup; otherwise obtain an explicit data migration plan.

## Backups and restore tests

`./scripts/backup.sh` creates PostgreSQL custom-format dumps for both independent databases, copies import artifacts, writes release metadata and SHA-256 manifest, then sends the set to encrypted off-host restic S3 storage. It does not prune snapshots; configure retention separately after agreeing a retention policy.

Periodically retrieve a backup to a non-production directory and run:

```bash
./scripts/restore-test.sh /path/to/retrieved/backup
```

The restore test checks checksums and dump readability, creates unique temporary Docker containers/network, restores both databases, checks the Moves Flyway history and Keycloak tables, and checks import artifacts. It refuses obvious Docker/PostgreSQL production paths and never attaches to the production Compose project or volumes. Do not replace production volumes with this script; an actual disaster restore needs a separately reviewed, maintenance-window runbook.

## Upgrades checklist

1. Confirm DNS, firewall, TLS issuance, GHCR availability, and current backups.
2. Confirm CI built and tested both images for the exact full SHA.
3. Read Flyway changes; verify forward compatibility and a successful isolated restore test.
4. Deploy the SHA, wait for HTTPS health checks, then perform authenticated smoke checks for the web app and Keycloak login.
5. Record release SHA, migration version, backup snapshot, and operator in the change record.
