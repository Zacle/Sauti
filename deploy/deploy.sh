#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti

: "${IMAGE_TAG:?IMAGE_TAG is required}"

compose=(docker compose --env-file .env.production -f docker-compose.prod.yml)
previous_tag=""
if [[ -f .deployed-image-tag ]]; then
  previous_tag="$(<.deployed-image-tag)"
fi

"${compose[@]}" build --pull backend dashboard
"${compose[@]}" pull caddy
"${compose[@]}" up -d --remove-orphans
"${compose[@]}" exec -T caddy caddy reload \
  --config /etc/caddy/Caddyfile \
  --adapter caddyfile

domain="$(grep -E '^SAUTI_DOMAIN=' .env.production | tail -1 | cut -d= -f2-)"
domain="${domain:-sauti.uk}"

healthy=false
for _ in {1..36}; do
  if curl --fail --silent --show-error \
    --resolve "${domain}:443:127.0.0.1" \
    "https://${domain}/health" >/dev/null; then
    healthy=true
    break
  fi
  sleep 5
done

if [[ "${healthy}" != "true" ]]; then
  "${compose[@]}" logs --tail=150 backend dashboard caddy
  if [[ -n "${previous_tag}" ]]; then
    echo "Health check failed; rolling back to ${previous_tag}."
    export IMAGE_TAG="${previous_tag}"
    "${compose[@]}" up -d --remove-orphans
  fi
  exit 1
fi

printf '%s\n' "${IMAGE_TAG}" > .deployed-image-tag
docker image prune -f --filter "until=168h"
echo "Sauti ${IMAGE_TAG} is healthy at https://${domain}"
