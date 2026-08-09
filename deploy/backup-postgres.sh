#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

cd /opt/sauti
mkdir -p backups

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_name="sauti-${timestamp}.dump"
partial_name=".${backup_name}.partial"
env_file="${SAUTI_PRODUCTION_ENV_FILE:-.env.production}"

cleanup() {
  rm -f "backups/${partial_name}"
}
trap cleanup EXIT

docker run --rm \
  --env-file "${env_file}" \
  -v "/opt/sauti/backups:/backups" \
  postgres:18-alpine \
  sh -c 'umask 077; pg_dump "$NEON_DATABASE_URL" --format=custom --no-owner --no-privileges --file="/backups/'"${partial_name}"'"'

docker run --rm \
  -v "/opt/sauti/backups:/backups:ro" \
  postgres:18-alpine \
  pg_restore --list "/backups/${partial_name}" >/dev/null

mv "backups/${partial_name}" "backups/${backup_name}"
(
  cd backups
  sha256sum "${backup_name}" > "${backup_name}.sha256"
)
trap - EXIT

offsite_env="${SAUTI_BACKUP_OFFSITE_ENV_FILE:-.env.backup-offsite}"
if [[ -f "${offsite_env}" ]] && grep -Eq '^RESTIC_REPOSITORY=.+$' "${offsite_env}"; then
  offsite_mode="$(stat -c %a "${offsite_env}")"
  if (( (8#${offsite_mode} & 8#077) != 0 )); then
    echo "Off-site backup configuration must not be accessible by group or other users."
    exit 1
  fi
  docker run --rm \
    --env-file "${offsite_env}" \
    -v "/opt/sauti/backups:/data:ro" \
    restic/restic:0.18.0 \
    backup "/data/${backup_name}" "/data/${backup_name}.sha256" \
    --tag sauti-postgres --host sauti-production
fi

find backups -type f \( -name 'sauti-*.dump' -o -name 'sauti-*.dump.sha256' \) -mtime +7 -delete

echo "Validated backup created: /opt/sauti/backups/${backup_name}"
