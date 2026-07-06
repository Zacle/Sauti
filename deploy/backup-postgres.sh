#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti
mkdir -p backups

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"

docker run --rm \
  --env-file .env.production \
  -v "/opt/sauti/backups:/backups" \
  pgvector/pgvector:pg16 \
  sh -c 'pg_dump "$NEON_DATABASE_URL" -Fc > "/backups/sauti-'"${timestamp}"'.dump"'

find backups -type f -name 'sauti-*.dump' -mtime +7 -delete
