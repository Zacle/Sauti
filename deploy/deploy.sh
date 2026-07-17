#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti

: "${IMAGE_TAG:?IMAGE_TAG is required}"

ensure_streaming_provider() {
  local setting="$1"
  local provider="$2"
  local credential="$3"
  local credential_value current_value
  credential_value="$(grep -E "^${credential}=" .env.production | tail -1 | cut -d= -f2- || true)"
  current_value="$(grep -E "^${setting}=" .env.production | tail -1 | cut -d= -f2- || true)"
  if [[ -n "${credential_value}" && ( -z "${current_value}" || "${current_value}" == "noop" ) ]]; then
    grep -v "^${setting}=" .env.production > .env.production.tmp || true
    printf '%s=%s\n' "${setting}" "${provider}" >> .env.production.tmp
    mv .env.production.tmp .env.production
    chmod 600 .env.production
  fi
}

ensure_streaming_provider "SAUTI_STT_STREAMING_PROVIDER" "deepgram" "DEEPGRAM_API_KEY"
ensure_streaming_provider "SAUTI_TTS_STREAMING_PROVIDER" "cartesia" "CARTESIA_API_KEY"

if grep -q '^CARTESIA_API_KEY=.' .env.production; then
  grep -v '^SAUTI_TTS_STREAMING_PROVIDER=' .env.production > .env.production.tmp || true
  printf '%s=%s\n' "SAUTI_TTS_STREAMING_PROVIDER" "cartesia" >> .env.production.tmp
  mv .env.production.tmp .env.production
  chmod 600 .env.production
fi

compose=(docker compose --env-file .env.production -f docker-compose.prod.yml)
previous_tag=""
if [[ -f .deployed-image-tag ]]; then
  previous_tag="$(<.deployed-image-tag)"
fi

docker_root_dir() {
  docker info --format '{{.DockerRootDir}}' 2>/dev/null || printf '%s\n' /var/lib/docker
}

docker_storage_path() {
  local root
  root="$(docker_root_dir)"
  if df -Pk "${root}" >/dev/null 2>&1; then
    printf '%s\n' "${root}"
  else
    # Some non-root deploy users can access Docker through its socket but
    # cannot stat DockerRootDir directly. It normally resides on `/`.
    printf '%s\n' /
  fi
}

available_kib() {
  df -Pk "$(docker_storage_path)" | awk 'NR == 2 { print $4 }'
}

prune_old_sauti_images() {
  local keep_tag_1="${1:-}"
  local keep_tag_2="${2:-}"
  local repository tag

  while read -r repository tag; do
    if [[ ( "${repository}" == "sauti-backend" || "${repository}" == "sauti-dashboard" ) \
      && "${tag}" != "${keep_tag_1}" \
      && "${tag}" != "${keep_tag_2}" \
      && "${tag}" != "<none>" ]]; then
      docker image rm "${repository}:${tag}" || true
    fi
  done < <(docker image ls --format '{{.Repository}} {{.Tag}}')
}

prepare_build_storage() {
  local minimum_kib=$((4 * 1024 * 1024))
  local available

  echo "Docker storage before cleanup:"
  df -h "$(docker_storage_path)"

  # Failed builds never reach the post-deploy cleanup. Reclaim stale caches and
  # old commit-tagged Sauti images before asking Gradle and Next.js to build.
  docker builder prune -f --filter 'until=168h'
  docker image prune -f --filter 'until=168h'
  prune_old_sauti_images "${previous_tag}"

  available="$(available_kib)"
  if (( available < minimum_kib )); then
    echo "Less than 4 GiB is available; removing all unused build cache."
    docker builder prune -af
    docker image prune -f
    available="$(available_kib)"
  fi

  echo "Docker storage available for build: $((available / 1024)) MiB"
  if (( available < minimum_kib )); then
    echo "Insufficient Docker storage: at least 4 GiB is required for a reliable production build."
    exit 1
  fi
}

deployment_diagnostics() {
  echo "Deployment diagnostics (no environment values are printed):"
  "${compose[@]}" ps -a || true

  local backend_id
  backend_id="$("${compose[@]}" ps -q backend 2>/dev/null || true)"
  if [[ -n "${backend_id}" ]]; then
    docker inspect --format \
      'backend state={{.State.Status}} exit={{.State.ExitCode}} oom_killed={{.State.OOMKilled}} restart_count={{.RestartCount}} error={{.State.Error}} image={{.Config.Image}}' \
      "${backend_id}" || true
  else
    echo "backend container was not created"
  fi

  "${compose[@]}" logs --timestamps --tail=300 backend dashboard caddy || true
}

rollback() {
  if [[ -z "${previous_tag}" ]]; then
    echo "No previous image tag is available for rollback."
    return
  fi
  echo "Rolling back to ${previous_tag}."
  export IMAGE_TAG="${previous_tag}"
  "${compose[@]}" up -d --remove-orphans
  "${compose[@]}" exec -T caddy caddy reload \
    --config /etc/caddy/Caddyfile \
    --adapter caddyfile || true
}

prepare_build_storage
"${compose[@]}" build --pull backend dashboard
"${compose[@]}" pull caddy
if ! "${compose[@]}" up -d --remove-orphans; then
  deployment_diagnostics
  rollback
  exit 1
fi
if ! "${compose[@]}" exec -T caddy caddy reload \
    --config /etc/caddy/Caddyfile \
    --adapter caddyfile; then
  deployment_diagnostics
  rollback
  exit 1
fi

domain="$(grep -E '^SAUTI_DOMAIN=' .env.production | tail -1 | cut -d= -f2-)"
domain="${domain:-sauti.uk}"

healthy=false
for attempt in {1..36}; do
  backend_id="$("${compose[@]}" ps -q backend 2>/dev/null || true)"
  if [[ -z "${backend_id}" ]]; then
    echo "Backend container disappeared during health check attempt ${attempt}."
    break
  fi
  backend_state="$(docker inspect --format '{{.State.Status}}' "${backend_id}" 2>/dev/null || true)"
  if [[ "${backend_state}" != "running" ]]; then
    echo "Backend container entered state '${backend_state:-unknown}' during health check attempt ${attempt}."
    break
  fi
  if curl --fail --silent --show-error \
    --resolve "${domain}:443:127.0.0.1" \
    "https://${domain}/health" >/dev/null; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "${healthy}" != "true" ]]; then
  deployment_diagnostics
  rollback
  exit 1
fi

printf '%s\n' "${IMAGE_TAG}" > .deployed-image-tag
prune_old_sauti_images "${IMAGE_TAG}" "${previous_tag}"
docker image prune -f --filter "until=168h"
echo "Sauti ${IMAGE_TAG} is healthy at https://${domain}"
