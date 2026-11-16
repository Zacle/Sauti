/** Sample journeys for the public product previews. Keep labels aligned with AgentCreator.
 * These fixtures never load workspace data or perform calls. */
export const walkthroughs = {
  "appointment-booking": {
    name: "Appointment booking", agent: "Front desk", section: "Integrations",
    heading: "A conversation becomes", accent: "a place in the diary.",
    intro: "Connect a calendar, give the agent your booking rules, then follow a caller from their first question to a confirmed appointment.",
    steps: ["Connect the calendar", "Check a real opening", "Review the booking"],
    notes: ["In Integrations, connect your provider and enable it for this agent. Set the service duration and booking rules in Prompt.", "The agent checks availability before offering a time. The caller chooses a slot before the booking is created.", "Open Bookings to review the appointment. Open the call to see the conversation that led to it."],
    caller: "Could I book a cleaning on Friday morning?", reply: "Friday at 10:00 is available. Shall I book that for you?",
    confirmation: "Yes, Friday at ten works.", result: "Cleaning · Friday, 10:00", detail: "30 minutes · Bright Smile Dental",
    fields: [["Calendar destination", "Google Calendar"], ["Meeting routing", "Single calendar"]],
    prompt: "Check availability first. Offer a 30-minute cleaning. Confirm the caller’s choice before creating the booking.",
    test: "Ask for a time that is already taken.", expected: "Offer another available slot; never invent an opening.",
    ready: ["Calendar connected to the agent", "Service and duration in Prompt", "A test booking reviewed in Bookings"],
  },
  "customer-service": {
    name: "Customer service", agent: "Support desk", section: "Knowledge",
    heading: "Give every answer", accent: "a source you trust.",
    intro: "Add the information your team approves. See how a simple question becomes an answer, and where you set the limits.",
    steps: ["Add approved knowledge", "Answer the question", "Review the call"],
    notes: ["In Knowledge, add business notes or upload a document. Use Prompt to explain when the agent should ask a person for help.", "The example answer uses the opening hours you supplied. Policy exceptions should follow your escalation instructions.", "Calls brings the summary and transcript together so your team can review the answer and improve the source information."],
    caller: "Are you open on Saturday?", reply: "Yes, we’re open from 9am to 1pm on Saturdays. What can I help you with?",
    confirmation: "Great, I’ll come by in the morning.", result: "Opening-hours question answered", detail: "Caller intent · Business hours",
    fields: [["Approved knowledge", "Business information"], ["Document", "Front-desk guide.pdf"]],
    prompt: "Opening hours\nMonday–Friday: 9am–5pm\nSaturday: 9am–1pm\nSunday: closed\n\nPolicy exceptions must be referred to the team.",
    test: "Ask for an exception that is not in your policy.", expected: "Acknowledge the limit and offer a human; do not invent a policy.",
    ready: ["Current business information added", "Escalation rules written in Prompt", "Answers checked in a test transcript"],
  },
  "lead-qualification": {
    name: "Lead qualification", agent: "Property enquiries", section: "Prompt",
    heading: "Turn an enquiry into", accent: "a useful next conversation.",
    intro: "Tell the agent what your team needs to learn. Follow a property enquiry through discovery and into a call summary your team can act on.",
    steps: ["Define the questions", "Discover the need", "Read the handover"],
    notes: ["Use Prompt to define the qualifying questions. In Post-call, enable Call summary and Caller intent. Connect a CRM separately if your workflow needs one.", "Collect details naturally, one question at a time. Confirm the caller’s needs rather than guessing a budget or urgency.", "Review the captured details in Calls. This example keeps them in the summary; delivery to a CRM requires a configured integration."],
    caller: "I’m looking for a three-bedroom rental next month.", reply: "Which area are you considering, and what monthly budget should we work with?",
    confirmation: "Kilimani, up to KES 120,000 a month.", result: "Rental enquiry · Follow up", detail: "Caller intent · Property rental",
    fields: [["Call summary", "Enabled"], ["Caller intent", "Enabled"]],
    prompt: "Ask about location, property size, budget and moving date. Confirm their preferred contact details. Summarise the requirements for the team.",
    test: "Leave the budget undecided.", expected: "Record that it is unknown and continue; do not fill in an invented amount.",
    ready: ["Qualifying questions in Prompt", "Summary and intent enabled in Post-call", "Destination configured if using a CRM"],
  },
  "human-transfer": {
    name: "Human transfer", agent: "Customer care", section: "Routing",
    heading: "Know when to", accent: "bring in a person.",
    intro: "Set a transfer destination and clear instructions for escalation. Follow the moment a caller asks for a human.",
    steps: ["Set the destination", "Recognise the request", "Review the handoff"],
    notes: ["In Routing, enable Allow live transfer and add your team’s transfer number. Define the escalation triggers in Prompt.", "A direct request for a person is the cue in this example. The agent acknowledges it before attempting the transfer.", "Review the call and its summary. Test the destination and write a fallback instruction for times when your team cannot answer."],
    caller: "I’d like to speak to a person about my bill.", reply: "Of course. I’ll try to connect you to our team now.",
    confirmation: "Thank you.", result: "Human requested · Transfer attempted", detail: "Destination · Customer care",
    fields: [["Allow live transfer", "Enabled"], ["Transfer number", "+254 ••• ••• 214"]],
    prompt: "When a caller requests a person, offer a transfer. If the team cannot answer, ask for a message and a preferred callback time.",
    test: "Call when the transfer destination cannot answer.", expected: "Follow your fallback instructions without claiming a person has joined.",
    ready: ["Live transfer enabled in Routing", "Destination number tested", "Fallback instructions in Prompt"],
  },
  "multilingual-call-handling": {
    name: "Multilingual calls", agent: "Multilingual reception", section: "Main settings",
    heading: "Same business rules.", accent: "Their language.",
    intro: "Choose the languages your agent supports, then explore the same booking conversation in English, Kiswahili or French.",
    steps: ["Choose caller languages", "Hear the conversation", "Keep the context"],
    notes: ["In Main settings, select the primary language and supported caller languages. Choose a suitable voice in Speech & transcription.", "Switch the language below to explore the example. Test your selected voice and languages with real phrases before launch.", "Review the transcript in Calls. The booking rules and connected tools stay the same across your configured languages."],
    caller: "Habari, naweza kuweka miadi ya Ijumaa?", reply: "Ndiyo, saa nne asubuhi inapatikana. Nikuwekee?",
    confirmation: "Ndiyo, asante.", result: "Booking request · Kiswahili", detail: "One agent · Shared booking instructions",
    fields: [["Primary language", "English"], ["Supported caller languages", "English · Kiswahili · French"]],
    prompt: "Greet the caller naturally. Respond in a supported language they use. Keep the same booking and escalation rules in every language.",
    test: "Switch from Kiswahili to English during a test call.", expected: "Check that the agent follows the change without losing the booking details.",
    ready: ["Supported languages selected", "Voice tested in each language", "Transcript and tool outcomes reviewed"],
  },
  "after-hours-answering": {
    name: "After-hours answering", agent: "Evening reception", section: "Routing",
    heading: "Your doors close.", accent: "The conversation doesn’t have to.",
    intro: "Set your weekly schedule and decide what happens outside it. Explore the three after-hours behaviours already available in Routing.",
    steps: ["Set your hours", "Choose the response", "Pick up the next day"],
    notes: ["In Routing, choose Weekly schedule and set your opening hours. Check the agent’s timezone in Main settings.", "Choose Answer normally, Collect a message or Announce closure. The preview below shows how that choice changes this example call.", "Review the call summary when your team returns. A captured callback request is a follow-up for your team, not an automatically booked appointment."],
    caller: "Are you still open? I’d like to arrange a visit.", reply: "The team has finished for today. May I take a message for them?",
    confirmation: "Please ask them to call me tomorrow morning.", result: "Callback requested · Tomorrow morning", detail: "Message for the team · Review in Calls",
    fields: [["Operating hours", "Weekly schedule"], ["Timezone", "Africa/Nairobi"]],
    prompt: "Outside office hours, collect the caller’s name, contact details and reason for calling. Confirm the message for the team.",
    test: "Make a test call outside the configured schedule.", expected: "Check the timezone and confirm that the chosen after-hours behaviour applies.",
    ready: ["Weekly schedule and timezone checked", "After-hours behaviour selected", "Closed-hours response tested"],
  },
} as const;

export type SolutionSlug = keyof typeof walkthroughs;
export type Walkthrough = (typeof walkthroughs)[SolutionSlug];

export function isSolutionSlug(slug: string): slug is SolutionSlug {
  return Object.prototype.hasOwnProperty.call(walkthroughs, slug);
}
