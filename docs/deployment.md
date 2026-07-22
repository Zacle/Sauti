# Production deployment

Sauti deploys from `main` to one Docker host. GitHub Actions tests the
backend and dashboard, then updates the host over SSH. The host checks out
the exact verified commit and builds immutable Docker images locally.

## Server

Use Ubuntu 24.04 on a server with at least 4 GB RAM. The recommended initial
size is 8 GB because the host runs Spring Boot, Next.js, PostgreSQL, Redis,
ffmpeg, and Caddy.

Install Docker Engine and the Compose plugin, then create the deployment
directory:

```bash
sudo mkdir -p /opt/sauti
sudo chown "$USER":"$USER" /opt/sauti
```

Copy `deploy/.env.production.example` to
`/opt/sauti/.env.production`, replace every placeholder, and restrict it:

```bash
chmod 600 /opt/sauti/.env.production
```

For the initial server only, run `deploy/bootstrap-server-env.sh` after the
database container exists. It generates production secrets directly on the
host and replaces local-only settings without printing the secrets.

Point the `A` and `AAAA` records for `sauti.uk` at the server. Allow inbound
TCP 22, 80, and 443 and UDP 443. PostgreSQL, Redis, the backend, and the
dashboard are intentionally not published on host ports.

## GitHub configuration

Create these repository Actions secrets:

- `VPS_HOST`: server IPv4 address or hostname
- `VPS_USER`: non-root deployment user
- `VPS_SSH_KEY`: private key for that user's authorized public key
- `CARTESIA_API_KEY`: optional; synchronized into the production environment
  when Cartesia is enabled
- `VAPI_API_KEY`: optional; synchronized into the production environment when
  the Vapi browser runtime is enabled

Provider secrets are copied only when the corresponding Actions secret is
non-empty. An absent Actions secret does not remove a value that was already
configured directly in `/opt/sauti/.env.production`.

Protect `main`, require the `backend` and `dashboard` CI jobs, and require pull
requests for changes once additional collaborators join.

## Backups

The included backup script creates a PostgreSQL custom-format dump and retains
seven local days:

```bash
0 2 * * * /opt/sauti/backup-postgres.sh
```

Local backups do not protect against server loss. Copy them to encrypted
off-site object storage with restic or rclone before production customer data
is stored.

The `recordings-data` volume also needs an off-site retention policy if call
recordings are kept locally.
