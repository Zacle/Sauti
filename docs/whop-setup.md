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

Once a workspace has a Whop membership, Sauti no longer sends upgrades or
downgrades to Whop's generic membership portal. It first looks for one active,
renewing same-customer membership on the exact target plan and safely reuses it
when present. A membership with `cancel_at_period_end=true` is never reused.
Otherwise Sauti first tries to schedule an automatic invoice for the target
plan, anchored to the current renewal date. If Whop reports that automatic
collection is unsupported for the company, Sauti schedules a `send_invoice`
invoice instead. Whop emails that secure invoice at renewal and the customer
must pay it to activate the new plan. The replacement plan never takes effect
before the current paid subscription ends. Sauti ends only the synchronized source
membership at the paid-period boundary; no support operator is involved.

Whop's documented membership update API accepts metadata but does not expose a
target-plan mutation. Sauti therefore uses Whop's saved-payment and invoice APIs
instead of pretending a portal visit completed the change. The future invoice
creates a provider plan for that renewal; Sauti stores the generated plan ID and
only adopts its membership when customer, target, source membership, and tenant
all match the stored request. More than one matching target membership is
rejected rather than guessed. Replacing a pending transition creates the new
future invoice first and then voids the superseded invoice, so only the latest
requested plan should renew. Cancellation is different:
Sauti calls Whop's documented membership cancellation endpoint for the exact
tenant-owned membership and schedules it at the end of the paid period. If a
synchronized membership is marked for cancellation without a completed plan
change, the billing page exposes **Resume renewal** for that exact membership.
Local status remains webhook-driven after Whop accepts the recovery. If a
sandbox account already has two Sauti base
memberships, cancel the unwanted membership in Whop and verify that all six
configured base plan IDs belong to the same Whop product before testing again.

## Create the add-on catalog

Create five additional Whop products, each with one monthly recurring plan:

- Additional agent — USD 29/month → `WHOP_ADDON_AGENT_PLAN_ID`
- Concurrent call line — USD 25/month → `WHOP_ADDON_LINE_PLAN_ID`
- Business phone number — USD 5/month → `WHOP_ADDON_NUMBER_PLAN_ID`
- Premium voice — USD 19/month → `WHOP_ADDON_VOICE_PLAN_ID`
- SMS / WhatsApp messaging — USD 19/month → `WHOP_ADDON_MESSAGING_PLAN_ID`

Keep these as separate products rather than extra pricing options on the base
Sauti product. Each purchase creates an independent recurring membership, so a
base-plan change cannot accidentally remove an add-on. Sauti verifies the
signed workspace reference, configured add-on plan ID, and add-on identifier
before recording the entitlement. An already-active add-on opens its own Whop
membership portal instead of creating a duplicate checkout. The initial
self-service catalog supports one active membership of each add-on type per
workspace; larger quantities remain a custom-sales workflow.

Configure:

- `SAUTI_BILLING_PROVIDER=whop`
- `WHOP_API_KEY` with a server-only company API key 
- `WHOP_COMPANY_ID` with the Sauti Whop business ID
- `WHOP_WEBHOOK_SECRET` with the exact webhook signing secret shown by Whop,
  including its `whsec_` prefix; do not strip, decode, or transform it
- `WHOP_TENANT_REFERENCE_SECRET` with an independent random secret of at least
  32 bytes; do not reuse the API key or webhook secret
- the six `WHOP_PLAN_*` identifiers
- the five `WHOP_ADDON_*_PLAN_ID` identifiers
- `WHOP_CHECKOUT_REDIRECT_URL=https://sauti.uk/billing?checkout=success`

The API key must be created in the same Whop environment and company as the six
plan IDs. A production key cannot authenticate against
`https://sandbox-api.whop.com/api/v1`, and production plan IDs are not visible
to Whop Sandbox. The key must allow checkout-configuration creation plus
`member:basic:read`, `member:email:read`, `member:payment_methods:read`,
`plan:basic:read`, `invoice:create`, `invoice:update`, and `member:manage` for
membership cancellation and renewal recovery. Sauti only receives Whop's tokenized payment-method ID, never card
numbers. A `401` from the checkout-configuration list is an API-key or
environment mismatch; a `404` for every plan normally means the plan IDs belong
to the other environment or company.

## Production environment delivery

The repository-root `.env` is local-only and is never copied to production.
`sauti.uk` reads `/opt/sauti/.env.production` through Docker Compose. To keep
secrets out of source control, add the following under **GitHub repository
Settings → Secrets and variables → Actions** before the next normal deployment:

**Secrets:**

- `WHOP_API_KEY`
- `WHOP_COMPANY_ID`
- `WHOP_WEBHOOK_SECRET`
- `WHOP_TENANT_REFERENCE_SECRET`

**Variables:**

- `SAUTI_BILLING_PROVIDER=whop`
- `WHOP_API_BASE_URL=https://sandbox-api.whop.com/api/v1`
- `WHOP_API_VERSION_DATE=2026-07-20`
- `WHOP_CHECKOUT_REDIRECT_URL=https://sauti.uk/billing?checkout=success`
- `WHOP_SANDBOX=true`
- all six `WHOP_PLAN_*` IDs
- all five `WHOP_ADDON_*_PLAN_ID` IDs

The verified deployment workflow copies only non-empty values into the private
server environment file and restarts the backend through the normal CI/CD path.
Local `.env` values alone cannot change the production checkout status.

Keep `WHOP_API_VERSION_DATE=2026-07-20` until a reviewed provider-version
upgrade. Sandbox and production use separate API keys, products, plans, and
webhooks. For sandbox testing, use `https://sandbox-api.whop.com/api/v1` and set
`WHOP_SANDBOX=true`; never combine a sandbox API URL with production IDs.

