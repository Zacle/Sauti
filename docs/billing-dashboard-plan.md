# Billing Dashboard Product and Implementation Plan

Date: 2026-08-02

Status: preview UI implemented; billing remains in preview mode and is not
enforced. Durable shadow metering, a read-only projection API, and payment
provider integration are still pending.

## Product decision

Sauti should use hybrid billing: a fixed subscription with included AI minutes,
plus transparent usage-based overage and explicitly activated add-ons. During
the current testing period, the dashboard will explain and simulate this model
without charging customers, changing plans, pausing agents, blocking calls, or
creating invoices.

The dashboard must make that distinction unmistakable:

- Operational usage is real when it comes from completed Sauti calls.
- Prices, forecasts, plan changes, overages, add-ons, and invoices are previews.
- Every billing screen displays `Billing preview - no charges are being made`.
- At 100% usage, calls continue normally in testing mode.
- Actions use `Preview`, `Model`, or `Save test preference`; never `Pay`,
  `Upgrade now`, or another label that implies a real financial transaction.

## Source alignment

The plan preserves the SRS billing requirements while separating the testing
experience from future enforcement:

- BL-02: monthly allowance and per-minute overage.
- BL-03: tenant-scoped usage metering, with Redis as a fast counter and durable
  PostgreSQL reconciliation before billing goes live.
- BL-04: warnings at 80% and 100%.
- BL-05: future limit behaviour, redesigned as an explicit spend-control policy
  rather than an unexplained automatic pause.
- BL-07: usage and invoice visibility in the dashboard.
- BL-08: a no-card trial.

The SRS plan names and prices are superseded by the current public pricing
proposal: Launch $49/100 minutes, Growth $149/750 minutes, and Scale
$399/2,500 minutes, with 10% annual savings. The SRS behavioural requirements
remain the baseline.

## Experience principles

1. **Answer the three billing questions first.** The first viewport must show
   the current plan, usage remaining, and projected month-end cost.
2. **Separate actuals from estimates.** Real usage, simulated charges, and
   future behaviour must never share an unlabeled total.
3. **Prevent surprises.** Show the bill formula, overage rate, activated
   add-ons, warning thresholds, and simulated spending cap before any plan
   comparison.
4. **Protect service continuity.** A live call is never interrupted. Future
   enforcement applies only to the next call and follows a configured fallback.
5. **Use progressive disclosure.** Keep the overview readable for a small
   business owner; put provider-level usage and calculation detail behind
   `View breakdown`.
6. **Design for trust, not urgency.** Avoid countdowns, preselected paid
   upgrades, hidden overages, disabled downgrade paths, and other dark patterns.
7. **Support the actual markets.** Layouts must remain usable on mobile and with
   Arabic RTL. Currency, tax, carrier destination, and timezone assumptions
   must be visible when they eventually vary by workspace.

## Information architecture

Use one `/billing` feature with four URL-addressable tabs. Keeping the tabs in
one feature avoids a fragmented first release while still allowing direct links.

| Tab | Primary question | Content |
| --- | --- | --- |
| Overview | What is my current position? | Plan, billing-preview notice, minute gauge, cycle dates, forecast, alerts, and next best action |
| Usage | What consumed my allowance? | Daily trend, agent/channel breakdown, usage ledger, filters, and calculation explanation |
| Plans & add-ons | What would another setup cost? | Current plan, three-plan comparison, annual toggle, add-on modeller, and projected total |
| Invoices | What financial records exist? | Preview invoice examples during testing; real invoices, receipts, tax, and payment method only after activation |

Tab state should be reflected in the URL, for example `/billing?tab=usage`, so
alerts and support links can open the relevant view directly.

## Billing overview design

### Header

- Eyebrow: `Workspace billing`.
- Title: `Usage & billing`.
- Description: `Understand usage, forecast costs, and test plan controls before
  billing goes live.`
- Persistent amber/blue information banner: `Billing preview - no charges are
  being made and usage limits are not enforced.`
- Secondary action: `How billing works` opens a concise explanatory drawer.

### First-row cards

1. **Current plan**
   - Growth, $149/month, billed monthly.
   - 750 included AI minutes, 3 agents, 2 concurrent calls.
   - `Testing mode` status badge.
   - Primary action: `Compare plans`.

2. **Minutes this cycle**
   - Large `used / included` value and accessible progress bar.
   - Remaining minutes and reset date in the same reading order.
   - Forecast marker on the progress track.
   - `View usage breakdown` action.

3. **Projected month-end**
   - Base subscription, projected overage, active add-ons, and projected total.
   - Label every monetary value `Estimate`.
   - Show the formula `Plan + overage + activated add-ons`.
   - Explain excluded variable items, such as destination-specific carrier
     charges, until their rate tables are available.

