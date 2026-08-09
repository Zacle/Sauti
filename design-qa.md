# Design QA: usage and billing checkout

## Evidence

- Source visual truth: `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-7581d99e-55e3-4196-b826-a0e35f30a028.png`.
- Source pixels: 1560 x 1016.
- Target state: desktop `Plans & add-ons` billing workspace with a Whop sandbox checkout available.
- Automated browser capture: blocked because the Codex in-app browser runtime could not initialize in this desktop session (`failed to write kernel assets: path not found`). No alternate browser automation was used.

## Static comparison

- The implementation preserves the existing Sauti console shell, navy/teal palette, bordered cards, typography, spacing rhythm, and responsive breakpoints shown in the source.
- The plans workspace now mirrors the source hierarchy: numbered plan, configuration, and estimate steps; monthly/annual selector; plan cards; usage and add-on controls; sticky estimate; explicit currency; primary checkout action; and the four-part billing benefits strip.
- All visible icons come from the existing Lucide icon library. No placeholder or approximate image asset was introduced.
- Sandbox status is data-driven rather than decorative. The page distinguishes configured Whop sandbox, live checkout, and incomplete server setup.
- The primary action opens a review dialog and then calls the real provider-neutral checkout endpoint. It no longer describes this journey as a mutation-free preview.

## Interaction and build verification

- Whop sandbox/live/provider/configuration status is returned by an authenticated backend endpoint.
- Incomplete server setup disables checkout with an explicit setup-required state.
- A configured sandbox creates the same real hosted checkout configuration used by production, but against the separately configured Whop sandbox resources.
- Backend tests passed.
- Dashboard typecheck passed.
- ESLint passed with zero warnings.
- Optimized Next.js production build passed, including `/billing`.
- `git diff --check` passed (line-ending notices only).

## Required live visual acceptance

- After deployment, open `/billing?tab=plans` at 1560 x 1016 and compare it beside the source.
- Confirm the banner reads `Whop sandbox checkout is enabled` when `WHOP_SANDBOX=true`.
- Select a plan, change minutes and add-ons, and confirm the estimate updates without changing the workspace.
- Continue to Whop Sandbox and complete one test checkout; confirm Sauti changes entitlement only after the signed membership webhook.

final result: blocked — implementation and build checks passed, but the required browser screenshot comparison could not run because the in-app browser runtime was unavailable.