The billing page reads the active provider environment from the backend. When
the Whop API key, company ID, redirect URL, and independent tenant-reference
secret and all six base plan IDs are present, it labels the flow as **Whop sandbox checkout** and creates
a real hosted test checkout. If those server settings are missing, the page
shows **Setup required** instead of presenting a non-functional preview. Add-on
purchase buttons become available only when all five add-on plan IDs are also
configured. All IDs must belong to the same Whop environment and company as
the API key.

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

Verified membership, payment, refund, and dispute events are normalized by the
durable worker into immutable, tenant-owned acceptance evidence. These records
contain only operational references, normalized status, amount/currency, and
timestamps; customer details and raw webhook JSON remain in the private
provider inbox and are not exposed through an admin API. Events received before
this evidence layer are replayed idempotently by Flyway migration `V68`.

Evidence means Sauti received and attributed a correctly signed provider event.
Sauti deterministically reconciles that evidence into a current payment
position: the newest state for each refund or dispute resource wins, successful
refunds are deducted once, open disputes are reported as exposure, and lost
disputes are reported as loss. Currency mismatches and impossible deductions
are marked unresolved and excluded from aggregate totals.

This projection does not prove bank settlement, does not adjust communication
balance, and does not suspend a workspace after a refund or dispute. Financial
enforcement remains out of scope until representative live evidence has been
compared with Whop and explicitly approved.

Platform administrators can review the evidence-derived acceptance matrix at
`https://admin.sauti.uk/admin/billing`. The page is read-only and never calls
Whop. It shows credential/webhook/signing presence as booleans, masks plan
references, and never returns secrets or raw provider payloads.

The page also shows separate Sandbox and Live financial summaries by currency.
Refreshing derives them only from stored normalized evidence and never makes a
provider request. Customer data and full payment references are not exposed.

The matrix requires all six Launch, Growth, and Scale monthly/annual plan IDs to
be configured, but it does not require six purchases. One representative new
sandbox subscription must store all three lifecycle events:

1. `membership.activated`;
2. `payment.succeeded`;
3. `membership.cancel_at_period_end_changed` or `membership.deactivated`.

Purchase one configured variant with Whop Sandbox test payment details, wait for
the worker to process its signed activation and payment webhooks, then schedule
cancellation for that exact membership and refresh the matrix. Do not purchase
all six variants and do not use an upgrade/downgrade as an acceptance shortcut.
Whop's unsupported automatic-collection path is not a blocker for this test;
Sauti applies customer plan changes only at the paid-period boundary.

For `payment.succeeded`, Sauti additionally resolves the payment through its
verified membership ownership and queues a payment confirmation to the
workspace account email. The email outbox is provider-payment idempotent and
retries temporary SMTP failures. It includes the purchase, amount, currency,
provider payment reference, confirmation time, and masked card suffix when
available. This customer notification is not represented as financial
reconciliation or a tax invoice.

The checkout redirect is presentation only. Sauti never grants a plan from a
`status=success` query parameter; only a verified membership webhook can update
the workspace subscription.

## Safe acceptance

### Clean up repeated sandbox checkouts first

Every completed checkout creates a separate Whop membership; repeating a base
checkout is not an upgrade. The Sauti billing page labels the single membership
synchronized with the workspace as **Current subscription to keep** and shows
its Whop membership reference. In Whop Dashboard, open **Users**, choose the
customer, and use **Access details** to match that reference. Cancel the other
test memberships. Do not choose between same-priced memberships by price alone.

After a base membership has synchronized, Sauti rejects another base checkout
for that workspace. Selecting another plan stays inside Sauti. One verified
existing target membership is adopted; no match creates a renewal-date invoice;
and multiple matches produce a clear conflict that requires duplicate cleanup.
Once a plan transition is requested or scheduled, it is the workspace's only
pending transition. Sauti shows its target and effective date and rejects any
additional upgrade or downgrade before contacting Whop. The existing request
must complete before another plan change can be requested.

1. Start in Whop sandbox and create all six plans there.
2. Complete the first base checkout, then request Growth to Scale in Sauti.
   Confirm the page stays in Sauti, reports the effective renewal date, the old
   membership is canceling, and Whop contains one future automatic invoice. At
   renewal, confirm the generated membership is adopted from its signed event.
   Also test one pre-existing target membership (reuse) and two matching
   memberships (clear conflict, no new subscription).
3. Purchase one add-on. Confirm it appears separately in Sauti, does not change
   the base plan, and its Manage action opens that add-on membership.
4. Replay the same webhook ID and confirm it is processed once.
5. Alter the body, signature, company ID, and timestamp independently and
   confirm each request is rejected.
6. Exercise activation, renewal, cancel-at-period-end, deactivation,
   past-due/failed payment, expiration, refund, and dispute journeys.
7. Confirm webhook failures are visible in the Admin Analytics billing-event
   queue.
8. Confirm a signed active Whop membership moves that workspace to call-access
   `enforce`, cancel-at-period-end remains callable through its paid-through
   timestamp, and expired, past-due, or unpaid access returns HTTP 402 for new
   browser/web sessions. Inbound calls must hang up before the AI assistant is
   started, while historical calls remain readable. Workspaces without a Whop
   lifecycle remain in `observe`; refund/dispute financial enforcement still
   requires a separate review.

The older 2Checkout and Lemon Squeezy adapters remain dormant rollback code.
Do not configure their secrets or select them in production unless a later
review explicitly changes the active provider.
