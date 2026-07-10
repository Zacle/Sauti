#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/sauti

if grep -q '^# SAUTI_PRODUCTION_OVERRIDES$' .env.production; then
  echo "Production overrides already configured."
  exit 0
fi

# Remove a malformed first-run append produced by cross-shell escaping.
sed -i '/^nSAUTI_DOMAIN=/d' .env.production

db_password="$(openssl rand -hex 24)"
jwt_secret="$(openssl rand -hex 32)"
web_voice_secret="$(openssl rand -hex 32)"
tools_key="$(openssl rand -hex 16)"
webhook_secret="$(openssl rand -hex 32)"

docker exec sauti-postgres-1 psql -U sauti -d sauti \
  -c "ALTER USER sauti WITH PASSWORD '${db_password}';"

{
  echo
  echo "# SAUTI_PRODUCTION_OVERRIDES"
  echo "SAUTI_DOMAIN=sauti.uk"
  echo "POSTGRES_DB=sauti"
  echo "DB_USER=sauti"
  echo "DB_PASSWORD=${db_password}"
  echo "JAVA_OPTS=-Xms256m -Xmx1536m"
  echo "JWT_SECRET=${jwt_secret}"
  echo "SAUTI_WEB_VOICE_TOKEN_SECRET=${web_voice_secret}"
  echo "SAUTI_TOOLS_ENCRYPTION_KEY=${tools_key}"
  echo "SAUTI_WEBHOOK_SIGNING_SECRET=${webhook_secret}"
  echo "AUTH_EXPOSE_DEV_TOKENS=false"
  echo "SAUTI_DOCUMENT_STORAGE_PROVIDER=database"
  echo "GOOGLE_OAUTH_REDIRECT_URI=https://sauti.uk/api/v1/auth/oauth/google/callback"
  echo "GOOGLE_CALENDAR_REDIRECT_URI=https://sauti.uk/api/v1/integrations/google-calendar/callback"
  echo "GOOGLE_SHEETS_REDIRECT_URI=https://sauti.uk/api/v1/integrations/google_sheets/callback"
  echo "HUBSPOT_REDIRECT_URI=https://sauti.uk/api/v1/integrations/hubspot/callback"
  echo "SALESFORCE_REDIRECT_URI=https://sauti.uk/api/v1/integrations/salesforce/callback"
  echo "CALENDLY_REDIRECT_URI=https://sauti.uk/api/v1/integrations/calendly/callback"
} >> .env.production

chmod 600 .env.production
echo "Production environment configured."
