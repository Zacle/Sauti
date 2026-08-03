# Design QA: public voice demo dialog

## Evidence

- Source visual truth: `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-9027c15e-0516-4a26-85c3-54cd658c54fb.png`.
- Source error state: `C:\Users\Zacle\AppData\Local\Temp\codex-clipboard-8a5ae0b7-39e6-404e-82df-34a68431777e.png`.
- Implementation capture: `C:\Users\Zacle\.codex\visualizations\2026\07\29\019faf25-54b2-7311-b536-4c619135ab47\public-demo-centered-dialog.png`.
- Source pixels: 903 x 903 for the speaking dialog and 787 x 261 for the post-call error crop.
- Implementation viewport and pixels: 1280 x 720 CSS pixels at device scale factor 1.25; browser capture is 1280 x 720 pixels.
- State: desktop speaking dialog over the homepage.
- Normalization: full-viewport composition was compared rather than pixel scale because the source and implementation have different viewport dimensions.

## Findings

- No remaining P0, P1, or P2 visual findings.
- Fonts and typography: the existing Sauti dialog hierarchy, weights, wrapping, and labels are unchanged.
- Spacing and layout rhythm: the modal is portaled directly under `BODY`; its 520 x 560 CSS-pixel panel measured exactly at viewport center (`centerDeltaX=0`, `centerDeltaY=0`) inside a 1280 x 720 backdrop.
- Colors and visual tokens: the navy/teal dialog, dimmed backdrop, coral end-call action, radii, and elevation remain consistent with the source.
- Image quality and asset fidelity: the existing canvas voice animation remains sharp and centered; no image asset was replaced.
- Copy and content: all speaking-state copy is preserved. The teardown-only playback rejection from the second source image is no longer presented as a customer-facing error.

## Interaction and runtime verification

- Browser-rendered geometry confirmed the backdrop spans exactly 1280 x 720, the dialog is centered at 380 x 80, the portal parent is `BODY`, and background scrolling is locked.
- Browser console errors in the rendered dialog state: none.
- The focused voice suite passed 32/32 tests, including new active-vs-teardown audio playback coverage.
- Typecheck, zero-warning lint, and the production build passed.
- A real microphone call was not initiated during automated QA; the user-provided post-call screenshot supplied the failure evidence and the lifecycle behavior is covered at policy level.

## Comparison history

- Initial P1: the fixed overlay inherited the transformed homepage container's containing block and appeared left of the viewport center.
- Fix: render the overlay through a React portal, give the backdrop explicit viewport dimensions, lock background scrolling, and preserve internal responsive overflow.
- Post-fix evidence: the browser measurement reports zero horizontal and vertical center delta.
- Initial P1: removing the audio element during normal call teardown rejected a pending `play()` promise and surfaced a red provider error after the call.
- Fix: ignore playback rejection only after the runtime is stopped/ended or its media element/source is removed; genuine failures while active remain reportable. Late public-demo provider errors are also ignored once teardown starts, and closing the ended dialog resets the trigger for another call.

## Follow-up polish

- Run one production microphone call after deployment to confirm the real provider teardown remains visually silent.

final result: passed
