#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

cd /opt/sauti
mkdir -p restore-drills

source_kind="${1:-offsite}"
production_env="${SAUTI_PRODUCTION_ENV_FILE:-.env.production}"
drill_env="${SAUTI_RESTORE_DRILL_ENV_FILE:-.env.restore-drill}"
work_dir=""

[[ -f "${production_env}" ]] || { echo "Production environment file is missing."; exit 1; }
[[ -f "${drill_env}" ]] || { echo "Restore-drill environment file is missing: ${drill_env}"; exit 1; }
drill_mode="$(stat -c %a "${drill_env}")"
(( (8#${drill_mode} & 8#077) == 0 )) || { echo "Restore-drill environment permissions are too broad."; exit 1; }

python3 ./validate-restore-target.py "${production_env}" "${drill_env}"

cleanup() {
  if [[ -n "${work_dir}" && "${work_dir}" == /opt/sauti/restore-drills/work-* ]]; then
    rm -rf -- "${work_dir}"
  fi
}
trap cleanup EXIT

case "${source_kind}" in
  local)
    backup_root="/opt/sauti/backups"
    ;;
  offsite)
    offsite_env="${SAUTI_BACKUP_OFFSITE_ENV_FILE:-.env.backup-offsite}"
    [[ -f "${offsite_env}" ]] || { echo "Off-site backup configuration is missing: ${offsite_env}"; exit 1; }
    offsite_mode="$(stat -c %a "${offsite_env}")"
    (( (8#${offsite_mode} & 8#077) == 0 )) || { echo "Off-site backup configuration permissions are too broad."; exit 1; }
    work_dir="/opt/sauti/restore-drills/work-restore-$(date -u +%Y%m%dT%H%M%SZ)-$$"
    mkdir -p "${work_dir}"
    docker run --rm \
      --env-file "${offsite_env}" \
      -v "${work_dir}:/restore" \
      restic/restic:0.18.0 restore latest --tag sauti-postgres --target /restore
    backup_root="${work_dir}"
    ;;
  *)
    echo "Backup source must be 'local' or 'offsite'."
    exit 1
    ;;
esac

backup_path="$(find "${backup_root}" -type f -name 'sauti-*.dump' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
[[ -n "${backup_path}" ]] || { echo "No Sauti database backup was found."; exit 1; }
checksum_path="${backup_path}.sha256"
[[ -f "${checksum_path}" ]] || { echo "Checksum is missing for $(basename "${backup_path}")."; exit 1; }
(
  cd "$(dirname "${backup_path}")"
  sha256sum --check "$(basename "${checksum_path}")" >/dev/null
)

table_count="$(docker run --rm --env-file "${drill_env}" postgres:18-alpine sh -c \
  'psql "$SAUTI_RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = '\''public'\'' AND table_type = '\''BASE TABLE'\''"')"
table_count="${table_count//[[:space:]]/}"
if [[ "${table_count}" != "0" ]]; then
  echo "Restore target contains ${table_count} public tables; refusing to overwrite it."
  exit 1
fi

docker run --rm \
  --env-file "${drill_env}" \
  -v "$(dirname "${backup_path}"):/backup:ro" \
  postgres:18-alpine sh -c \
  'pg_restore --dbname="$SAUTI_RESTORE_DATABASE_URL" --no-owner --no-privileges --no-comments --exit-on-error "/backup/'"$(basename "${backup_path}")"'"'

validation="$(docker run --rm --env-file "${drill_env}" postgres:18-alpine sh -c \
  'psql "$SAUTI_RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -F "|" -c "
    SELECT
      CASE WHEN to_regclass('\''public.flyway_schema_history'\'') IS NOT NULL THEN 1 ELSE 0 END,
      CASE WHEN to_regclass('\''public.tenants'\'') IS NOT NULL THEN 1 ELSE 0 END,
      CASE WHEN to_regclass('\''public.calls'\'') IS NOT NULL THEN 1 ELSE 0 END,
      CASE WHEN to_regclass('\''public.bookings'\'') IS NOT NULL THEN 1 ELSE 0 END,
      (SELECT count(*) FROM flyway_schema_history),
      (SELECT count(*) FROM tenants),
      (SELECT count(*) FROM calls),
      (SELECT count(*) FROM bookings);"')"

IFS='|' read -r has_migrations has_tenants has_calls has_bookings migrations tenants calls bookings <<< "${validation}"
if [[ "${has_migrations}${has_tenants}${has_calls}${has_bookings}" != "1111" ]]; then
  echo "Restore validation failed because required tables are missing."
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
evidence_path="restore-drills/restore-drill-${timestamp}.json"
checksum="$(sha256sum "${backup_path}" | cut -d' ' -f1)"
python3 - "${evidence_path}" "${timestamp}" "${source_kind}" "$(basename "${backup_path}")" \
  "${checksum}" "${migrations}" "${tenants}" "${calls}" "${bookings}" <<'PY'
import json
import sys
from pathlib import Path

path, timestamp, source, backup, checksum, migrations, tenants, calls, bookings = sys.argv[1:]
evidence = {
    "completedAt": timestamp,
    "source": source,
    "backup": backup,
    "sha256": checksum,
    "status": "passed",
    "validation": {
        "flywayMigrations": int(migrations),
        "tenants": int(tenants),
        "calls": int(calls),
        "bookings": int(bookings),
    },
}
Path(path).write_text(json.dumps(evidence, indent=2) + "\n")
PY
chmod 600 "${evidence_path}"

echo "Restore drill passed. Evidence: /opt/sauti/${evidence_path}"
echo "The isolated target was intentionally retained for operator review; remove it through its database provider after review."
