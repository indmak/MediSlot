# Secrets

This directory holds secret **files** that are mounted into the containers at
`/run/secrets` and read by the app via Spring's `configtree` importer. Secrets
never go through environment variables, so they do **not** show up in
`docker inspect` / `docker exec ... printenv`.

> `secrets/**` is git-ignored except for the `*.example` templates and this
> README. Never commit real secret files.

## Files

| File | Used by | Purpose |
|------|---------|---------|
| `db_root_password` | `db` (MySQL) | MySQL `root` password |
| `db_password` | `db` + `app` | MySQL app-user password; also the app's datasource password |
| `medislot/external/deepseek/apikey` | `app` | DeepSeek API key (for future use) |

## Setup

Create each file **without a trailing newline** (a stray `\n` becomes part of the value):

```bash
cd /www/wwwroot/medi.goposy.com
mkdir -p secrets/medislot/external/deepseek

# Generate strong passwords (or reuse existing ones)
printf '%s' "$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)" > secrets/db_root_password
printf '%s' "$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)" > secrets/db_password

# DeepSeek key (add when you have it)
printf '%s' 'sk-your-deepseek-key' > secrets/medislot/external/deepseek/apikey

# Lock down permissions
chmod 700 secrets
find secrets -type f -exec chmod 600 {} \;

# The app container runs as a non-root user, so give it ownership of the files
# (root can still read; other host users cannot). Check the uid with:
#   docker run --rm --entrypoint id medislot-app:latest
chown -R 1001:1001 secrets
```

Then (re)start the stack:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## How the app reads it

`application-docker.yml` imports the mounted directory:

```yaml
spring:
  config:
    import: optional:configtree:/run/secrets/
  datasource:
    password: ${db_password}
```

Nested paths become dotted property names, e.g.
`secrets/medislot/external/deepseek/apikey` → property `medislot.external.deepseek.apikey`,
bound by `ExternalApiProperties`.

## Changing a secret

```bash
printf '%s' 'new-value' > secrets/db_password
docker compose -f docker-compose.prod.yml up -d app    # recreate to re-read
```

> **Note:** the MySQL root/app passwords are only applied when the database is
> **first initialized**. Changing `secrets/db_password` after that will *not*
> change the existing MySQL user — update the database user to match, or start
> from a fresh data directory.

## Why not `.env` / environment variables?

Environment variables are visible to anyone with root or Docker access
(`docker inspect`, `printenv`, `/proc/<pid>/environ`). Mounted secret files
avoid that surface. They are still readable by root on the host — if you need
stronger isolation, use an external secret manager (Vault, Infisical, Doppler,
…) and inject short-lived credentials instead.
