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

# Circular AI Voice Animation QA — 2026-08-03

- Source visual truth: the user-supplied voice-animation screenshots and
  `C:\Users\Zacle\Downloads\screen-capture.webm`, used only as visual direction.
- Implementation: `http://127.0.0.1:8088/agents/new`.
- Intended viewport: desktop Agent Studio, 1280 x 720 CSS pixels at device scale 1.
- Source pixels: supplied references vary; no source pixels are included in the implementation.
- Implementation pixels: browser screenshot unavailable.
- State: blank Amina agent, idle voice-test panel.

## Full-view comparison evidence

Blocked. The in-app browser rendered and exposed the component DOM, but every
full-page screenshot request timed out or closed the tab, including a fresh
production-build tab before the animation was mounted.

## Focused-region evidence

Browser inspection confirmed a 1:1 animation wrapper, a square high-density
canvas, and zero image, video, or SVG descendants. The canvas uses equal radial
geometry around one center and changes shape over time, but a focused pixel
capture could not be obtained from the browser.

The pre-call `calm` state was sampled independently from call activity. Its
motion marker advanced eight steps over 2.2 seconds at approximately 30 FPS,
confirming continuous idle animation before a call begins. The calm perimeter
cycle was shortened from roughly 18 seconds to roughly four seconds so the
movement is visibly readable rather than technically active but imperceptible.

## Findings

- [P2] Final visual polish cannot be certified without rendered image evidence.
  - Evidence: DOM geometry and runtime checks pass, but browser capture fails.
  - Fix: inspect the live preview or repeat screenshot comparison when the
    in-app browser capture surface is available.

## Required fidelity surfaces

- Fonts and typography: unchanged from the previously verified Agent Studio.
- Spacing and layout rhythm: wrapper is square and responsive; surrounding panel layout is unchanged.
- Colors and visual tokens: teal strands and cobalt panel field retain Sauti's established palette.
- Image quality and asset fidelity: no reference image/video is present; final raster quality is not screenshot-certified.
- Copy and content: unchanged.

## Comparison history

- Earlier implementation was visibly oval because it used a 220 x 172 view box
  and unequal X/Y radii. The new implementation uses a square canvas and one
  radius for both axes.
- The copied-media implementation and all extracted reference assets were
  removed before this pass.

final result: blocked

---

# Original Test-call AI Ribbon QA (supersedes copied-media implementation)

- Design reference only: `C:\Users\Zacle\Downloads\screen-capture.webm` and the two supplied test-panel screenshots.
- Browser-rendered implementation: `D:\Documents\Sauti\design-qa\test-call-original-ai-ribbon-1600x900.png`.
- Viewport: 1600 x 900 CSS pixels at device scale 1.
- State: blank Amina agent, idle voice-test panel.

## Correction and visual comparison

The recording is now treated only as visual direction. The implementation contains no pixels, crop, cursor, browser chrome, or corner from the reference. It draws five original teal ribbon paths over the existing cobalt Sauti field. The animation wrapper is transparent and overflow-visible, so the artwork is not enclosed in a circular image container.

All earlier copied recordings, extracted frames, reference crops, comparisons,
and product captures that rendered those assets were removed from `design-qa`.
Only the independently drawn implementation capture remains as current evidence.

## Interaction and regression checks

- Two samples taken 500 ms apart produced different path geometry, confirming continuous organic morphing.
- Browser inspection found five SVG paths and zero image or video elements inside the animation.
- Repository inspection found no remaining copied recording, extracted frame, or reference-derived test-call asset.
- The wrapper computed to a transparent background with visible overflow.
- A fresh production-preview tab reported no console warnings or errors.
- The animation respects `prefers-reduced-motion` by holding its initial frame.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.

No actionable P0, P1, or P2 findings remain.

final result: passed

---

# Test-call missing-animation recovery QA

- Source failure states:
  - `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-fb3bf755-b885-4ce2-baec-4f2441000813.png` (517 x 786 pixels, idle preparation);
  - `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-5bcf6968-e7ba-4bd3-be49-463c89f04d2d.png` (517 x 709 pixels, live speaking).
