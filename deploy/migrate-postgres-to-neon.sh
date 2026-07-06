#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti
mkdir -p backups

target_tables="$(
  docker run --rm \
    --env-file .env.external-source \
    pgvector/pgvector:pg16 \
    sh -c 'psql "$NEON_DATABASE_URL" -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = '\''public'\'' AND table_type = '\''BASE TABLE'\'';"'
)"

if [[ "${target_tables//[[:space:]]/}" != "0" ]]; then
  echo "Neon already contains ${target_tables//[[:space:]]/} public tables; refusing to overwrite it."
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump_path="backups/pre-neon-${timestamp}.dump"

docker exec sauti-postgres-1 \
  pg_dump -U sauti -d sauti -Fc \
  > "${dump_path}"

docker run --rm \
  --env-file .env.external-source \
  -v "/opt/sauti/backups:/backups:ro" \
  pgvector/pgvector:pg16 \
  sh -c 'pg_restore --dbname="$NEON_DATABASE_URL" --no-owner --no-privileges --no-comments --exit-on-error "/backups/'"$(basename "${dump_path}")"'"'

docker run --rm \
  --env-file .env.external-source \
  pgvector/pgvector:pg16 \
  sh -c 'psql "$NEON_DATABASE_URL" -v ON_ERROR_STOP=1 -tAc "SELECT '\''tenants='\'' || count(*) FROM tenants UNION ALL SELECT '\''calls='\'' || count(*) FROM calls UNION ALL SELECT '\''migrations='\'' || count(*) FROM flyway_schema_history;"'

echo "Migration completed. Safety dump: /opt/sauti/${dump_path}"
