# Design QA: billing plans and Whop checkout

## Evidence

- Source visual truth: `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-c6a7a24c-d8e7-4c5b-9bd8-5cab2b9769e8.png`.
- Source pixels and viewport evidence: 1608 x 969 at desktop density.
- State: authenticated `Plans & add-ons` view with Whop Sandbox checkout ready.
- Implementation screenshot: unavailable. The Codex in-app browser runtime
  failed to initialize in this desktop session with `failed to write kernel
  assets: path not found`. No alternate browser automation was used.

## Full-view comparison evidence

- The supplied screen shows a P1 horizontal-overflow defect: the estimate
  summary reaches beyond the right viewport edge, clipping currency badges,
  ledger values, the calculated total, and action content.
- The supplied screen also shows P2 typography drift: helper descriptions and
  add-on explanations inherit bold weight, uppercase treatment, and expanded
  letter spacing intended for section labels.
- The existing Sauti navy/teal palette, three-step hierarchy, rounded cards,
  provider-status banner, plan controls, and checkout hierarchy remain the
  visual baseline. No image asset is present or required on this screen.

## Focused-region comparison evidence

- Estimate summary: the grid's desktop minimum tracks plus content-box card
  padding exceeded the available console width. Monetary values were also
  allowed to shrink inside flex rows.
- Configuration descriptions: `.planStudio small` inherited the shared
  uppercase label rule and a `750` font weight, producing the heavy appearance
  visible under minutes, agents, and add-ons.

## Comparison history

1. Initial evidence: right-hand content clipped and secondary copy displayed
   with excessive emphasis.
2. Fix applied: pricing cards now use border-box sizing and a zero intrinsic
   minimum; monetary values are non-shrinking and stay on one line. Billing
   descriptions, helper text, and footers now use normal weight, spacing, and
   casing. Headings, labels, prices, and the checkout action were reduced to
   moderate emphasis.
3. Post-fix evidence: TypeScript, zero-warning ESLint, and the optimized Next.js
   build pass. A browser-rendered screenshot comparison remains unavailable
   because the in-app browser runtime cannot initialize.

## Interaction checks

- The plan, interval, projected-minute, quantity, add-on, reset, and checkout
  controls remain unchanged and compile successfully.
- Checkout remains data-driven: Sandbox and live labels come from backend
  configuration, and an unavailable provider disables the primary action.
- Browser interaction and console-error inspection are blocked by the browser
  runtime issue above.

## Remaining finding

- [P2] Capture `/billing?tab=plans` at 1608 x 969 after deployment and verify
  that the complete estimate card, every amount, and both actions remain inside
  the viewport at 100% and 125% browser zoom.

final result: blocked
