#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti
mkdir -p backups

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
database="$(grep -E '^POSTGRES_DB=' .env.production | tail -1 | cut -d= -f2-)"
user="$(grep -E '^DB_USER=' .env.production | tail -1 | cut -d= -f2-)"
database="${database:-sauti}"
user="${user:-sauti}"

docker compose --env-file .env.production -f docker-compose.prod.yml \
  exec -T postgres pg_dump -U "${user}" -d "${database}" -Fc \
  > "backups/sauti-${timestamp}.dump"

find backups -type f -name 'sauti-*.dump' -mtime +7 -delete
