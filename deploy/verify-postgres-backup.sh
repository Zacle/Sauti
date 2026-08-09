#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

cd /opt/sauti

source_kind="${1:-offsite}"
max_age_hours="${SAUTI_BACKUP_MAX_AGE_HOURS:-36}"
work_dir=""

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
    work_dir="/opt/sauti/restore-drills/work-verify-$(date -u +%Y%m%dT%H%M%SZ)-$$"
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
[[ -n "${backup_path}" ]] || { echo "No Sauti database backup was found in ${source_kind} storage."; exit 1; }
checksum_path="${backup_path}.sha256"
[[ -f "${checksum_path}" ]] || { echo "Checksum is missing for $(basename "${backup_path}")."; exit 1; }

(
  cd "$(dirname "${backup_path}")"
  sha256sum --check "$(basename "${checksum_path}")" >/dev/null
)

age_seconds="$(( $(date -u +%s) - $(stat -c %Y "${backup_path}") ))"
if (( age_seconds < 0 || age_seconds > max_age_hours * 3600 )); then
  echo "Latest ${source_kind} backup is older than ${max_age_hours} hours."
  exit 1
fi

docker run --rm \
  -v "$(dirname "${backup_path}"):/backup:ro" \
  postgres:18-alpine \
  pg_restore --list "/backup/$(basename "${backup_path}")" >/dev/null

echo "Backup verification passed: source=${source_kind} file=$(basename "${backup_path}") age_seconds=${age_seconds}"
