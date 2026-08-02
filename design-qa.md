# Sauti Logo Design QA

- Source visual truth: `C:\Users\Zacle\.codex\visualizations\2026\07\28\019fab01-cdf5-74e3-84be-78463e67622f\sauti-logo\sauti-circular-logo-transparent.png`
- Installed asset: `D:\Documents\Sauti\dashboard\public\sauti-logo-circular.png`
- Implementation evidence: `C:\Users\Zacle\.codex\visualizations\2026\07\28\019fab01-cdf5-74e3-84be-78463e67622f\sauti-logo\sauti-circular-size-check.png`
- Source pixels: 1254 x 1254 RGBA
- Installed pixels: 1024 x 1024 RGBA
- Checked UI sizes: 16, 24, 32, 48, 64, and 128 px
- State: transparent circular logo on a light neutral surface

## Full-view comparison evidence

The installed asset preserves the selected circular navy/cobalt background, white conversation-loop symbol, five-bar waveform, and transparent area outside the circle. The source was downsampled with Lanczos resampling without changing its square aspect ratio.

## Focused region comparison evidence

Direct raster checks at favicon and sidebar sizes show the circle, conversation loop, and central waveform remain distinguishable. No text, chroma-key background, or colored extraction spill is visible in the installed asset.

## Required fidelity surfaces

- Fonts and typography: not applicable; the selected logo contains no text.
- Spacing and layout rhythm: the circular mark has transparent perimeter spacing and is rendered with `object-fit: contain`.
- Colors and visual tokens: the installed asset retains the selected navy/cobalt circle and white/cyan symbol.
- Image quality and asset fidelity: the installed PNG has an alpha channel, a clean circular edge, and no visible background-key artifacts in the direct size check.
- Copy and content: no written content remains.

## Comparison history

- The earlier 3D S-mark direction was superseded by the selected circular conversation-loop design.
- The old CSS zoom and white background treatment were removed so the new circle is not cropped or boxed.
- The shared component and favicon metadata now use the cache-safe `/sauti-logo-circular.png` path, and the legacy square gradient backing is explicitly disabled.

## Blocker

The in-app browser connection failed before the local dashboard could be captured, so browser-rendered placement and console checks could not be completed in this session.

final result: blocked

---

# Agent Studio Option 2 Design QA

- Source visual truth: `C:\Users\Zacle\.codex\generated_images\019fab01-cdf5-74e3-84be-78463e67622f\call_ydi4FUUF50WIhsWW1oPGRv3f.png`
- Implementation evidence: `D:\Documents\Sauti\design-qa\agent-studio-option-2-final-1607x934.png`
- Combined comparison: `D:\Documents\Sauti\design-qa\agent-studio-option-2-comparison.png`
- Viewport: 1607 x 934 CSS pixels for both source and implementation
- State: new Appointment Booker agent, Main settings selected, unsaved test call idle

## Full-view comparison evidence

The implementation follows the selected fixed command-workspace composition: a slim global icon rail, a persistent setup/progress rail, one independently scrollable settings surface, and a fixed test-call panel. The implementation retains the existing Sauti settings controls and real draft state instead of replacing them with mock content.

## Focused region comparison evidence

- The right call panel uses the generated concentric AI-ring asset with the circular Sauti mark at its center.
- Listening, Thinking, and Speaking states are represented beside the orb and the same orb remains visible during active calls.
- The left setup rail derives progress and configured indicators from the current form values.
- At 1607 x 934, the document body remains fixed at the viewport height while only `.agent-studio-form` scrolls.
- At 1024 x 768, the outer document remains fixed, the center pane remains the only scroll owner, and the test-call rail collapses to preserve working space.

## Interaction and accessibility checks

- Setup navigation was exercised between Main settings and Behavior & prompt; both content and active state updated correctly.
- Test-call controls continue to use the existing Telnyx state and validation logic. The unsaved template correctly shows the disabled “Save agent to test” state.
- Reduced-motion styles pause the decorative ring animation.
- Browser console contained no errors. The above-the-fold ring image LCP warning was corrected by marking the image as high priority.

## Comparison history

