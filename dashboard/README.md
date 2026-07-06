# Sauti web

Next.js dashboard and marketing site for Sauti.

## Local development

The Spring Boot API is expected at `http://localhost:8080`. Override it with
`SAUTI_API_BASE_URL` when required.

```powershell
npm.cmd install
npm.cmd run dev
```

The web application runs on `http://localhost:8088` and proxies `/api/*` to the
backend configured in `next.config.ts`.

`SAUTI_AUTH_BYPASS=true` keeps console routes open during the UI design phase.
Set it to `false` when route protection should be enforced.

## Checks

```powershell
npm.cmd run typecheck
npm.cmd run build
```

## Architecture

```text
app/
  (marketing)/    public route shells
  (auth)/         authentication route shells
  (console)/      console routes wrapped by AuthProvider and AppShell
  (onboarding)/   full-screen onboarding route
features/         domain-owned components, data, and styles
components/       shared domain-agnostic UI
hooks/            shared React hooks and client caches
lib/api/          API transport split by backend domain
types/            shared API contracts
styles/           tokens, reset, marketing, and console foundations
middleware.ts     console route protection
```

Console authentication enforcement uses the non-sensitive
`sauti.session.present` cookie. JWT values remain in browser storage and are
attached only by the API client.
