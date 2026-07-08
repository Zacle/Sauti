# Voice Agent Evaluation

Use this document to judge whether Sauti calls feel like a natural phone conversation, not just whether the agent completes a workflow.

## Target Style

The target call style is a concise appointment-booking receptionist:

- adapts language without friction,
- speaks in one or two short phone-like sentences,
- repairs misunderstandings gently,
- stays inside the configured scope,
- answers only from known facts,
- asks one question at a time,
- progresses booking details in a predictable order,
- confirms important dates/times naturally,
- never pretends a tool action happened unless it did.

## Reference Scenario: French Appointment Booking

Caller intent: French-speaking caller asks whether the agent speaks French, checks general information, asks about services, then books a consultation for tomorrow at 10:00.

Required behaviors:

- When caller asks `Bonjour, est-ce que vous parlez français ?`, reply in French naturally.
- When caller asks for information, ask what information they need.
- When caller asks hours or availability without a service, do not invent hours. If configured hours are absent, ask what service or appointment type they mean.
- When caller asks what services are offered, answer only from configured services. If unknown, say the exact service list is not available and offer to continue with booking or human follow-up.
- When caller says they want a consultation, collect booking details in this order:
  1. full name,
  2. date,
  3. time preference,
  4. contact detail.
- If the caller gives unclear text for a name, phone, or email, ask them to repeat slowly.
- Do not ask for date of birth, insurance, symptoms, or medical history unless the agent configuration explicitly requires it.
- Confirm relative dates using the actual date. Example: on 2026-07-08, `demain` means Thursday 2026-07-09.
- Do not say a booking, callback, message, or confirmation was completed unless a tool result confirms it.

## Scoring Rubric

Score each category from 1 to 5.

- Natural phone delivery: short, spoken, not scripted or dashboard-like.
- Language handling: follows caller language without arguing or announcing internal policy.
- Grounding: uses configured facts/tools only; admits missing facts.
- Repair: handles `pardon`, unclear names, unclear numbers, and silence without pretending.
- Booking flow: gathers only needed fields in a sensible order.
- Tool truthfulness: never claims an action completed without a successful tool result.
- Empathy: warm and calm without fake emotion or excessive apology.

Release bar:

- No category below 4 for production voice-agent changes.
- Any grounding or tool-truthfulness score below 4 blocks release.

## Regression Prompts

Run these manually or through an automated call simulation after voice-agent changes:

- `Bonjour, est-ce que vous parlez français ?`
- `Je vous appelle pour vérifier quelques informations.`
- `Vous êtes ouvert de quelle heure à quelle heure ?`
- `Quel service vous offrez ?`
- `J'aimerais prendre un rendez-vous de consultation.`
- unclear name, for example `On ne sait pas pareil.`
- clear name, for example `Mon nom, c'est Zakari Sylva.`
- `Pour demain.`
- `À 10 heures.`
- unclear phone number or email.
- `Au revoir.`
