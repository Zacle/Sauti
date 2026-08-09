# Whop billing activation

Whop is Sauti's active billing adapter. The dashboard continues to call the
provider-neutral `POST /api/v1/billing/checkout` endpoint, so replacing Whop
later does not require a checkout UI rewrite.

## Create the catalog

Create one Whop product for Sauti and six recurring plans:

- Launch monthly and annual
- Growth monthly and annual
- Scale monthly and annual

Copy the stable `plan_...` identifiers into the matching `WHOP_PLAN_*`
variables. Sauti creates a fresh checkout configuration for the selected plan,
adds a signed workspace reference as metadata, and sends the customer to the
returned Whop-hosted `purchase_url`. Prices and billing periods remain owned by
the Whop plans; the browser cannot submit either value.

Configure:

- `SAUTI_BILLING_PROVIDER=whop`
- `WHOP_API_KEY` with a server-only company API key
- `WHOP_COMPANY_ID` with the Sauti Whop business ID
- `WHOP_WEBHOOK_SECRET` with the exact webhook signing secret shown by Whop,
  including its `whsec_` prefix; do not strip, decode, or transform it
- `WHOP_TENANT_REFERENCE_SECRET` with an independent random secret of at least
  32 bytes; do not reuse the API key or webhook secret
- the six `WHOP_PLAN_*` identifiers
- `WHOP_CHECKOUT_REDIRECT_URL=https://sauti.uk/billing?checkout=success`

Keep `WHOP_API_VERSION_DATE=2026-07-20` until a reviewed provider-version
upgrade. Sandbox and production use separate API keys, products, plans, and
webhooks. For sandbox testing, use `https://sandbox-api.whop.com/api/v1` and set
`WHOP_SANDBOX=true`; never combine a sandbox API URL with production IDs.

## Webhook

Create one v1 webhook in Whop's Developer dashboard:

`https://sauti.uk/webhooks/whop`

Subscribe to:

- `membership.activated`
- `membership.deactivated`
- `membership.cancel_at_period_end_changed`
- `payment.succeeded`
- `payment.failed`
- `payment.pending`
- `refund.created`
- `refund.updated`
- `dispute.created`
- `dispute.updated`

Sauti validates the Standard Webhooks signature over the unmodified request
body, rejects events older than five minutes, checks both the signed event ID
and configured Whop company, deduplicates deliveries, persists the event, and
returns quickly. A durable worker applies membership state afterward. Whop
does not guarantee event ordering, so provider timestamps prevent an older
event from overwriting a newer subscription.

Until the next Phase 3 slice adds normalized financial evidence, verified
payment, refund, and dispute events are retained with the explicit `deferred`
state. This avoids claiming that money movement has been reconciled while also
preserving the signed input for an idempotent backfill.

The checkout redirect is presentation only. Sauti never grants a plan from a
`status=success` query parameter; only a verified membership webhook can update
the workspace subscription.

## Safe acceptance

1. Start in Whop sandbox and create all six plans there.
2. Complete a checkout for each plan and confirm the plan changes only after a
   signed membership event.
3. Replay the same webhook ID and confirm it is processed once.
4. Alter the body, signature, company ID, and timestamp independently and
   confirm each request is rejected.
5. Exercise activation, renewal, cancel-at-period-end, deactivation,
   past-due/failed payment, expiration, refund, and dispute journeys.
6. Confirm webhook failures are visible in the Admin Analytics billing-event
   queue.
7. Keep every billing account in `observe` until refund/dispute ledger handling
   and production lifecycle evidence receive a separate review.

The older 2Checkout and Lemon Squeezy adapters remain dormant rollback code.
Do not configure their secrets or select them in production unless a later
review explicitly changes the active provider.