- Browser-rendered implementation evidence:
  - `D:\Documents\Sauti\design-qa\test-call-screenshots-fixed-a.png`;
  - `D:\Documents\Sauti\design-qa\test-call-screenshots-fixed-b.png`.
- Combined comparison: `D:\Documents\Sauti\design-qa\test-call-missing-vs-restored-comparison.png`.
- Viewport: 1280 x 786 CSS pixels at device scale 1.
- States: real idle `TestCallPanel` playback plus an active speaking fallback with an intentionally unavailable WebM.

## Full-view comparison evidence

The supplied screenshots preserve the correct test-panel layout but show a completely empty cobalt animation field. The corrected browser render keeps the same Sauti typography, controls, panel surfaces, and status treatment while restoring the teal waveform in both the idle and speaking regions.

## Focused comparison evidence

The combined comparison places each supplied blank animation region beside the corrected browser-rendered region. The idle state displays the live WebM waveform; the speaking failure simulation displays the real still waveform asset even though its video source is deliberately unavailable.

## Findings and comparison history

- [P1 fixed] Both production states could render an empty cobalt field when the animation files were absent from a reviewed checkout.
  - Fix: removed the two production animation assets from `dashboard/.gitignore` so the WebM and PNG are visible members of the uncommitted deliverable.
  - Post-fix evidence: `git status --short --untracked-files=all` reports both public assets for maintainer review.
- [P1 fixed] The video element was the only visible waveform source, so autoplay delay, decode failure, reduced motion, or a missing WebM could leave the panel blank.
  - Fix: mounted the real PNG waveform beneath the WebM and kept the video transparent until playback is confirmed. Playback failure and reduced motion retain the visible still asset.
  - Post-fix evidence: the intentionally missing-video speaking state shows the waveform with video opacity `0`.
- [P2 fixed] A muted autoplay could begin before React received the `playing` event, leaving healthy playback transparent.
  - Fix: the component now marks video playback visible when its explicit `play()` promise resolves, while retaining the event handler as an additional signal.
  - Post-fix evidence: the idle video reported `readyState: 4`, `paused: false`, `playingClass: true`, and opacity `1`; its time advanced from approximately 0.83 to 1.63 seconds across captures.

## Required fidelity surfaces

- Fonts and typography: unchanged from the supplied states; no wrapping or hierarchy behavior was changed in production code.
- Spacing and layout rhythm: the existing fixed animation wrappers and surrounding controls are unchanged.
- Colors and visual tokens: the fallback uses the exact cobalt/teal source asset already selected for the animation.
- Image quality and asset fidelity: both the fallback and motion layers use the supplied real raster/WebM assets; no CSS-drawn substitute is introduced.
- Copy and content: idle, language-detection, privacy, and live speaking copy remain unchanged.

## Interaction and accessibility checks

- Normal playback becomes visible after hydration and advances continuously.
- A deliberately unavailable video keeps a visible still waveform instead of a blank field.
- Reduced-motion behavior now displays the still waveform while suppressing playback.
- Browser console contained no warnings or errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.

No actionable P0, P1, or P2 findings remain.

final result: passed

---

# Freeform test-call animation follow-up QA

- Source visual truth: `C:\Users\Zacle\Downloads\screen-capture.webm` (1920 x 1078, approximately five seconds).
- Matched source frame: `D:\Documents\Sauti\design-qa\test-call-reference-matched-frame.png` (1280 x 720 browser-rendered pixels).
- Rendered implementation frames:
  - `D:\Documents\Sauti\design-qa\test-call-freeform-final-a.png`;
  - `D:\Documents\Sauti\design-qa\test-call-freeform-final-b.png`.
- Combined focused comparison: `D:\Documents\Sauti\design-qa\test-call-freeform-comparison.png`.
- Viewport: 1280 x 720 CSS pixels at device scale 1.25.
- State: idle-size and active speaking-size animation crops, playing from the supplied WebM.

## Full-view comparison evidence

The browser-rendered test states preserve the surrounding Sauti panel layout while presenting the waveform as a floating, irregular teal perimeter. The previous circular mask, inset ring, circular outline, and circular shadow are absent. The cobalt field extends beyond the rectangular crop boundary, so neither a round badge nor a visible video tile remains.

## Focused comparison evidence