- Iteration 1 exposed an outer document scrollbar beneath the fixed workspace.
- The root document overflow was constrained and the internal settings pane retained `overflow-y: auto`.
- Iteration 2 passed the full-view comparison and scroll-ownership checks.
- Follow-up regression check found that the route-level fixed shell also constrained the pre-editor template chooser.
- The overflow lock now activates only when the configured studio is mounted; the template chooser has a verified independent 294 px scroll range at 1280 x 720, while the configured editor retains center-only scrolling.
- The global Agents navigation is now expanded at 252 px by default and collapses to 76 px only through the visible user control. Both states were browser-verified, including persistence after reload.
- The collapse control now belongs to the shared authenticated shell rather than Agents alone; the preference was verified across Dashboard and Calls while retaining the same expanded and compact dimensions.

## Intentional differences

- The implementation shows the actual unsaved-agent and unselected-voice state instead of the source mock’s ready-to-call state.
- Existing production settings are kept intact, so the central form contains richer controls than the visual concept.
- A live provider call was not placed during local QA; the active Listening, Thinking, and Speaking render paths are implemented but still require provider-backed verification.

final result: passed

---

# Billing Preview Dashboard Design QA

- Source visual truth:
  - `D:\Documents\Sauti\design-qa\billing-source-overview.png`
  - `D:\Documents\Sauti\design-qa\billing-source-usage.png`
  - `D:\Documents\Sauti\design-qa\billing-source-plans.png`
- Rendered implementation:
  - `D:\Documents\Sauti\design-qa\billing-implementation-overview.png`
  - `D:\Documents\Sauti\design-qa\billing-implementation-usage.png`
  - `D:\Documents\Sauti\design-qa\billing-implementation-plans.png`
  - `D:\Documents\Sauti\design-qa\billing-implementation-invoices.png`
  - `D:\Documents\Sauti\design-qa\billing-implementation-preview-dialog.png`
  - `D:\Documents\Sauti\design-qa\billing-implementation-mobile.png`
- Combined same-state comparisons:
  - `D:\Documents\Sauti\design-qa\billing-comparison-overview.png`
  - `D:\Documents\Sauti\design-qa\billing-comparison-usage.png`
  - `D:\Documents\Sauti\design-qa\billing-comparison-plans.png`
- Desktop viewport: 1440 x 1024 CSS pixels.
- Mobile viewport: 390 x 844 CSS pixels.
- State: Growth plan, 486 of 750 aggregate AI minutes used, 690-minute
  conservative forecast, and billing preview enabled.

## Findings and comparison history

- [P2 fixed] The initial forecast scale produced a floating-point Y-axis label
  and did not match the selected 690-minute forecast state.
  - Fix: rounded the chart ceiling to 50-minute increments and used the
    conservative 1.42x preview forecast, capped to prevent runaway estimates.
- [P2 fixed] The first QA mock used field names that did not match the existing
  `BillingUsage` contract and triggered a client error.
  - Fix: corrected the test payload to the real DTO shape and repeated browser
    QA in a fresh tab; the final warning/error console is empty.
- Intentional fidelity difference: the implementation does not reproduce the
  source's invented per-agent usage rows or pretend that add-ons are active.
  It shows the live aggregate and an explicit shadow-metering unavailable state
  until the durable tenant ledger can support truthful detail.
- Intentional fidelity difference: cycle dates are derived from the current
  preview month rather than frozen to the dates in the generated source.

## Required fidelity surfaces

- Layout and hierarchy: the dark console shell, compact tab strip, persistent
  preview notice, three-part usage hero, chart-plus-ledger usage view, and
  three-column plan modeller preserve the selected visual direction.
- Typography and spacing: headings, labels, dense card spacing, dividers, and
  responsive stacking use the established Sauti console scale and tokens.
- Color and assets: navy surfaces, cyan state accents, amber thresholds, and
  Lucide icons stay consistent with the existing dashboard without handmade
  SVG or placeholder assets.
- Content truthfulness: live aggregate usage, modelled forecasts, preview
  prices, unavailable detailed metering, and nonexistent invoices are visibly
  distinguished.
- Responsive behavior: at 390 x 844 there is no page-level horizontal
  overflow; the tab strip scrolls intentionally, cards stack, and the dialog
  remains fully inside the viewport.

## Interaction and regression checks

- Overview, Usage, Plans & add-ons, and Invoices tabs work and persist their
  state in the URL.
- Monthly/annual pricing, projected minutes, extra-agent quantity, optional
  add-ons, reset, and transparent total arithmetic update correctly.
