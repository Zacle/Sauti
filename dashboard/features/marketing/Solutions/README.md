# Public solution previews

These previews use fictional data and make no API calls. They are illustrative,
not screenshots of a customer workspace. Only the marketing routes are changed.

- `walkthrough-content.ts`: setup instructions, example conversations, outcomes,
  and test scenarios for each solution. Edit this when product behaviour changes.
- `SolutionExperience.tsx`: compact hero visuals and the walkthrough section.
- `InteractiveDemo.tsx` and `InteractiveDemo.module.css`: the clickable workspace,
  three-action journeys, local selections, transitions, and completion receipts.
  Uses shared buttons, badges, Motion, and the existing marketing tokens.
- `SolutionPages.tsx`: overview cards, detail-page heroes and related links.

Product sources used to align the previews:

- `features/agents/AgentCreator/AgentCreator.tsx`: Main settings, Prompt,
  Knowledge, Routing, Integrations, Post-call, and Test call.
- `features/agents/domain/structured-agent-settings.ts`: calendar destination,
  meeting routing, and after-hours policies.
- `features/bookings/presentation/BookingsPage.tsx`: booking review.
- `features/calls/CallsPage/CallsPage.tsx`: call summary and transcript.

When these screens change, update the corresponding scene and fixture together.
Keep the sample-data label. Do not import authenticated page components or live
workspace data into public previews. Screenshots or video can replace a scene
later without changing the surrounding walkthrough; keep a text alternative and
use a poster and user-initiated playback for any recording.

Walkthroughs autoplay while visible: each action gets a moving cursor and click
ripple before advancing, and the completed result holds before the loop restarts.
Pause/Play controls allow exploration; clicking a sample control pauses playback.
Offscreen/hidden-page playback pauses, and reduced-motion users get manual controls.
Selections are retained through completion and cleared by Restart demo / Try
again. Booking slots, language, support question, transfer availability and
after-hours selectors are local examples only. Changes must update both the
conversation and final receipt. Motion transitions respect reduced motion.
