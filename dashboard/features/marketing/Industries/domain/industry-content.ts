export type IndustryIconKey =
  | "bell"
  | "briefcase"
  | "building"
  | "calendar"
  | "check"
  | "clock"
  | "education"
  | "headphones"
  | "home"
  | "key"
  | "languages"
  | "map"
  | "message"
  | "phone"
  | "route"
  | "scissors"
  | "shield"
  | "sparkles"
  | "stethoscope"
  | "user"
  | "users"
  | "wrench";

export type IndustryStep = {
  icon: IndustryIconKey;
  title: string;
  detail: string;
};

export type IndustryPillar = {
  eyebrow: string;
  title: string;
  description: string;
};

export type IndustryContent = {
  slug: string;
  label: string;
  cardDescription: string;
  image: string;
  imageAlt: string;
  variant: "left" | "right" | "cinematic" | "memo" | "campus" | "field";
  accent: string;
  heroPrefix: string;
  heroHighlight: string;
  heroSuffix: string;
  description: string;
  proof: string[];
  call: {
    heading: string;
    callerLabel: string;
    caller: string;
    agent: string;
    outcome: string;
    outcomeDetail: string;
    secondaryHeading: string;
    secondaryCaller: string;
    secondaryOutcome: string;
  };
  transformation: {
    titlePrefix: string;
    titleHighlight: string;
    beforeLead: string;
    afterLead: string;
    before: IndustryStep[];
    after: IndustryStep[];
  };
  sectionEyebrow: string;
  sectionTitle: string;
  sectionDescription: string;
  pillars: IndustryPillar[];
  systems: string[];
  finalTitle: string;
  finalDescription: string;
};