- The read-only preview dialog opens, receives focus, fits mobile, and closes.
- The future limit-policy choices are explicitly test-only and make no API
  mutation; calls continue at 100% during preview.
- An 810-of-750-minute edge state showed zero remaining, marked the 100%
  threshold reached, and explicitly confirmed that calls continue in preview;
  the normal 486-of-750 state was restored afterward.
- The invoice tab contains no fabricated invoice IDs, payment methods, charges,
  or receipts.
- Final fresh-tab console inspection returned no warnings or errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.
- No remaining actionable P0/P1/P2 findings.

final result: passed

---

# Billing Supplied-Reference Follow-up QA

- Source visual truth:
  - `D:\Documents\Sauti\design-qa\billing-reference-overview-v2.png`
    (1448 x 1086, billing content canvas)
  - `D:\Documents\Sauti\design-qa\billing-reference-plans-v2.png`
    (1619 x 971, full page with the Sauti header)
- Rendered overview evidence:
  - `D:\Documents\Sauti\design-qa\billing-v2-overview-1448x1086.png`
  - `D:\Documents\Sauti\design-qa\billing-v2-comparison-overview.png`
- Desktop states checked: Growth plan, 486 of 750 minutes used, 600-minute
  forecast; annual Growth plan, 900 projected minutes, and two additional
  agents producing the reference $217.60 estimate.
- Mobile viewport checked: 390 x 844 CSS pixels.
- Overview normalization: the overview source begins at the billing content
  canvas, while the implemented product retains the 60 px Sauti return header.
  The combined comparison therefore aligns the implementation below that
  header with the source at the same 1448 px width.

## Findings and comparison history

- [P2 fixed] The first implementation capture forecasted 690 minutes, placing
  the marker too far right of the supplied 600-minute state.
  - Fix: recalibrated the conservative preview multiplier so 486 used forecasts
    600 minutes while retaining the existing allowance cap.
- [P2 fixed] The initial usage hero underweighted the current-plan column and
  left too much width in the remainder column.
  - Fix: rebalanced the desktop grid to the supplied plan / usage / remaining
    proportions.
- [P2 fixed] The cost and alert cards were visibly taller than the reference.
  - Fix: tightened card padding, ledger rows, total row, and alert-row height.
- [P2 fixed] The current-plan and selected-policy icons did not match the
  reference semantics.
  - Fix: used the established Lucide bar-chart and check icons.

## Required fidelity surfaces

- Layout and hierarchy: the focused full-width billing canvas, slim branded
  header, tab treatment, preview banner, hero, lower grid, and modeller match
  the supplied desktop compositions.
- Typography and spacing: large page and usage numerals, compact uppercase
  labels, sharp card borders, and dense financial rows follow the references.
- Colors and assets: the implementation stays within Sauti's deep navy and cyan
  system and uses the existing brand asset and Lucide icon set.
- Copy and content: the modelled forecast, dates, cost arithmetic, testing
  notice, and non-enforcement language remain explicit and truthful.
- Responsive behavior: at 390 x 844, both Overview and Plans & add-ons render
  all sections with no page-level horizontal overflow.

## Interaction and regression checks

- All four billing tabs remain available and the overview/plans controls switch
  state correctly.
- Annual selection and additional-agent quantity produce $134.10 base,
  $25.50 overage, $58 add-ons, and $217.60 estimated total.
- The supplied 486-of-750 state exposes 264 remaining and a 600-minute forecast.
- Billing remains preview-only; no control mutates the workspace or pauses calls.
- Browser DOM and responsive checks found no missing core sections or overflow.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.
- No actionable P0/P1/P2 findings remain.

final result: passed

---

# Pricing Add-ons and Scalable Tier Follow-up QA

- Implemented surface: `dashboard/features/marketing/Pricing/presentation/MarketingPricingPage.tsx`
- Desktop evidence: `D:\Documents\Sauti\design-qa\pricing-addons-final-desktop.png`
- Mobile evidence: `D:\Documents\Sauti\design-qa\pricing-addons-final-mobile.png`
- Viewports: 1440 x 1024 and 390 x 844 CSS pixels.

## Intentional design extension

- Preserved the selected pricing direction's navy calculator, editorial hierarchy,
  rounded controls, comparison table, and restrained console tokens.