The combined comparison normalizes the recording crop and the active Sauti crop to equal 350 x 350 regions. Both retain the same open center, overlapping translucent teal lobes, soft edge, and continuously changing non-circular perimeter. The difference between the two silhouettes is an expected difference in playback phase rather than a different visual treatment.

## Findings and comparison history

- [P1 fixed] The prior wrapper used `border-radius: 50%`, forcing every WebM frame into a perfect circular viewport.
  - Fix: removed the circular radius, inset outline, circular shadows, and isolated round background.
  - Post-fix evidence: computed styles report `border-radius: 0px` and `box-shadow: none`; both rendered sizes show the waveform's own perimeter.
- [P2 fixed] Removing the circle initially exposed a saturated rectangular video field because call-state filters recolored the WebM background.
  - Fix: removed the brightness/saturation filters and matched the surrounding cobalt field to the sampled recording color `rgb(1, 23, 109)`, with a sufficiently large solid core around both responsive sizes.
  - Post-fix evidence: the final captures show a continuous field around the animation with no visible square edge.

## Required fidelity surfaces

- Fonts and typography: unchanged; the active status remains readable and aligned below the animation.
- Spacing and layout rhythm: fixed responsive wrappers keep surrounding controls stable while the internal perimeter changes.
- Colors and visual tokens: the source teal/cobalt colors are unfiltered; the surrounding field uses the sampled source background before fading into the navy panel.
- Image quality and asset fidelity: the exact supplied WebM remains the visible source; no CSS-drawn replacement or synthetic transform animation is used.
- Copy and content: existing provider-neutral test-call labels and status copy are unchanged.

## Interaction and accessibility checks

- Playback advanced from approximately 0.97 seconds to 1.91 seconds over the measured interval in both rendered sizes, with `paused: false`, `ended: false`, and `readyState: 4`.
- The two captures show materially different waveform silhouettes without movement of the wrappers.
- The existing reduced-motion effect still pauses and resets the video when requested.
- Browser console contained no warnings or errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.

No actionable P0, P1, or P2 findings remain.

final result: passed

---

# Provider-neutral AI Voice Test QA

- Source visual truth: `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-f6083687-f635-4a48-b56f-53eda4446322.png` (537 x 481 RGB pixels).
- Browser-rendered idle implementation: `D:\Documents\Sauti\design-qa\agent-test-orb-idle.png` (1280 x 720 pixels).
- Browser-rendered active component implementation: `D:\Documents\Sauti\design-qa\agent-test-orb-active.png` (1280 x 720 pixels).
- Combined source/implementation evidence: `D:\Documents\Sauti\design-qa\ai-orb-comparison.png` (893 x 626 pixels).
- Viewport: 1280 x 720 CSS pixels at device scale 1.
- State: configured Amina agent, idle voice-test panel; active speaking state verified in the focused component render because the local API does not create a provider call in QA mode.
- Density normalization: source and implementation were inspected at native density. The combined comparison preserves the source at 537 x 481 and uses a focused 308 x 572 crop of the rendered panel.

## Full-view comparison evidence

The agent editor preserves its three-column working layout at 1280 x 720. The right panel now uses provider-neutral `Voice test` and `Start test call` language, contains the supplied teal-on-blue ring, and keeps all controls visible. Browser inspection confirmed no visible provider name and no live transcript container.

## Focused region comparison evidence

The combined comparison places the supplied visual and the rendered test panel in one image. The implementation uses the exact raster source rather than a CSS approximation. Its blue field, teal ring, soft edges, and irregular silhouette remain recognizable at the smaller product scale. The focused active component render shows the same source asset at 244 x 244 with only a status and end-call control.

## Required fidelity surfaces

- Fonts and typography: the existing Sauti family, weights, and hierarchy are preserved; the provider-neutral labels fit without truncation.
- Spacing and layout rhythm: the idle orb remains fixed at 106 x 106 in the short desktop viewport; the active composition expands it to the intended larger focal treatment while keeping header and status visible.
- Colors and visual tokens: the asset's deep cobalt and teal palette blends with the existing navy console surface and cyan accent tokens.
- Image quality and asset fidelity: the implementation uses the exact supplied 537 x 481 PNG in two clipped layers. No custom SVG, CSS drawing, placeholder, or replacement illustration is used.
- Copy and content: visible provider branding is removed from the test panel, voice library, number guidance, and DTMF guidance. The active transcript and typed-message controls are absent; concise listening/thinking/speaking status copy remains.

