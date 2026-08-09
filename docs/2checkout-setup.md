# 2Checkout / 2Monetize activation (dormant adapter)

> Sauti now uses Whop for Phase 3. This document is retained only as rollback
> history. Do not configure or select 2Checkout for the current production
> launch; follow `docs/whop-setup.md` instead.

Sauti uses 2Checkout through a provider-neutral billing gateway. The active adapter is selected with
`SAUTI_BILLING_PROVIDER`; changing processors later does not change the dashboard checkout API or the
tenant billing model.

## Before account approval

Prepare six recurring catalog products in the 2Checkout Merchant Control Panel:

- Launch monthly and annual
- Growth monthly and annual
- Scale monthly and annual

Keep prices, billing intervals, renewal behavior, trial rules, refund wording, and the public Sauti pricing
page consistent. Copy each product code and its generated HTTPS buy link into the matching
`TWO_CHECKOUT_PRODUCT_*` and `TWO_CHECKOUT_BUY_LINK_*` variables. Sauti deliberately consumes generated
buy links instead of constructing undocumented cart URLs.

Set `TWO_CHECKOUT_SECRET_KEY` to the Secret Key from **Dashboard → Integrations → Webhooks and API**.
The same key validates SHA-256 LCN signatures and protects Sauti's external customer references. Changing
this key after live subscriptions exist requires a controlled rotation because older customer references
will no longer validate.

## LCN endpoint

Add this public LCN URL in the Merchant Control Panel:

`https://sauti.uk/webhooks/2checkout/lcn`

Select SHA-256 and enable subscription lifecycle triggers. Include at least these response parameters:

- `LICENSE_CODE`
- `EXPIRATION_DATE` and, when offered, `EXPIRATION_DATE_TIME`
- `DATE_UPDATED`
- `EXTERNAL_CUSTOMER_REFERENCE`
- `AVANGATE_CUSTOMER_REFERENCE`
- `STATUS`
- `LICENSE_PRODUCT_CODE`
- `TEST`
- `NEXT_RENEWAL_DATE`
- `ORIGINAL_ORDER_REFERENCE` and `LAST_ORDER_REFERENCE`
- `NEXT_RENEWAL_CARD_TYPE` and `NEXT_RENEWAL_CARD_LAST_DIGITS`
- `DISPATCH_REASON`
- `SIGNATURE_SHA2_256`

The endpoint verifies the signature over fields in their received order, stores each notification once,
and returns the signed read receipt required by 2Checkout. Subscription updates are processed from a
durable provider-scoped inbox. Billing enforcement remains in `observe` mode until live purchase,
renewal, cancellation, expiration, refund, and chargeback journeys have been reconciled.

## Safe activation checklist

1. Keep `SAUTI_BILLING_PROVIDER=2checkout` and populate all product variables in a non-production environment.
2. Use 2Checkout test mode to purchase each monthly and annual product.
3. Confirm the customer returns to Sauti and the workspace plan changes only after a signed LCN.
4. Replay an LCN and confirm it is idempotent; alter a signature and confirm it is rejected.
5. Test renewal, cancellation, past-due, expiration, refund, and chargeback behavior.
6. Add failing-webhook alerts in 2Checkout and monitor Sauti's durable event failures.
7. Only after reconciliation is reliable should a reviewed change move billing enforcement from observe mode.

To fall back temporarily, set `SAUTI_BILLING_PROVIDER=lemon_squeezy` and configure the existing Lemon
Squeezy variables. No dashboard code change is required.