- Extended the experience with an explicit monthly-bill formula and six optional
  add-on cards. This is an intentional content addition to answer buyer anxiety
  about variable telephony and messaging costs without changing the visual system.
- Lowered the entry plan to $49/month and made annual savings 10%, while retaining
  enough tier and overage separation to support infrastructure growth.

## Interaction and regression checks

- Default workload (50 calls/week at 3 minutes) recommends Growth at $149/month
  with 650 estimated minutes and 100 minutes of headroom.
- High workload (200 calls/week at 10 minutes) recommends Scale and correctly
  includes 6,167 overage minutes in the $1,262 monthly estimate.
- Annual billing displays $44, $134, and $359 monthly equivalents.
- All six add-ons and the `Plan + overage + activated add-ons` formula render on
  desktop and mobile.
- Desktop and 390 px mobile layouts have no horizontal overflow.
- Browser console inspection returned no warnings or errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.
- No actionable P0/P1/P2 findings remain.

final result: passed

---

# Pricing Guide Option 3 Design QA

- Selected visual source: `C:\Users\Zacle\.codex\generated_images\019fbc15-294f-7ad2-b6f6-0ec7de9f162e\exec-d7fdef00-552d-4b3d-846a-c223a1ee9899.png` (1024 x 1536).
- Rendered implementation evidence:
  - `D:\Documents\Sauti\design-qa\pricing-option3-final-top.png` (1009 x 979)
  - `D:\Documents\Sauti\design-qa\pricing-option3-final-table.png` (1009 x 979)
  - `D:\Documents\Sauti\design-qa\pricing-option3-final-mobile.png` (375 x 810 rendered content within a 390 x 844 CSS viewport)
- Combined source/implementation evidence:
  - `D:\Documents\Sauti\design-qa\pricing-option3-comparison-top.png`
  - `D:\Documents\Sauti\design-qa\pricing-option3-comparison-table.png`
- Comparison state: 50 calls/week, 3-minute average, Book appointments, monthly billing, Growth recommendation.
- Density normalization: the 1024-pixel-wide source was cropped into top and lower regions at the implementation screenshot aspect ratio, then resized with high-quality interpolation to 1009 x 979. The implementation was not rescaled. The lower implementation capture is aligned near 502 CSS pixels of page scroll; the sticky marketing header remains intentionally visible.

## Findings and correction history

- [P1 fixed] The initial Remotion Player root participated in layout and pushed recommendation content toward the bottom of the card.
  - Fix: placed the Player inside an explicit absolute motion layer, keeping animation decorative while normal HTML owns the content layout.
- [P2 fixed] Unselected outcome circles used the accent fill and appeared active.
  - Fix: made inactive circles transparent and reserved the cyan fill/check for the selected outcome.
- [P2 fixed] The mobile hero removed a line-break node without preserving whitespace, producing `againstthe`.
  - Fix: preserved explicit whitespace before the responsive break and reverified the final heading text in the browser.
- Intentional product difference: the selected concept's 2/5/15 concurrent-call limits became 1/2/5. The lower limits are a deliberate margin and capacity safeguard until production concurrency cost is validated.
- Intentional product difference: free-form Custom inputs from the visual concept were omitted from this first public calculator. Realistic presets keep the recommendation deterministic and avoid implying arbitrary-volume underwriting before backend billing supports it.

## Required fidelity surfaces

- Fonts and typography: the implementation uses Sauti's existing marketing type system and preserves the source's large outcome-led headline, compact labels, restrained metadata, and clear recommendation hierarchy. No truncation or broken wrapping remains at desktop or mobile widths.
- Spacing and layout: the selected two-column estimator/recommendation composition, nested input panels, highlighted recommendation border, comparison matrix, protection strip, and support CTA are reproduced with consistent radii and vertical rhythm.
- Colors and tokens: deep navy surfaces, cyan recommendation states, muted blue-gray borders, and violet support accents match the selected direction while using the existing Sauti token language. Active and inactive states remain distinguishable with adequate contrast.
- Image quality and assets: the design does not call for raster imagery. The existing Sauti brand mark and Lucide icon family are used consistently; no placeholder asset, handcrafted SVG, emoji, or CSS illustration substitutes the source.
- Copy and content: copy explains the calculator assumptions, variable pass-through costs, no-card browser trial, paid live-calling boundary, usage alerts, and spend controls without promising guaranteed business return.
- States and interactions: all call-volume, duration, and outcome options work; recommendations and economics update; monthly/annual pricing switches correctly; the comparison anchor scrolls to the plan table; reduced-motion users receive a static recommendation state.
- Accessibility: controls use semantic buttons with visible selected states, keyboard focus styling, practical mobile tap targets, readable contrast, and reduced-motion handling.
- Viewport resilience: 1440 x 1024, 1024 x 1536, and 390 x 844 checks found no horizontal overflow, clipping, overlap, or unusable controls.
- Browser integrity: the final console check returned no warnings or errors.