## Interaction, motion, and accessibility checks

- Idle browser inspection found `providerVisible: false`, `transcriptVisible: false`, and a visible fixed-size orb.
- Two samples 700 ms apart changed the image-layer transform while the 106 x 106 wrapper rectangle stayed identical.
- Active focused-render samples 650 ms apart changed the image-layer transform while the 244 x 244 wrapper rectangle stayed identical.
- Listening, thinking, and speaking classes use progressively faster motion without changing layout size.
- `prefers-reduced-motion: reduce` disables the image-layer animations.
- Browser console checks returned no errors for either the real idle route or focused active render.

## Findings and comparison history

- [P2 fixed] The prior test UI exposed the infrastructure provider in the eyebrow, CTA, active header, voice library, and configuration errors.
  - Fix: replaced customer-facing provider labels with Sauti-owned voice-test language while retaining provider identifiers only inside runtime checks and diagnostics.
  - Post-fix evidence: the route DOM and browser-visible text contain no provider name.
- [P2 fixed] The active state presented a scrolling transcript and typed-message controls instead of maintaining conversational focus.
  - Fix: replaced the transcript and input controls with the supplied animated orb and one status message.
  - Post-fix evidence: the focused active render contains only the live-test header, orb, status, and End action.

No actionable P0, P1, or P2 findings remain. The real active call was not placed during QA, so provider connectivity itself was not retested by this visual change.

final result: passed

---

# Agent Setup Visibility and Billing Navigation QA

- Source visual truth:
  `D:\Documents\Sauti\design-qa\agent-setup-hidden-reference.png`
  (371 x 793 pixels; setup rail with the lower card clipped).
- Rendered implementation:
  `D:\Documents\Sauti\design-qa\agent-setup-rail-fixed-1280x720.png`
  (1280 x 720 CSS pixels at device scale 1).
- Focused same-input comparison:
  `D:\Documents\Sauti\design-qa\agent-setup-visibility-comparison.png`.
- Billing evidence:
  `D:\Documents\Sauti\design-qa\billing-navigation-restored-1280x720.png`.
- State: new Appointment Booker draft, Main settings selected, personalisation
  dialog closed; billing Overview with preview-only fallback usage data.

## Findings and comparison history

- [P1 fixed] The setup rail used `overflow: hidden`, which clipped the lower
  personalisation card and could make setup destinations unreachable at short
  viewport heights.
  - Fix: separated the rail into readiness, bounded section navigation, and a
    persistent personalisation footer. At 1280 x 720 all eight setup buttons
    and the footer are simultaneously visible.
  - Post-fix evidence: the section region measures 319 px client and scroll
    height, every button bottom is within the region, and the footer bottom is
    702 px inside the 720 px viewport.
- [P1 fixed] Opening Usage & billing replaced the authenticated product shell,
  removing the main workspace navigation.
  - Fix: removed the route-specific standalone return and restored billing to
    the shared console shell with an active sidebar state.
  - Post-fix evidence: the billing capture shows the 252 px sidebar, workspace
    switcher, top bar, and highlighted `Usage & billing` destination.
- [P1 fixed] Restoring the sidebar exposed a billing hero width assumption that
  clipped the remaining-minutes column at 1280 px.
  - Fix: added a console-width hero grid for 1181-1500 px viewports.
  - Post-fix evidence: the hero client and scroll width both measure 951 px;
    the remaining column ends at x=1234 inside the 1280 px viewport.

## Required fidelity surfaces

- Fonts and typography: the existing Sauti type scale and weights are retained;
  short-height rows remove secondary descriptions instead of truncating titles.
- Spacing and layout rhythm: compact 38 px short-height rows preserve all eight
  destinations and the full personalisation card without crowding the readiness
  summary.
- Colors and visual tokens: existing navy surfaces, cyan active state, muted
  secondary labels, and green completion checks are unchanged.
- Image quality and asset fidelity: no raster or brand assets were replaced;
  the existing icon library and generated test-call orb remain intact.
