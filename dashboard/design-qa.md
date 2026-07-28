# Homepage design QA

- Reference: the approved refined third Product Design full-page direction (`Living Voice System`), with customer stories removed.
- Target viewport: desktop marketing homepage, 1440px wide.
- Implementation state: production build, TypeScript, and lint checks pass.
- Source assets:
  - `public/images/marketing/sauti-phone-hero.png`;
  - `public/images/marketing/industries/{healthcare,professional-services,home-services,retail,education}.png`.
- Automated browser capture: blocked. The in-app browser runtime fails during connection setup with `Cannot redefine property: process`.
- Visual comparison: blocked until a browser screenshot can be captured.
- Final result: blocked.

The implementation must be captured at the target desktop viewport and a representative mobile viewport, then compared against the selected reference before changing this result to `passed`.