No actionable P0/P1/P2 findings remain.

final result: passed

---

# Resources Console Option 2 Design QA

- Source visual truth: `C:\Users\Zacle\.codex\generated_images\019fbc15-294f-7ad2-b6f6-0ec7de9f162e\exec-17f4f212-1d22-4065-b71d-920a4cf47435.png` (1487 x 1058)
- Rendered implementation: `D:\Documents\Sauti\design-qa\resources-console-final-1440x1024.png` (1440 x 1008 screenshot pixels from a 1440 x 1024 CSS viewport)
- Combined comparison: `D:\Documents\Sauti\design-qa\resources-console-comparison.png`
- Density normalization: the source was resized with Lanczos sampling to the implementation capture size for the side-by-side comparison; no implementation pixels were rescaled.
- State: Resources overview, Business owner audience, default booking-calendar query.

## Findings and comparison history

- [P1 fixed] The first desktop pass wrapped the hero title and pushed the console below the selected composition.
  - Fix: tightened the desktop display scale, hero padding, status spacing, and console offset while preserving responsive wrapping below the desktop breakpoint.
  - Evidence: the final 1440 px capture keeps the full title on one line and exposes the complete three-column console above the fold.
- [P2 fixed] The featured-guide title initially wrapped despite the selected concept using one line.
  - Fix: added a desktop-only guide-title scale and no-wrap rule; mobile and tablet continue to wrap naturally.
- No actionable P0/P1/P2 findings remain after the second source-to-implementation comparison.

## Required fidelity surfaces

- Fonts and typography: the existing Sauti font stack is retained; the hero, featured guide, rails, labels, and compact metadata reproduce the selected hierarchy without truncation or cramped controls.
- Spacing and layout: the final overview preserves the selected search-first hierarchy, six-item browse rail, central launch guide, and trust rail. Card radii, borders, internal gaps, and section rhythm match the dark console treatment.
- Colors and tokens: navy surfaces, cyan primary actions, violet builder accents, mint trust states, muted body copy, and restrained borders map to the selected visual direction with accessible contrast.
- Image and icon quality: the selected design contains no required photographic or raster hero asset. The existing Sauti logo and one consistent Lucide icon family are used; no CSS illustration or placeholder imagery substitutes for a source asset.
- Copy and content: navigation and guidance are grounded in the project SRS, including multilingual agents, RAG-backed answers, bookings, analytics, tenant isolation, encrypted credentials, signed callbacks, and consent controls. Case-study content is explicitly framed as implementation patterns rather than invented customer claims.
- Interactions and accessibility: search submission, query clearing, audience selection, route navigation, native FAQ expansion, keyboard-reachable controls, semantic labels, visible focus behavior, live status feedback, and reduced-motion handling were checked.
- Responsive resilience: desktop (1440 px) and mobile (390 px) checks found no horizontal overflow, clipped controls, or broken hierarchy. The browse rail, launch sequence, journey cards, and trust content collapse into usable single-column arrangements.

## Functional and route checks

- Search was exercised with the Builder audience and a custom tenant-isolation query; the result status updated correctly.
- The Documentation, API Reference, Blog, Case Studies, FAQs, and Security routes all rendered their tailored content with a main landmark and no horizontal overflow.
- FAQ disclosure controls expanded correctly.
- A fresh production-preview navigation emitted no browser warnings or errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed; the build generated all six static resource detail routes.

final result: passed

---

# Agent Studio Call Visual Correction QA

- Source visual truth:
  - `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-408d2128-77fb-43fc-96e0-54da0146d3c0.png` (566 x 496)
  - `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-83e07ef1-8b6e-4038-a704-f7b2a07f9ff9.png` (546 x 683)
