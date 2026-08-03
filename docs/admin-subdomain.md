# Sauti Admin subdomain

The platform operations console is served from `https://admin.sauti.uk` while
using the existing dashboard, backend, database, and deployment. The subdomain
does not create another hosting account or server.

## DNS

Create this record with the DNS provider for `sauti.uk`:

```text
Type: A
Name: admin
Value: <the same OVH VPS IPv4 address used by sauti.uk>
```

Add an `AAAA` record only if the VPS has correctly configured public IPv6.
Wait for DNS to resolve before deploying the Caddy configuration, because the
apex domain already sends HSTS with `includeSubDomains`.

## Production configuration

```text
SAUTI_ADMIN_DOMAIN=admin.sauti.uk
SAUTI_PLATFORM_ADMIN_EMAILS=<existing verified Sauti account email>
```

`SAUTI_PLATFORM_ADMIN_EMAILS` remains a GitHub Actions secret. The domain is a
non-secret environment value and defaults to `admin.sauti.uk`.

## Isolation behavior

- `https://admin.sauti.uk/` redirects to `/admin`.
- Unauthenticated admin routes redirect to the admin-specific email/password
  login. Google login is intentionally hidden on this origin because its OAuth
  callback and browser session belong to the apex origin.
- A tenant owner cannot enter the console: backend admin APIs require a signed
  `ROLE_PLATFORM_ADMIN` claim and return `403` otherwise.
- Marketing and tenant paths requested on the admin host return to the apex
  host. `/webhooks/*` and `/ws/*` are not exposed by the admin virtual host.
- `https://sauti.uk/admin` redirects to the admin subdomain.
- Browser storage and the session-presence cookie are host-isolated, so an
  administrator signs in separately on the admin subdomain.
- Google-created accounts can use `Forgot password?` on the admin login to set
  an independent Sauti password. Recovery remains on the admin origin and does
  not change the account's Google password.

## Acceptance checks

After the reviewed release deploys:

1. `https://sauti.uk/admin` redirects to `https://admin.sauti.uk/admin`.
2. `https://admin.sauti.uk/` redirects to `/admin`, then to
   `/login?surface=admin&next=%2Fadmin` when signed out.
3. A normal workspace owner receives the explicit access-denied response and
   cannot retrieve `/api/v1/admin/overview`.
4. The configured admin signs in with email/password and sees the platform
   overview and demo-request queue.
5. `https://admin.sauti.uk/webhooks/telnyx/call-control` returns `404`.
6. Response headers include `X-Robots-Tag: noindex, nofollow, noarchive` and
   the restrictive permissions/referrer policies.
