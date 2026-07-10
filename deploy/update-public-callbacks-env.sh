#!/usr/bin/env bash
set -Eeuo pipefail

env_file="${1:-/opt/sauti/.env.production}"
domain="${2:-sauti.uk}"

if [[ ! -f "${env_file}" ]]; then
  echo "${env_file} not found"
  exit 1
fi

python3 - "${env_file}" "${domain}" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
domain = sys.argv[2].strip().strip("/")
https = f"https://{domain}"
wss = f"wss://{domain}"

updates = {
    "DASHBOARD_BASE_URL": https,
    "SAUTI_CORS_ALLOWED_ORIGINS": https,
    "SAUTI_WEB_VOICE_WEBSOCKET_URL": wss,
    "PUBLIC_BASE_URL": https,
    "TELNYX_MEDIA_WEBSOCKET_BASE_URL": f"{wss}/ws/telnyx/media",
    "GOOGLE_OAUTH_REDIRECT_URI": f"{https}/api/v1/auth/oauth/google/callback",
    "GOOGLE_CALENDAR_REDIRECT_URI": f"{https}/api/v1/integrations/google-calendar/callback",
    "GOOGLE_SHEETS_REDIRECT_URI": f"{https}/api/v1/integrations/google_sheets/callback",
    "HUBSPOT_REDIRECT_URI": f"{https}/api/v1/integrations/hubspot/callback",
    "SALESFORCE_REDIRECT_URI": f"{https}/api/v1/integrations/salesforce/callback",
    "CALENDLY_REDIRECT_URI": f"{https}/api/v1/integrations/calendly/callback",
    "SAUTI_LLM_WEBHOOK_URL": f"{https}/agent-turn",
    "SAUTI_LLM_DRAFT_WEBHOOK_URL": f"{https}/agent-draft",
    "SAUTI_CALENDAR_WEBHOOK_URL": f"{https}/calendar-events",
}

seen = set()
new_lines = []
for line in path.read_text().splitlines():
    stripped = line.strip()
    if "=" in stripped and not stripped.startswith("#"):
        key = stripped.split("=", 1)[0].strip()
        if key in updates:
            new_lines.append(f"{key}={updates[key]}")
            seen.add(key)
            continue
    new_lines.append(line)

if missing := [key for key in updates if key not in seen]:
    new_lines.append("")
    new_lines.append("# Public production callback URLs.")
    for key in missing:
        new_lines.append(f"{key}={updates[key]}")

path.write_text("\n".join(new_lines).rstrip() + "\n")
PY

chmod 600 "${env_file}"
echo "Updated public callback URLs in ${env_file} for ${domain}."