- Full implementation evidence: `D:\Documents\Sauti\design-qa\agent-studio-corrected-1280x720.png`
- Focused implementation evidence: `D:\Documents\Sauti\design-qa\pre-call-panel-corrected-308x572.png`
- Combined comparison: `D:\Documents\Sauti\design-qa\pre-call-orb-comparison.png`
- Viewport: 1280 x 720 CSS pixels at device scale 1
- State: new Appointment Booker agent, pre-call idle state, no selected Telnyx voice

## Findings and comparison history

- [P1 fixed] The shared console top bar was hidden on `/agents/new` and `/agents/[id]`.
  - Fix: restored the 76 px shared top bar and made the fixed Agent Studio consume the remaining viewport track.
  - Evidence: the full implementation capture shows search, notifications, Test agent, and profile controls above the editor; a saved-agent route check also returned a visible 76 px top bar.
- [P1 fixed] The previous call visual occupied too much space, used three unused state colors, and remained visible during an active call.
  - Fix: replaced it with one 106 px pre-call orb based on the supplied sphere/glow references, removed the Listening/Thinking/Speaking legend, and removed the active-call orb stage entirely.
  - Evidence: the focused comparison shows the restrained orb between the pre-call explanation and voice control. Browser DOM checks found no state legend or live visual stage.
- [P2 fixed] The Agents index contained an unrelated animated rings illustration.
  - Fix: removed the visual and its animation styles; the Agents hero now prioritizes the title and Create agent action.
- [P2 fixed] The pre-call voice summary exposed a provider voice identifier.
  - Fix: replaced technical identifier copy with “Voice ready” or “Choose a Telnyx voice.” A saved-agent route check found no visible technical agent identifier.

## Required fidelity surfaces

- Fonts and typography: existing Sauti console typography and hierarchy are preserved; the visual correction does not introduce image text.
- Spacing and layout rhythm: the orb is 106 px at the tested viewport, the test panel is 308 px visible width, and the restored top bar reserves 76 px without creating body overflow.
- Colors and visual tokens: the new asset follows the supplied pale white, periwinkle, blue, and restrained lilac palette without semantic state colors.
- Image quality and asset fidelity: `pre-call-orb.png` is a 1254 x 1254 RGBA asset with transparent surroundings and no rings, logo, text, or key-color hole.
- Copy and content: the three-state legend and technical identifiers are absent; call preparation and privacy copy remain.

## Interaction and regression checks

- The Agents index no longer renders the rings asset or its visual container.
- `/agents/new` and `/agents/[id]` both render the shared top bar.
- Main Settings remains the editor scroll owner and the document remains fixed to the viewport.
- The active call still exposes textual status and transcript feedback, but no decorative orb is mounted during the call.
- A provider-backed active call was not placed during local QA.
- Browser console check on the final corrected preview returned no warnings or errors.
- Follow-up motion correction: the orb wrapper remains fixed at 106 x 106 px while two real-image cloud layers drift internally at different durations. Browser sampling 1.4 seconds apart confirmed identical wrapper coordinates and changing layer transforms/opacity; no outer translation or bounce remains.
- Follow-up speed correction: reduced the layer cycles to 2.6 and 3.2 seconds, expanded opposing travel to roughly 18–22% across the texture, and strengthened blur, brightness, hue, saturation, and opacity changes. Sampling only 500 ms apart showed materially different internal transforms while the wrapper coordinates remained identical.
- Follow-up direction correction: replaced the two-endpoint ping-pong with independent closed multi-stop routes. Four browser samples 520 ms apart showed the two textures moving through different X/Y quadrants while the 106 x 106 px circular wrapper remained fixed.

final result: passed

---

# Authentication Experience Design QA

- Selected visual truth: `C:\Users\Zacle\.codex\generated_images\019fab01-cdf5-74e3-84be-78463e67622f\call_OlpXUx8btmIhczLc1uy0DLRu.png`
- Implemented surface: `dashboard/features/auth/AuthForm/AuthForm.tsx`
- Styling: `dashboard/features/auth/AuthForm/AuthForm.css`
- States covered: register, login, email verification, Google availability, missing workspace name, submitting, and OAuth redirecting
- Responsive targets: desktop split view, tablet/small-laptop single-column form, and stacked phone fields below 560 px

## Fidelity notes

