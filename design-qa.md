# Design QA: demo-request dropdowns

## Evidence

- Source visual truth: `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-3de907cc-e55c-49ad-aa02-d492ec521ae5.png`
- Implementation capture: `C:\Users\Zacle\.codex\visualizations\2026\07\29\019faf25-54b2-7311-b536-4c619135ab47\request-demo-dropdown-implementation.png`
- Source pixels: 810 x 442, cropped desktop form region.
- Implementation viewport: 1280 x 720 CSS pixels at device scale factor 1.25.
- State: expected monthly conversations menu open with `Not sure yet` selected.
- Comparison method: the source and browser-rendered implementation were opened together; the form-control region was compared because the source is a crop rather than a complete viewport.

## Findings

- No remaining P0, P1, or P2 findings.
- The native operating-system popup from the source has intentionally been replaced by the product's Radix-based dark select surface. The open menu now preserves the Sauti palette, radius, typography, focus ring, elevation, selected checkmark, and visible scroll affordances.
- Fonts and typography: labels, selected values, and options retain the existing Sauti family and hierarchy; option density remains readable.
- Spacing and layout rhythm: triggers remain full-width and aligned to the existing two-column form grid; the menu matches its trigger width and does not disturb document layout.
- Colors and visual tokens: the selected state uses the existing teal semantic accent instead of the browser-native blue highlight; contrast remains clear against the navy panel.
- Image quality and asset fidelity: this control contains no raster imagery; standard Lucide chevrons/checkmarks match the established interface icon system.
- Copy and content: all original country, industry, and monthly-volume values are preserved, including the `Select an industry` placeholder.

## Interaction verification

- Opened all three custom comboboxes in the in-app browser.
- Selected `Under 100` and `Healthcare` and verified their trigger values updated.
- Opened the long country list, verified `Kenya` remained selected, and confirmed the menu exposes scrolling controls.
- Checked browser console errors after the interactions: none.
- Verified the homepage exposes an enabled `Retry voice demo` action after initialization failure.

## Comparison history

- Initial P1: the native select popup ignored the application theme and displayed a bright browser-blue selection surface.
- Fix: replaced the three native selects with the existing accessible `DarkSelect`, improved its iconless layout, placeholder, form-name/required support, selected state, open chevron, width matching, and scroll controls.
- Post-fix evidence: `C:\Users\Zacle\.codex\visualizations\2026\07\29\019faf25-54b2-7311-b536-4c619135ab47\request-demo-dropdown-implementation.png`; the open dropdown remains visually integrated and selections update without console errors.

## Follow-up polish

- None required for this scope.

final result: passed
