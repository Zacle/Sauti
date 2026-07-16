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
docker image prune -f --filter "until=168h"
echo "Sauti ${IMAGE_TAG} is healthy at https://${domain}"