### Spend-control simulator

This card explains future behaviour but does not control calls yet.

- Option A: continue with overage up to a monthly cap - recommended.
- Option B: pause new AI-handled calls at the allowance.
- Option C: notify an owner and require approval before more usage.
- Fallback preview: human transfer, voicemail, or temporary pause.
- Clear helper text: `Saved for testing only. This will not pause agents or
  change your bill.`

### Alerts timeline

Show the billing-cycle checkpoints as a calm status list:

- Cycle started.
- 80% warning - upcoming.
- 100% allowance - upcoming.
- Cycle resets on the stated date.

When usage crosses 100% in testing mode, replace any alarming failure message
with: `Allowance exceeded in preview. Calls are continuing; projected overage
is now being modelled.`

## Usage tab

### Summary

- AI minutes used.
- Included minutes remaining.
- Projected overage minutes.
- Number of calls and average duration for context.

### Breakdown

- Daily usage chart with included-allowance and forecast reference lines.
- Agent, channel, and direction filters.
- Table columns: date, agent, channel, direction, calls, duration, billable AI
  minutes, and status.
- Explain rounding and aggregation in a `How usage is calculated` disclosure.
- Empty, loading, partial-data, and reconciliation-pending states must be
  designed explicitly.

The customer view must not expose Telnyx IDs, provider tokens, internal cost,
or other tenants' usage.

## Plans and add-ons tab

- Reuse the public pricing catalog as the single source for names, allowances,
  agent limits, concurrency, overage rates, and annual discount.
- Highlight the current plan without visually forcing a higher tier.
- Let the user enter projected call volume or select a plan and add-ons.
- Show a sticky estimate summary on desktop and a bottom summary card on mobile.
- Break the estimate into base plan, minute overage, each add-on, and total.
- Mark regional calling and business numbers as `From` prices where rates vary.
- The final action is `Preview this setup`; it opens a read-only confirmation
  sheet and makes no API mutation.

Plan recommendations should compare the projected total across tiers. If a
higher tier is cheaper than the current plan plus overage, explain that with
plain arithmetic instead of a generic `Best value` badge.

## Invoices tab during testing

Do not fabricate real transaction history. Use one of these honest states:

- No preview activity: `Invoices will appear here after billing is activated.`
- Demonstration requested: clearly labeled `Sample invoice` rows generated from
  test scenarios, with no invoice number that resembles a real provider record.
- Provider sandbox later: `Test` badge on every sandbox invoice and receipt.

Payment-method controls, tax identifiers, downloadable receipts, and billing
address editing stay hidden until the payment processor is connected in sandbox
mode.

## Responsive and accessibility behaviour

- Desktop: three first-row cards, two-column detail grid, sticky estimate in
  plan modelling.
- Tablet: two-column cards with the forecast card spanning the row.
- Mobile: single-column reading order; tabs become horizontally scrollable with
  visible selected state; tables switch to labelled usage rows rather than
  shrinking below legible sizes.
- RTL: layout direction follows locale, but numbers, dates, currency, and phone
  values retain appropriate bidirectional isolation.
- Progress is communicated with text as well as colour.
- Warning, over-limit, and error states never rely on red/amber alone.
- Keyboard focus is visible; dialogs restore focus to their trigger; charts
  have equivalent text summaries; motion respects reduced-motion settings.

## State model

| State | Dashboard behaviour | Enforcement |
| --- | --- | --- |
| Preview, under 80% | Normal usage and forecast | None |
| Preview, 80-99% | Informational warning and projected overage | None |
| Preview, 100%+ | Calls continue; projected overage is shown | None |
| Shadow billing | Compare calculated usage with providers internally | None |
| Sandbox payments | Test checkout, webhook, and invoice badges | None on production calls |
| Live billing | Real charges and selected limit policy | Introduced only after explicit launch approval |

## Data and API plan

### Phase 1: dashboard preview

Use the existing tenant-scoped `GET /api/v1/billing/usage` response for real
minute usage. Add a frontend billing adapter that combines that response with
the shared pricing catalog and explicit preview metadata.

No write endpoints are called. Plan selection, add-ons, and spend controls live
in page state or clearly labeled local test preferences.

### Phase 2: read-only billing projection API

Introduce `GET /api/v1/billing/overview` with a stable read model:

- `mode`: `preview | shadow | sandbox | live`.
- current plan and billing interval.
- cycle start, cycle end, and timezone.
- included, used, remaining, forecast, and overage minutes.
- price breakdown with currency and `estimated` flag.
- alert thresholds and delivery state.
- configured future limit policy and fallback.
- data freshness and reconciliation status.

