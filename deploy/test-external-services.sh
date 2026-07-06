#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti

docker run --rm \
  --env-file .env.external-source \
  pgvector/pgvector:pg16 \
  sh -c 'psql "$NEON_DATABASE_URL" -v ON_ERROR_STOP=1 -tAc "SELECT current_database(), current_user, pg_get_userbyid(datdba), has_schema_privilege(current_user, '\''public'\'', '\''CREATE'\'') FROM pg_database WHERE datname = current_database();"'

docker run --rm \
  --env-file .env.external-source \
  redis:7-alpine \
  sh -c 'redis-cli --tls -h "$UPSTASH_REDIS_HOST" -p "$UPSTASH_REDIS_PORT" -a "$UPSTASH_REDIS_PASSWORD" ping'

docker run --rm \
  --env-file .env.external-source \
  curlimages/curl:8.14.1 \
  sh -c 'curl --silent --show-error --fail --ssl-reqd "smtp://smtp.resend.com:587" >/dev/null'

echo "External service connectivity checks passed."