- Recreated the selected navy split-screen composition with a focused registration panel and a real Sauti waveform image.
- Preserved the selected headline, compact benefit rows, bright Google action, blue primary action, restrained borders, and dense professional spacing.
- Adapted the concept to the existing Sauti component and token system rather than replacing the application shell.
- On narrow screens the supporting visual panel is removed and the form becomes the primary content, avoiding horizontal overflow and keeping controls at usable touch sizes.

## Functional checks

- Workspace name and country now precede Google registration because both values are required to create the workspace.
- Google configuration and validation feedback render immediately beneath the Google control.
- Missing workspace names receive focus and a specific error.
- The OAuth transition uses `window.location.assign` and sends new registrations through `/onboarding`.
- Type checking and linting passed.
- The production build remained CPU-bound without producing further output and was stopped after a bounded wait; no build error was emitted before termination.

## Blocker

The in-app browser connection was unavailable, so a same-viewport implementation screenshot, combined visual comparison, and browser console check could not be completed.

final result: blocked

---

# Latest QA Status

The most recent build gate is **Agent Studio Call Visual Correction QA** above, using:

- source references: `codex-clipboard-408d2128-77fb-43fc-96e0-54da0146d3c0.png` and `codex-clipboard-83e07ef1-8b6e-4038-a704-f7b2a07f9ff9.png`;
- implementation: `D:\Documents\Sauti\design-qa\agent-studio-corrected-1280x720.png`;
- focused side-by-side evidence: `D:\Documents\Sauti\design-qa\pre-call-orb-comparison.png`;
- viewport: 1280 x 720 CSS pixels at device scale 1;
- state: Appointment Booker pre-call idle state.

The full findings, required fidelity surfaces, interaction checks, and P1/P2 correction history are recorded in that section. No actionable P0/P1/P2 findings remain.

final result: passed

---

# Agent Studio Clarity Follow-up QA

- Source visual truth:
  - `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-8fe16dfc-2af0-41da-bf14-bcd7a08b9153.png` (364 x 743)
  - `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-ac6be074-f54e-4e4c-831b-7bb9b3baf2c8.png` (438 x 94)
- Rendered implementation:
  - `D:\Documents\Sauti\design-qa\agent-studio-simplified-1280x720.png` (1280 x 720)
  - `D:\Documents\Sauti\design-qa\setup-readiness-simplified.png` (250 x 572)
  - `D:\Documents\Sauti\design-qa\pre-call-panel-no-voice-card.png` (308 x 572)
- Viewport: 1280 x 720 CSS pixels at device scale 1.
- State: new Appointment Booker agent, pre-call idle state, no selected Telnyx voice.
- Density normalization: source and implementation were inspected at native pixel density; focused crops were used because the source screenshots cover individual interface regions.

## Findings and comparison history

- [P2 fixed] `4 of 6 milestones complete` did not explain what was being counted and appeared inconsistent with the eight visible editor sections.
  - Fix: changed the card to `Launch readiness`, retained the useful percentage, and replaced the count with direct next-step guidance.
  - Post-fix evidence: the focused sidebar capture shows `Complete the remaining setup requirements` with no milestone count.
- [P2 fixed] The selected-voice card repeated state without helping the user act.
  - Fix: removed the card and its styles while keeping the missing-voice instruction when configuration is required.
  - Post-fix evidence: the focused pre-call capture moves directly from the orb to the actionable instruction and call button.

## Required fidelity surfaces

- Fonts and typography: existing Sauti type scale, weights, and antialiasing are unchanged; new copy fits without truncation.
- Spacing and layout rhythm: removing the voice card reduces unnecessary vertical density while preserving the orb, instruction, call action, and privacy note.
- Colors and visual tokens: the readiness card and call panel continue using the established console tokens.
- Image quality and asset fidelity: the existing generated pre-call orb remains unchanged and sharp within its fixed circular crop.
- Copy and content: unexplained milestone language and redundant voice-ready content are absent; remaining copy is task-oriented.

## Interaction and regression checks

- The setup percentage and progress fill continue to reflect the existing readiness calculation.
- The call action remains disabled without a saved compatible voice and is explained by the retained instruction.
- The pre-call orb continues to animate and is not shown during an active call.
- The shared navigation, top bar, and Main Settings scroll ownership remain unchanged.
- Focused source/implementation comparisons found no remaining actionable P0/P1/P2 mismatch.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.

final result: passed