Add tenant-scoped read endpoints for usage breakdown and invoice metadata. API
responses expose provider-neutral records, never secrets or raw webhook data.

### Phase 3: shadow metering

- Record immutable, idempotent usage events in PostgreSQL.
- Keep Redis as the low-latency counter, not the only billing record.
- Meter duration in seconds and define one documented aggregation/rounding rule.
- Reconcile Sauti usage against Telnyx and other provider records.
- Model carrier voice, AI-engine minutes, messages, numbers, agents, and
  concurrency as separate charge dimensions to avoid double counting.
- Run at least two complete billing cycles in shadow mode before charging.

### Phase 4: sandbox payments

- Connect Lemon Squeezy or the approved merchant-of-record alternative in
  sandbox mode.
- Verify signed, replay-safe, idempotent webhooks.
- Add test checkout, test subscription changes, and test invoice retrieval.
- Keep production tenants in preview/shadow unless explicitly migrated.

### Phase 5: controlled activation

Activation requires a separate product decision and implementation request.
Before live billing:

- approve prices and country-specific provider rate tables;
- validate margins with shadow data;
- add owner/admin authorization for financial actions;
- add payment failures, grace periods, refunds, taxes, proration, and
  cancellation states;
- confirm alerts and fallback routing with real email delivery;
- provide audit logs and customer-visible calculation detail;
- enable enforcement per workspace with a kill switch and safe rollback.

## Frontend architecture

- `dashboard/features/billing/domain/`: pricing projection, usage thresholds,
  display state, and pure formatting helpers.
- `dashboard/features/billing/presentation/`: billing page, tabs, cards, usage
  chart/table, plan modeller, preview sheet, and CSS module.
- `dashboard/lib/api/billing.ts`: tenant-scoped billing reads; future writes stay
  explicit and are not introduced in preview mode.
- `dashboard/types/api.ts`: provider-neutral billing DTOs.
- `dashboard/app/(console)/billing/page.tsx`: thin feature wrapper.

The public pricing page and authenticated billing page must import one shared
catalog rather than duplicating plan values. Move the catalog into a neutral
domain module before implementing the dashboard.

## Backend architecture boundaries

- Keep `com.sauti.billing` inside the modular monolith.
- Controllers expose DTOs only; billing rules stay in the billing service.
- Every usage, subscription, invoice, and preference query is tenant-scoped.
- Webhooks are signature-verified, idempotent, durable, and safe to retry.
- Financial provider IDs are metadata; credentials remain encrypted or in
  server-only environment configuration.
- Do not use the tenant row's aggregate counter as the final billable ledger.

## Analytics to collect during preview

Track product events without financial side effects:

- billing page viewed;
- usage breakdown opened;
- 80%/100% message viewed;
- plan comparison opened;
- plan/add-on scenario modelled;
- preview confirmation completed;
- billing explanation opened;
- support/contact action selected.

Do not record card data, invoice content, provider secrets, or sensitive billing
addresses in analytics.

## Acceptance criteria for the preview release

- A workspace owner can understand plan, usage remaining, reset timing,
  forecast, overage rate, and add-ons without leaving `/billing`.
- Every screen and preview action states that no charge or enforcement occurs.
- Real usage is visually distinguished from estimated money.
- Crossing 80% and 100% produces the correct non-blocking preview state.
- Calls and agent activation are unchanged at and above 100%.
- The plan modeller uses the same catalog and arithmetic as public pricing.
- There are no real upgrade, checkout, payment, invoice, plan-mutation, or
  agent-pause calls.
- Loading, empty, error, over-limit, trial, and mobile states are covered.
- Keyboard, screen-reader, reduced-motion, 390 px mobile, and Arabic RTL checks
  pass.
- Tenant-isolation tests cover every billing read model.

## Recommended implementation sequence

1. Extract a shared pricing catalog and add unit tests for projections.
2. Build the preview-mode overview using the existing usage endpoint.
3. Add the usage breakdown with honest unavailable/reconciliation states where
   backend detail does not yet exist.
4. Add the plans/add-ons modeller and read-only confirmation sheet.
5. Add the invoice empty/sample states without payment controls.
6. Run authenticated desktop, mobile, keyboard, reduced-motion, and RTL QA.
7. Instrument preview interactions and collect tester feedback.
8. Only after pricing approval, begin shadow metering and sandbox payments.

## Explicitly out of scope now

- Real checkout or payment collection.
- Real subscription creation, upgrade, downgrade, cancellation, or proration.
- Real invoices or receipts.
- Charging minute overage or add-ons.
- Pausing agents, rejecting calls, or enforcing concurrency because of plan.
- Payment-failure suspension.
- Production billing webhooks.
