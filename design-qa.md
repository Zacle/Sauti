# Design QA: workspace Settings operations console

## Comparison target

- Source visual truth: `C:\Users\Zacle\.codex\generated_images\019faf25-54b2-7311-b536-4c619135ab47\exec-07f5c0a2-fbc1-4cd0-80af-c52e2aa60076.png`.
- Source pixels: 1600 x 1000, desktop density.
- Target implementation: authenticated `/settings` route in the existing Sauti console.
- Intended state: General tab active, workspace and integration summaries loaded.
- Implementation screenshot: unavailable because this protected route requires an authenticated local workspace session and no user-selected authenticated browser surface was available for capture.
- Implementation pixels, CSS viewport, and density normalization: unavailable without a browser-rendered capture.

## Full-view comparison evidence

- The selected direction's horizontal category navigation, compact workspace-health strip, two-column General settings layout, calm navy surfaces, teal active states, and restrained typography are implemented in the existing Settings feature.
- The previous permanent secondary sidebar and oversized profile card were removed.
- General now contains a real persisted business-name field and modern timezone and booking-duration dropdowns. Existing working privacy-retention and webhook behavior remains available under dedicated tabs; Calls & AI, Notifications, billing, and provider settings link to their real management surfaces instead of presenting non-functional controls.

## Focused-region comparison evidence

- Source inspection established the intended compact tab treatment, divided information rows, moderate radii, and low-emphasis descriptions.
- Settings dropdowns now use accessible Radix popovers with visible selected, highlighted, open, and keyboard-focus states.
- Recording compliance uses a custom focus-visible checkbox with an explicit checked state while retaining a native checkbox input for semantics.
- Browser-rendered comparison of those focused states is blocked by the authenticated local route.

## Required fidelity surfaces

- Fonts and typography: implementation uses the console font stack with moderate headings, normal-weight descriptions, compact labels, and no oversized display heading; rendered wrapping remains unverified.
- Spacing and layout rhythm: the 1400px content frame, three-part health strip, horizontal tabs, divided two-column rows, and mobile stacking rules follow the selected reference; browser-rendered alignment remains unverified.
- Colors and visual tokens: existing Sauti midnight navy, cool borders, teal active states, and semantic red/green feedback are retained without introducing decorative gradients.
- Image quality and asset fidelity: the screen contains no raster imagery. Existing Lucide icons are used consistently with the repository and selected reference.
- Copy and content: every visible summary is derived from session or API data, reports unavailable optional status honestly, or links to the real owning feature. Editable General fields save through the tenant-scoped workspace-profile API and become defaults for new agents.

## Findings

- [P1] Authenticated browser-rendered comparison is unavailable.
  - Location: `/settings`, all tabs and interactive control states.
  - Evidence: the selected visual target is available, but an authenticated implementation screenshot at the matching viewport is not.
  - Impact: exact wrapping, density, dropdown placement, checkbox rendering, and responsive overflow cannot receive final visual sign-off.
  - Fix: open `/settings` in an authenticated browser after deployment or with a local test session, capture General and Data & privacy at 1600 x 1000 plus a 390px mobile view, then compare each capture with the selected target and correct any P0-P2 differences.

## Interaction and build checks

- Horizontal tabs use ARIA tab roles and update the displayed panel.
- General, Calls & AI, Notifications, billing, integration, password-reset, policy, terms, support, and deletion destinations remain real links.
- Privacy and webhook saves retain their existing API calls, error/success states, loading states, and validation boundaries.
- General saves business name, IANA timezone, and default booking duration through a validated tenant-scoped API and updates the authenticated tenant snapshot.
- Dashboard typecheck, zero-warning lint, and optimized production build pass; all 63 pages generated.

## Comparison history

1. Initial screenshot: a light shell mismatch around an oversized dark Settings card with a permanent secondary navigation and weak information hierarchy.
2. Selected target: horizontal category tabs, compact operational summary, structured rows, and restrained typography.
3. Implementation: rebuilt the page around that target, preserved only working controls, and added modern accessible dropdown/checkbox states.
4. Post-fix visual evidence: blocked pending an authenticated browser-rendered capture.

final result: blocked

---

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
4. The first deployed correction still overflowed because the three desktop
   grid tracks retained fixed 340px/500px/390px minimums. Those were replaced
   with zero-minimum proportional tracks, and a billing-content container query
   now moves the estimate to its own row at 821–1320px of actual workspace
   width, independent of the expanded or collapsed sidebar state.

## Interaction checks

- The plan, interval, projected-minute, quantity, add-on, reset, and checkout
  controls remain unchanged and compile successfully.
- Checkout remains data-driven: Sandbox and live labels come from backend
  configuration, and an unavailable provider disables the primary action.
- Browser interaction and console-error inspection are blocked by the browser
  runtime issue above.

## Remaining finding

- [P2] Capture `/billing?tab=plans` at 1608 x 969 after the revised deployment and verify
  that the complete estimate card, every amount, and both actions remain inside
  the viewport at 100% and 125% browser zoom.

final result: blocked