- Copy and content: all setup labels, readiness guidance, billing labels, and
  preview-only language remain unchanged and readable.

## Interaction and regression checks

- Every setup destination remains a working button inside the new navigation
  region; the mobile horizontal-strip behavior is preserved.
- The personalisation action remains visible and functional beneath the list.
- Billing renders inside the normal expanded and collapsible dashboard shell.
- Billing has no page-level horizontal overflow at 1280 x 720.
- Browser console inspection returned no warnings or errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed.
- No actionable P0/P1/P2 findings remain.

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

---

# Recording-matched Voice Orb Motion QA

- Source visual truth: `C:\Users\Zacle\Downloads\screen-capture.webm` (1920 x 1078 video pixels).
- Source cycle evidence: `D:\Documents\Sauti\design-qa\motion-reference\contact-sheet.png` (12 frames sampled every 300 ms).
- Focused source cycle: `D:\Documents\Sauti\design-qa\motion-reference\orb-cycle-crops.png`.
- Browser-rendered implementation:
  - `D:\Documents\Sauti\design-qa\agent-test-orb-recording-a.png`;
  - `D:\Documents\Sauti\design-qa\agent-test-orb-recording-b.png`;
  - `D:\Documents\Sauti\design-qa\agent-test-orb-recording-final.png`.
- Combined motion comparison: `D:\Documents\Sauti\design-qa\orb-motion-comparison.png`.
- Viewport: 1280 x 720 CSS pixels at device scale 1.
- State: configured Amina agent, idle voice-test panel. The same `TestCallOrb` component is used during active listening, thinking, and speaking states.
- Density normalization: the 1920 x 1078 recording is rendered responsively and cropped to the orb region; source and implementation crops were normalized to equal comparison cells.

## Full-view comparison evidence

The corrected implementation preserves the existing Sauti agent-editor layout and provider-neutral copy. The test panel now plays the supplied motion source inside the orb crop instead of transforming a still image. Two browser captures 650 ms apart show different perimeter silhouettes while the 106 x 106 wrapper remains at the identical coordinates.

## Focused comparison evidence

The focused comparison places two source-video states next to two Sauti-rendered states. Both show the same irregular perimeter deformation: lobes swell, flatten, and exchange prominence while the center remains anchored. The earlier whole-object rotation and scale pulse are absent. A cobalt radial field softens the cropped source boundary against the navy test surface.

## Findings and comparison history

- [P1 fixed] The first implementation animated a still image with rotation, translation, scaling, blur, and opacity. That motion was structurally different from the supplied recording.
  - Fix: removed the synthetic keyframes and layered still-image animation. The component now uses the supplied WebM as the motion asset and crops its original orb region.
  - Post-fix evidence: source and Sauti frame pairs show matching perimeter deformation without whole-object rotation.
- [P2 fixed] The raw recording crop initially read as a hard blue circular badge.
  - Fix: matched the surrounding radial field to the recording's cobalt background and softened it into the existing panel surface.
  - Post-fix evidence: `agent-test-orb-recording-final.png` shows a continuous blue field around the floating ring rather than a bordered badge.

## Required fidelity surfaces

- Fonts and typography: unchanged from the verified Sauti test panel; no new text or wrapping drift was introduced.
- Spacing and layout rhythm: the orb wrapper retains its fixed responsive dimensions and therefore does not move surrounding controls during playback.
- Colors and visual tokens: the exact teal/cobalt recording is retained and blended into the navy console without recoloring the source motion.
- Image quality and asset fidelity: the exact user-supplied WebM is used as the visible motion source, with the still PNG retained only as a loading and reduced-motion poster.
- Copy and content: provider-neutral `Voice test` and `Start test call` copy remains unchanged; no transcript is shown.

## Interaction and accessibility checks

- Browser inspection reported a ready 1920 x 1078 video, active playback, no visible provider name, and no transcript container.
- Playback advanced and looped between the two measured samples while the wrapper rectangle remained unchanged.
- Reduced-motion preference pauses the video at its poster frame and resumes playback when the preference changes.
- Browser console contained no errors.
- `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run build`, and `git diff --check` passed.

No actionable P0, P1, or P2 findings remain.

final result: passed
