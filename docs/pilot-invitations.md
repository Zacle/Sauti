# Pilot invitation operations

Pilot invitations are the only approved path for creating a new workspace
while public registration is disabled.

## Configure the operator boundary

Generate a strong random value outside the repository and save it as the
GitHub Actions secret `SAUTI_OPERATOR_API_KEY`. The deploy workflow copies a
non-empty value into the protected production environment. Never place the
real value in source control, screenshots, browser storage, or support logs.

## Approve a demo request

The demo-request notification email includes the request UUID. After reviewing
the prospect and confirming that a pilot is affordable, call:

```powershell
curl.exe -X POST `
  -H "X-Sauti-Operator-Key: <secret>" `
  "https://sauti.uk/api/v1/operator/demo-requests/<request-id>/invitation"
```

The server emails a private activation link to the exact address submitted in
the request. The link expires after 72 hours and cannot be reused. Issuing it
does not provision a phone number or activate paid communication channels.

Until the durable delivery/reissue slice is implemented, verify with the
prospect that the invitation arrived before relying on it for onboarding.