export const industries: IndustryContent[] = [
  {
    slug: "clinics-healthcare",
    label: "Clinics & Healthcare",
    cardDescription: "Protect patient access without adding more pressure to the front desk.",
    image: "/images/marketing/industries/clinics-healthcare-hero.png",
    imageAlt: "A clinic receptionist welcoming a patient at a modern front desk",
    variant: "left",
    accent: "#32d7ee",
    heroPrefix: "Your front desk can’t answer every call. ",
    heroHighlight: "Sauti can.",
    heroSuffix: "",
    description:
      "Give every patient a calm first response. Sauti answers routine questions, coordinates appointments, and brings sensitive needs to your team with the context intact.",
    proof: ["Appointment-ready", "Multilingual", "Privacy-minded"],
    call: {
      heading: "Appointment request",
      callerLabel: "Patient",
      caller: "I need a general check-up next week.",
      agent: "Tuesday at 10:30 AM is available. Shall I reserve it?",
      outcome: "Appointment confirmed",
      outcomeDetail: "Tue, 10:30 AM · General consultation",
      secondaryHeading: "Sensitive question",
      secondaryCaller: "I need to speak with someone about my results.",
      secondaryOutcome: "Routed to the care team",
    },
    transformation: {
      titlePrefix: "From an overloaded front desk to ",
      titleHighlight: "reliable patient access",
      beforeLead: "Patients wait. Staff repeat manual work.",
      afterLead: "Every call reaches a safe next step.",
      before: [
        { icon: "phone", title: "Calls stack up", detail: "Busy lines and missed patients" },
        { icon: "clock", title: "Long waits", detail: "Routine questions consume time" },
        { icon: "calendar", title: "Manual scheduling", detail: "Back-and-forth creates errors" },
        { icon: "users", title: "Staff stretched", detail: "Less attention for in-person care" },
      ],
      after: [
        { icon: "headphones", title: "Every call answered", detail: "Consistent, calm first response" },
        { icon: "calendar", title: "Slots coordinated", detail: "Availability checked before promises" },
        { icon: "route", title: "Sensitive calls routed", detail: "Context follows the patient" },
        { icon: "shield", title: "Boundaries respected", detail: "Clear rules for what AI can do" },
      ],
    },
    sectionEyebrow: "Built around patient access",
    sectionTitle: "Routine calls move quickly. Sensitive care stays human.",
    sectionDescription:
      "The agent follows your hours, appointment rules, escalation policy, and approved information instead of improvising around clinical questions.",
    pillars: [
      { eyebrow: "Access", title: "Answer common questions clearly", description: "Opening hours, visit preparation, directions, and appointment types stay available whenever patients call." },
      { eyebrow: "Coordination", title: "Book, reschedule, and confirm", description: "Sauti checks real availability, captures the right details, and sends status updates after every change." },
      { eyebrow: "Escalation", title: "Know when to bring in staff", description: "Urgent, sensitive, or uncertain requests are routed with a concise summary instead of a cold handoff." },
    ],
    systems: ["Google Calendar", "Calendly", "Email confirmations", "Human transfer"],
    finalTitle: "Make patient access feel calm from the first ring.",
    finalDescription: "Start with one clinic workflow, test it with your team, and expand only when the boundaries feel right.",
  },
  {
    slug: "salons-beauty",
    label: "Salons & Beauty",
    cardDescription: "Turn every service question into a polished booking experience.",
    image: "/images/marketing/industries/salons-beauty-hero.png",
    imageAlt: "A salon owner consulting with a client in a contemporary beauty studio",
    variant: "right",
    accent: "#f9a8d4",
    heroPrefix: "Stay with the client in your chair. ",
    heroHighlight: "Sauti handles the phone.",
    heroSuffix: "",
    description:
      "Let clients ask about services, find the right time, reschedule, or cancel without pulling stylists away from the work in front of them.",
    proof: ["Service-aware", "Schedule-ready", "Always polished"],
    call: {
      heading: "New service booking",
      callerLabel: "Client",
      caller: "Can I book knotless braids this Saturday?",
      agent: "Amina has 11:00 AM available. The service takes three hours.",
      outcome: "Booking confirmed",
      outcomeDetail: "Sat, 11:00 AM · Knotless braids",
      secondaryHeading: "Schedule change",
      secondaryCaller: "Can I move my colour appointment?",
      secondaryOutcome: "Rescheduled and client notified",
    },
    transformation: {
      titlePrefix: "From interrupted appointments to ",
      titleHighlight: "an effortless booking rhythm",
      beforeLead: "The phone competes with the client in front of you.",
      afterLead: "Bookings move while the team keeps working.",
      before: [
        { icon: "bell", title: "Phone keeps ringing", detail: "Stylists stop mid-service" },
        { icon: "message", title: "Questions repeat", detail: "Prices and durations explained again" },
        { icon: "calendar", title: "Diary gets messy", detail: "Changes arrive across channels" },
        { icon: "user", title: "Clients wait", detail: "The experience loses its polish" },
      ],
      after: [
        { icon: "sparkles", title: "Services explained", detail: "Duration and preparation included" },
        { icon: "calendar", title: "Right slot offered", detail: "Staff and service availability aligned" },
        { icon: "check", title: "Changes completed", detail: "Reschedules and cancellations confirmed" },
        { icon: "bell", title: "Team stays informed", detail: "Every booking reaches the diary" },
      ],
    },
    sectionEyebrow: "Designed for the service menu",
    sectionTitle: "The agent understands more than dates and times.",
    sectionDescription:
      "Give Sauti your services, durations, team availability, preparation notes, and booking rules so callers receive answers that match how the salon really operates.",
    pillars: [
      { eyebrow: "Discover", title: "Guide clients to the right service", description: "Answer questions about options, duration, preparation, and who is available without rushing the conversation." },
      { eyebrow: "Book", title: "Protect the diary from guesswork", description: "Match service length, staff, opening hours, and real availability before a booking is confirmed." },
      { eyebrow: "Retain", title: "Make changes feel easy", description: "Reschedule or cancel by phone, send the updated status, and keep the relationship warm." },
    ],
    systems: ["Google Calendar", "Booking email", "SMS-ready events", "Owner follow-up"],
    finalTitle: "Give every caller the same polished welcome as every walk-in.",
    finalDescription: "Configure one service menu and let your team hear how Sauti represents the salon before you go live.",
  },
  {
    slug: "real-estate",
    label: "Real Estate",
    cardDescription: "Qualify intent while it is fresh and turn serious callers into viewings.",
    image: "/images/marketing/industries/real-estate-hero.png",
    imageAlt: "A real-estate professional showing a contemporary property to prospective buyers",
    variant: "cinematic",
    accent: "#7dd3fc",
    heroPrefix: "Property interest moves fast. ",
    heroHighlight: "Your response should too.",
    heroSuffix: "",
    description:
      "Sauti captures the property, budget, timing, and viewing preference on the first call—then routes qualified interest to the right agent.",
    proof: ["Intent captured", "Viewings coordinated", "Leads routed"],
    call: {
      heading: "Buyer enquiry",
      callerLabel: "Prospect",
      caller: "I’m calling about the two-bedroom flat in Westlands.",
      agent: "Are you looking to move this quarter, and what viewing time suits you?",
      outcome: "Viewing requested",
      outcomeDetail: "Thu, 4:30 PM · Westlands · Buyer qualified",
      secondaryHeading: "High-intent lead",
      secondaryCaller: "I’m ready to make an offer after a second viewing.",
      secondaryOutcome: "Warm transfer to the listing agent",
    },
    transformation: {
      titlePrefix: "From scattered enquiries to ",
      titleHighlight: "viewing-ready conversations",
      beforeLead: "The team calls back without knowing who is serious.",
      afterLead: "Every enquiry arrives with context and a next step.",
      before: [
        { icon: "phone", title: "Enquiries go cold", detail: "Interest fades before callbacks" },
        { icon: "map", title: "Property unclear", detail: "Agents repeat basic discovery" },
        { icon: "message", title: "Intent unqualified", detail: "Budget and timing stay unknown" },
        { icon: "calendar", title: "Viewings delayed", detail: "More back-and-forth to schedule" },
      ],
      after: [
        { icon: "building", title: "Property identified", detail: "Listing and location captured" },
        { icon: "user", title: "Intent qualified", detail: "Budget, timing, and need structured" },
        { icon: "calendar", title: "Viewing proposed", detail: "Availability coordinated early" },
        { icon: "route", title: "Right agent alerted", detail: "Context arrives with the lead" },
      ],
    },
    sectionEyebrow: "Built for enquiry velocity",
    sectionTitle: "Separate curiosity from intent without making the call feel like a form.",
    sectionDescription:
      "The conversation stays natural while Sauti captures the fields your team needs to prioritize, prepare, and follow up well.",
    pillars: [
      { eyebrow: "Identify", title: "Know the property and the caller", description: "Capture listing interest, preferred area, property type, and contact details in the caller’s own words." },
      { eyebrow: "Qualify", title: "Understand readiness and fit", description: "Ask focused questions about budget, timing, financing, and motivation without turning the call into an interrogation." },
      { eyebrow: "Convert", title: "Move naturally toward a viewing", description: "Coordinate a suitable time or hand high-intent callers directly to the responsible agent." },
    ],
    systems: ["HubSpot", "Salesforce", "Google Calendar", "Signed webhooks"],
    finalTitle: "Respond while the property is still top of mind.",
    finalDescription: "Pilot Sauti on one enquiry type and compare the quality of the handoff your agents receive.",
  },
  {
    slug: "professional-services",
    label: "Professional Services",
    cardDescription: "Prepare every consultation with structured intake and discreet routing.",
    image: "/images/marketing/industries/professional-services-hero.png",
    imageAlt: "Two professional advisors reviewing a client intake in a modern office",
    variant: "memo",
    accent: "#c4b5fd",
    heroPrefix: "Start every consultation ",
    heroHighlight: "already prepared.",
    heroSuffix: "",
    description:
      "Sauti answers new enquiries, captures the reason for contact, books the right consultation, and flags sensitive or urgent matters for a person.",
    proof: ["Structured intake", "Priority routing", "Discreet handoff"],
    call: {
      heading: "New client intake",
      callerLabel: "Prospective client",
      caller: "I need advice about a contract dispute.",
      agent: "I can arrange a consultation. Is there an active deadline we should note?",
      outcome: "Consultation prepared",
      outcomeDetail: "Matter: contract · Deadline flagged · Tue, 2:00 PM",
      secondaryHeading: "Priority matter",
      secondaryCaller: "The response deadline is tomorrow.",
      secondaryOutcome: "Escalated to the duty professional",
    },
    transformation: {
      titlePrefix: "From vague enquiries to ",
      titleHighlight: "prepared consultations",
      beforeLead: "Professionals spend billable time reconstructing the first call.",
      afterLead: "The right context is ready before the meeting begins.",
      before: [
        { icon: "phone", title: "Calls interrupt focus", detail: "Deep work stops for every enquiry" },
        { icon: "message", title: "Details stay vague", detail: "Reason and urgency are incomplete" },
        { icon: "briefcase", title: "Fit is unclear", detail: "Wrong matters reach wrong people" },
        { icon: "clock", title: "Deadlines hide", detail: "Urgent work waits in the queue" },
      ],
      after: [
        { icon: "message", title: "Reason captured", detail: "A concise matter summary is ready" },
        { icon: "clock", title: "Urgency identified", detail: "Important deadlines are surfaced" },
        { icon: "route", title: "Expertise matched", detail: "The right team receives the enquiry" },
        { icon: "calendar", title: "Consultation booked", detail: "Caller and advisor are aligned" },
      ],
    },
    sectionEyebrow: "Designed for trusted advice",
    sectionTitle: "Efficient intake without flattening a sensitive conversation.",
    sectionDescription:
      "Control which questions the agent asks, what it never advises on, and exactly when a call must move to a qualified professional.",
    pillars: [
      { eyebrow: "Intake", title: "Capture the facts that shape the next step", description: "Reason for contact, relevant dates, organization, urgency, and preferred consultation time arrive in a structured summary." },
      { eyebrow: "Boundaries", title: "Inform without giving professional advice", description: "Sauti can explain process and availability while keeping legal, financial, or specialist judgement with your team." },
      { eyebrow: "Routing", title: "Respect expertise and urgency", description: "Match the enquiry to the right practice area, location, or duty professional using your own rules." },
    ],
    systems: ["CRM delivery", "Consultation calendar", "Email summaries", "Priority transfer"],
    finalTitle: "Make the first conversation useful before it reaches your desk.",
    finalDescription: "Model one intake pathway with your team and hear how the boundaries work in a real call.",
  },
  {
    slug: "education",
    label: "Education",
    cardDescription: "Give every prospective learner a clear route from question to next step.",
    image: "/images/marketing/industries/education-hero.png",
    imageAlt: "An admissions advisor speaking with a prospective student and parent",
    variant: "campus",
    accent: "#67e8f9",
    heroPrefix: "Every learner has a different question. ",
    heroHighlight: "Give each one a clear next step.",
    heroSuffix: "",
    description:
      "Sauti answers approved admissions questions, captures applicant context, schedules callbacks, and routes enquiries to the right department.",
    proof: ["Admissions-ready", "Department routing", "Family-friendly"],
    call: {
      heading: "Admissions enquiry",
      callerLabel: "Prospective student",
      caller: "Do I need prior experience for the data programme?",
      agent: "No prior experience is required. Would you like an advisor to call you about the next intake?",
      outcome: "Advisor callback booked",
      outcomeDetail: "Tomorrow, 3:00 PM · Data programme",
      secondaryHeading: "Funding question",
      secondaryCaller: "Can someone explain the bursary requirements?",
      secondaryOutcome: "Routed to student finance",
    },
    transformation: {
      titlePrefix: "From repeated questions to ",
      titleHighlight: "confident applicant journeys",
      beforeLead: "Teams answer the same questions across busy intake periods.",
      afterLead: "Every learner gets an answer, owner, or callback.",
      before: [
        { icon: "phone", title: "Seasonal call spikes", detail: "Applicants wait during key dates" },
        { icon: "message", title: "Answers vary", detail: "Information depends on who picks up" },
        { icon: "building", title: "Departments bounce calls", detail: "Ownership is not obvious" },
        { icon: "user", title: "Context gets lost", detail: "Students repeat their story" },
      ],
      after: [
        { icon: "education", title: "Approved answers", detail: "Programmes and process explained" },
        { icon: "user", title: "Applicant context", detail: "Course, stage, and need captured" },
        { icon: "calendar", title: "Callback scheduled", detail: "A clear time replaces uncertainty" },
        { icon: "route", title: "Department matched", detail: "Admissions, finance, or support" },
      ],
    },
    sectionEyebrow: "Built around the learner journey",
    sectionTitle: "One voice across admissions, finance, and student support.",
    sectionDescription:
      "Give callers consistent approved information, then route the conversation according to programme, application stage, language, and department.",
    pillars: [
      { eyebrow: "Answer", title: "Make essential information easy to reach", description: "Programmes, entry requirements, deadlines, locations, and application steps stay available beyond office hours." },
      { eyebrow: "Understand", title: "Capture where the learner is in the journey", description: "Prospective student, applicant, parent, or current learner—each caller reaches a relevant next step." },
      { eyebrow: "Connect", title: "Route ownership, not just calls", description: "Book a callback or deliver the summary to admissions, finance, or support so follow-up starts with context." },
    ],
    systems: ["Admissions CRM", "Callback calendar", "Email summaries", "Department webhooks"],
    finalTitle: "Make every admissions call feel clear and considered.",
    finalDescription: "Start with your highest-volume enquiry type and shape the answers with the team that owns them.",
  },
  {
    slug: "local-businesses",
    label: "Local Businesses",
    cardDescription: "Keep earning attention after hours, on the road, and between jobs.",
    image: "/images/marketing/industries/local-businesses-hero.png",
    imageAlt: "A local service-business owner helping a customer in a modern workshop",
    variant: "field",
    accent: "#5eead4",
    heroPrefix: "You can’t stop the job every time the phone rings. ",
    heroHighlight: "You shouldn’t lose the lead either.",
    heroSuffix: "",
    description:
      "Sauti answers when you are busy, captures the job, checks service coverage, and turns after-hours calls into clear bookings or follow-up tasks.",
    proof: ["After-hours ready", "Job details captured", "Owner notified"],
    call: {
      heading: "New job request",
      callerLabel: "Customer",
      caller: "My boiler stopped working and I need someone tomorrow.",
      agent: "I can capture the job now. What area are you in, and is there any leak?",
      outcome: "Priority callback created",
      outcomeDetail: "Heating · No leak · Call by 8:30 AM",
      secondaryHeading: "Service-area check",
      secondaryCaller: "Do you cover the north side of town?",
      secondaryOutcome: "Area confirmed and visit requested",
    },
    transformation: {
      titlePrefix: "From missed calls to ",
      titleHighlight: "work-ready opportunities",
      beforeLead: "The phone rings when hands are full or the day is over.",
      afterLead: "Every caller leaves a useful, actionable next step.",
      before: [
        { icon: "wrench", title: "Team is on the job", detail: "Nobody can pause safely" },
        { icon: "phone", title: "Calls go to voicemail", detail: "Customers ring the next business" },
        { icon: "map", title: "Area stays unknown", detail: "Travel and fit checked too late" },
        { icon: "message", title: "Details are thin", detail: "Owner calls back from scratch" },
      ],
      after: [
        { icon: "headphones", title: "Calls answered", detail: "Even after the working day" },
        { icon: "wrench", title: "Job understood", detail: "Service, urgency, and symptoms captured" },
        { icon: "map", title: "Coverage checked", detail: "Area and preferred timing confirmed" },
        { icon: "bell", title: "Owner gets the next step", detail: "Booking or callback arrives ready" },
      ],
    },
    sectionEyebrow: "Built for work that happens away from a desk",
    sectionTitle: "A dependable phone presence for small teams doing real work.",
    sectionDescription:
      "Set service areas, opening hours, urgency rules, job categories, and callback promises so Sauti represents the business accurately.",
    pillars: [
      { eyebrow: "Capture", title: "Understand the job before the callback", description: "Service needed, location, symptoms, urgency, and contact details arrive together instead of in a vague voicemail." },
      { eyebrow: "Prioritize", title: "Separate emergencies from ordinary work", description: "Your rules decide what needs an immediate human response and what can wait for the next working window." },
      { eyebrow: "Follow through", title: "Give every lead a visible owner", description: "Book when the calendar allows, or create a clear follow-up task with the caller’s preferred time." },
    ],
    systems: ["Google Calendar", "Owner email", "Custom webhook", "Live transfer"],
    finalTitle: "Let the work continue without letting the opportunity disappear.",
    finalDescription: "Pilot Sauti on after-hours calls and see how much more useful the morning callback list becomes.",
  },
];

export function industryFor(slug: string) {
  return industries.find((industry) => industry.slug === slug) ?? null;
}
