"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  ArrowRight,
  Bot,
  CalendarCheck,
  CheckCircle2,
  Clock,
  Globe,
  Moon,
  Sparkles,
} from "lucide-react";
import {
  allDestinations,
  destinationAccents,
  groupFor,
  menuGroups,
  pageFor,
  pricingPage,
  sectionPathFor,
} from "@/features/marketing/site-map";

type Destination = NonNullable<ReturnType<typeof import("@/features/marketing/site-map").pageFor>>;
type VisualDestination = Pick<Destination, "section" | "group" | "icon"> & {
  slug: string;
  eyebrow: string;
  title: string;
  description: string;
  href: string;
};

const proofBySection: Record<string, string[]> = {
  platform: ["Realtime call orchestration", "Configurable agents", "Production controls"],
  solutions: ["Caller-first workflows", "Calendar and tool actions", "Human escalation paths"],
  integrations: ["Signed webhooks", "Provider routing", "OpenAPI-ready contracts"],
  industries: ["Industry-specific prompts", "Business-hour routing", "Outcome analytics"],
  resources: ["Implementation guidance", "Security context", "Buyer-ready proof"],
};

const flowBySection: Record<string, string[]> = {
  platform: ["Configure the agent", "Connect call infrastructure", "Process turns live", "Measure every outcome"],
  solutions: ["Understand caller intent", "Run the right workflow", "Complete the action", "Sync the record"],
  integrations: ["Receive call event", "Choose provider or tool", "Sign and send payload", "Return result to agent"],
  industries: ["Model the front desk", "Apply business rules", "Route sensitive cases", "Review performance"],
  resources: ["Learn the concept", "Evaluate the controls", "Plan the rollout", "Launch the pilot"],
};

const detailBySlug: Record<string, { proof: string[]; title: string; text: string; flow: string[] }> = {
  "ai-voice-agent-platform": {
    proof: ["One platform for calls", "Agents with tenant tools", "Production-ready controls"],
    title: "The platform coordinates every moving part of an AI phone call.",
    text:
      "Sauti brings together call transport, speech, language intelligence, tool execution, booking logic, analytics, and tenant policy so teams can launch voice agents without stitching separate systems together.",
    flow: ["Create the tenant agent", "Attach voice and tool providers", "Run controlled live turns", "Record outcome and analytics"],
  },
  "live-call-pipeline": {
    proof: ["Twilio media events", "STT and TTS streaming", "Barge-in aware turns"],
    title: "The call pipeline keeps the conversation moving in real time.",
    text:
      "This page focuses on the inbound call loop: Twilio streams audio, speech is transcribed, the LLM decides the next action, tools run when needed, and speech is streamed back to the caller.",
    flow: ["Receive media frames", "Detect final utterance", "Process the agent turn", "Stream voice response"],
  },
  "agent-builder": {
    proof: ["Configurable voice", "Prompt and greeting controls", "Tool permissions per agent"],
    title: "Agent Builder turns business rules into a callable AI front desk.",
    text:
      "Admins configure the agent's language behavior, greeting, voice, schedule, system prompt, and allowed tools so the live call follows the tenant's operating rules.",
    flow: ["Define the agent identity", "Set prompt and greeting", "Choose voice and language", "Enable approved tools"],
  },
  "call-monitoring": {
    proof: ["Live call status", "Transcript visibility", "Outcome tracking"],
    title: "Call Monitoring gives operators a live view of what the agent is doing.",
    text:
      "Supervisors can follow active calls, see interruptions, inspect transcripts, watch booking and transfer events, and understand why a call ended the way it did.",
    flow: ["Track connection state", "Display live transcript", "Surface interruptions", "Record final disposition"],
  },
  analytics: {
    proof: ["Booking success rate", "Duration and sentiment", "Tenant-level usage"],
    title: "Analytics converts call activity into operational decisions.",
    text:
      "Sauti aggregates outcomes, duration, sentiment, booking performance, transfer rates, and usage so teams can improve prompts, staffing, and workflows.",
    flow: ["Collect call events", "Aggregate tenant metrics", "Calculate outcomes", "Report performance trends"],
  },
  "security-compliance": {
    proof: ["Tenant isolation", "Encrypted credentials", "Signed webhooks"],
    title: "Security controls protect call data, tools, and tenant boundaries.",
    text:
      "The platform validates inbound signatures, encrypts external credentials, signs outbound webhooks, isolates tenant configuration, and keeps auditable call records.",
    flow: ["Validate caller/webhook source", "Apply tenant policy", "Protect tool credentials", "Archive audit records"],
  },
  "appointment-booking": {
    proof: ["Availability before confirmation", "Calendar event creation", "Confirmation messages"],
    title: "Appointment Booking turns caller intent into a reserved slot.",
    text:
      "The agent gathers the service need, checks real availability, presents valid times, books the chosen slot, and triggers confirmations without double-booking.",
    flow: ["Understand requested service", "Check available slots", "Confirm selected time", "Create booking and notify"],
  },
  "customer-service": {
    proof: ["Policy-grounded answers", "Escalation when needed", "After-call summaries"],
    title: "Customer Service resolves common calls while keeping humans available for exceptions.",
    text:
      "Sauti answers routine questions, handles status and policy conversations, detects when confidence is low, and escalates with the call context attached.",
    flow: ["Classify the question", "Answer from approved policy", "Watch confidence", "Summarize or escalate"],
  },
  "lead-qualification": {
    proof: ["Intent capture", "Urgency scoring", "CRM-ready payloads"],
    title: "Lead Qualification captures the details sales teams usually lose on missed calls.",
    text:
      "The agent asks focused discovery questions, captures budget and urgency, identifies next steps, and sends structured lead data into the business workflow.",
    flow: ["Capture caller need", "Ask qualifying questions", "Score fit and urgency", "Send lead event"],
  },
  "human-transfer": {
    proof: ["Rule-based routing", "Warm context handoff", "Safe fallback path"],
    title: "Human Transfer moves sensitive calls to the right person with context.",
    text:
      "When a call needs a human, Sauti matches routing rules, prepares the summary, and either transfers live or schedules a callback if nobody is available.",
    flow: ["Detect transfer trigger", "Match routing policy", "Prepare summary", "Transfer or schedule"],
  },
  "multilingual-call-handling": {
    proof: ["Language detection", "Provider routing", "Natural interruption handling"],
    title: "Multilingual handling lets callers speak naturally in their preferred language.",
    text:
      "The call pipeline detects language, selects speech and voice behavior, and keeps the conversation natural across supported languages and accents.",
    flow: ["Detect caller language", "Route speech provider", "Respond in language", "Preserve transcript context"],
  },
  "after-hours-answering": {
    proof: ["Always-on coverage", "Callback capture", "Next-day follow-up"],
    title: "After-hours answering keeps the business responsive when the team is offline.",
    text:
      "Sauti answers outside working hours, captures caller needs, books available slots when allowed, or creates a clear follow-up task for the team.",
    flow: ["Answer after hours", "Capture caller need", "Book or collect callback", "Notify the team"],
  },
  "voice-infrastructure": {
    proof: ["Twilio webhooks", "Media stream control", "Authoritative status callbacks"],
    title: "Voice Infrastructure connects carrier calls to the Sauti agent runtime.",
    text:
      "This destination explains how inbound calls enter the system, how media streams are handled, and how Twilio status events update final duration, outcome, and recording references.",
    flow: ["Validate Twilio request", "Open media stream", "Track call lifecycle", "Persist final status"],
  },
  "speech-and-voice": {
    proof: ["Realtime STT", "Streaming TTS", "Provider fallback"],
    title: "Speech & Voice makes the agent hear and respond naturally.",
    text:
      "Sauti routes audio through speech-to-text and text-to-speech providers, handles silence and interruptions, and streams voice responses back without waiting for the whole turn to finish.",
    flow: ["Decode caller audio", "Transcribe utterance", "Generate speech chunks", "Stream response to Twilio"],
  },
  "ai-models": {
    proof: ["Tool-calling LLMs", "Prompt construction", "Conversation memory"],
    title: "AI Models decide what the agent should say or do next.",
    text:
      "The model receives tenant instructions, structured conversation history, available tools, and call context, then chooses whether to answer, check availability, book, transfer, or end safely.",
    flow: ["Build system prompt", "Send structured history", "Execute selected tools", "Return caller response"],
  },
  calendars: {
    proof: ["Google Calendar", "Calendly support", "Custom booking webhooks"],
    title: "Calendar integrations prevent the agent from promising impossible times.",
    text:
      "Sauti checks live availability before confirming a booking, creates calendar events after confirmation, and keeps tenant-specific scheduling rules in the call path.",
    flow: ["Read requested time", "Check calendar provider", "Reserve selected slot", "Sync booking result"],
  },
  "business-tools": {
    proof: ["CRM delivery", "Team notifications", "Workflow automation"],
    title: "Business Tools turn call outcomes into team follow-up.",
    text:
      "When a call produces a lead, booking, transfer, or support outcome, Sauti can send structured events to CRM, chat, no-code, or internal systems.",
    flow: ["Detect call outcome", "Prepare business payload", "Route to tool", "Confirm delivery"],
  },
  "developer-tools": {
    proof: ["REST API", "OpenAPI docs", "Signed webhooks"],
    title: "Developer Tools make Sauti extensible without weakening security.",
    text:
      "Developers can integrate tenant systems through documented APIs, signed webhook payloads, and controlled tool endpoints the agent can call during live conversations.",
    flow: ["Define endpoint contract", "Sign outbound request", "Receive tool result", "Audit integration event"],
  },
  "clinics-healthcare": {
    proof: ["Appointment intake", "Front-desk FAQs", "Sensitive escalation"],
    title: "Clinics use Sauti to keep patient access open without overloading the desk.",
    text:
      "The agent can answer appointment questions, collect patient intent, book or reschedule visits, and transfer sensitive calls to staff with a clear summary.",
    flow: ["Identify patient need", "Check clinic schedule", "Book or escalate", "Record patient-facing outcome"],
  },
  "salons-beauty": {
    proof: ["Service booking", "Reschedule handling", "Reminder workflows"],
    title: "Salons use Sauti to capture bookings while stylists stay focused.",
    text:
      "Sauti handles service questions, preferred times, stylist availability, reschedules, and confirmations so callers do not wait for someone to pick up.",
    flow: ["Capture service request", "Find open appointment", "Confirm preference", "Send booking reminder"],
  },
  "real-estate": {
    proof: ["Buyer qualification", "Viewing requests", "Lead routing"],
    title: "Real estate teams use Sauti to qualify interest at the moment callers reach out.",
    text:
      "The agent asks about property interest, budget, urgency, and viewing availability, then sends qualified lead details to the right person or system.",
    flow: ["Capture property interest", "Ask qualifying details", "Offer viewing slot", "Route lead summary"],
  },
  "professional-services": {
    proof: ["Consultation booking", "Intake questions", "Priority routing"],
    title: "Professional services teams use Sauti to turn inquiries into prepared consultations.",
    text:
      "The agent captures the reason for contact, asks intake questions, books consultations, and flags high-priority callers for human follow-up.",
    flow: ["Identify service need", "Collect intake details", "Schedule consultation", "Send team summary"],
  },
  education: {
    proof: ["Admissions answers", "Callback scheduling", "Department routing"],
    title: "Education teams use Sauti to answer common questions and route inquiries faster.",
    text:
      "The agent can answer program questions, collect applicant details, schedule callbacks, and route callers to admissions, finance, or support teams.",
    flow: ["Classify inquiry", "Answer approved FAQ", "Collect contact details", "Route department follow-up"],
  },
  "local-businesses": {
    proof: ["After-hours capture", "Booking requests", "Simple follow-up"],
    title: "Local businesses use Sauti to stop losing calls when nobody is available.",
    text:
      "Sauti answers routine calls, captures new opportunities, books appointments when possible, and leaves the team with a clear next action.",
    flow: ["Answer missed call", "Capture caller intent", "Book or create task", "Notify the owner"],
  },
  documentation: {
    proof: ["Setup guides", "Architecture notes", "Deployment context"],
    title: "Documentation explains how to configure, operate, and extend Sauti.",
    text:
      "Docs should help teams understand agent setup, call flow, tool configuration, provider routing, environment settings, and production rollout decisions.",
    flow: ["Understand concepts", "Configure environment", "Connect providers", "Launch safely"],
  },
  "api-reference": {
    proof: ["Endpoint contracts", "DTO examples", "Webhook payloads"],
    title: "API Reference gives developers the contracts needed to integrate cleanly.",
    text:
      "The API reference documents REST controllers, request and response models, webhook signatures, and integration payloads for tenant systems.",
    flow: ["Choose endpoint", "Review schema", "Send signed request", "Handle response"],
  },
  blog: {
    proof: ["Implementation patterns", "Voice AI guidance", "Product updates"],
    title: "The blog turns Sauti’s architecture into practical operating lessons.",
    text:
      "Articles can explain booking automation, language detection, call analytics, security decisions, and how real teams deploy AI phone agents.",
    flow: ["Explain a pattern", "Show implementation", "Share tradeoffs", "Apply to a workflow"],
  },
  "case-studies": {
    proof: ["Before and after", "Measured outcomes", "Workflow examples"],
    title: "Case Studies show how teams convert more calls with Sauti.",
    text:
      "Case studies should focus on missed-call reduction, booking lift, support deflection, response time, and operational lessons from real pilots.",
    flow: ["Describe problem", "Deploy workflow", "Measure results", "Share rollout lessons"],
  },
  faqs: {
    proof: ["Buyer questions", "Technical answers", "Launch clarity"],
    title: "FAQs remove common blockers before a team starts a pilot.",
    text:
      "The FAQ page should answer how Sauti handles compliance, billing, integrations, customization, fallback behavior, and support expectations.",
    flow: ["Surface concern", "Answer clearly", "Link proof", "Move buyer forward"],
  },
  security: {
    proof: ["Credential encryption", "Signature validation", "Tenant isolation"],
    title: "Security resources explain how Sauti protects live call operations.",
    text:
      "This page should help technical and compliance buyers understand encrypted tool credentials, Twilio validation, signed webhooks, and tenant-scoped records.",
    flow: ["Validate sources", "Protect secrets", "Isolate tenants", "Audit activity"],
  },
};

const visualModeBySlug: Record<string, string> = {
  "ai-voice-agent-platform": "platform",
  "live-call-pipeline": "pipeline",
  "agent-builder": "builder",
  "call-monitoring": "monitoring",
  analytics: "analytics",
  "security-compliance": "security",
  "appointment-booking": "booking",
  "customer-service": "support",
  "lead-qualification": "lead",
  "human-transfer": "transfer",
  "multilingual-call-handling": "language",
  "after-hours-answering": "after-hours",
  "voice-infrastructure": "voice",
  "speech-and-voice": "speech",
  "ai-models": "models",
  calendars: "calendar",
  "business-tools": "business-tools",
  "developer-tools": "developer",
  security: "security",
  documentation: "docs",
  "api-reference": "developer",
  faqs: "docs",
};

const categoryOutcomes: Record<string, { layout: string; title: string; description: string; metrics: string[]; achievement: string }> = {
  product: {
    layout: "system",
    title: "A complete AI phone agent platform, from first ring to final outcome.",
    description:
      "The product category explains how Sauti actually works: voice infrastructure, agent configuration, live turns, monitoring, analytics, and production controls.",
    metrics: ["Realtime", "call loop", "30+", "languages", "Signed", "webhooks"],
    achievement: "Build one controlled system for answering, understanding, acting, and measuring phone calls.",
  },
  solutions: {
    layout: "outcomes",
    title: "Business workflows that turn calls into booked actions.",
    description:
      "Solutions focus on what teams need from calls: appointments, support, qualification, safe transfer, multilingual handling, and after-hours coverage.",
    metrics: ["24/7", "coverage", "0", "missed intent", "Live", "handoffs"],
    achievement: "Convert caller intent into the right business outcome without forcing your team onto every call.",
  },
  integrations: {
    layout: "network",
    title: "Connect Sauti to the stack that already runs your business.",
    description:
      "Integrations show how voice, speech, AI models, calendars, CRMs, webhooks, and developer APIs work together in the call pipeline.",
    metrics: ["HMAC", "signed", "API", "ready", "Multi", "provider"],
    achievement: "Make every call event trigger a trustworthy action in your calendars, tools, and internal systems.",
  },
  industries: {
    layout: "verticals",
    title: "Industry-ready call agents shaped around real operating needs.",
    description:
      "Industry pages focus on how different teams configure prompts, tools, routing, and compliance posture for their day-to-day phone calls.",
    metrics: ["Custom", "prompts", "Policy", "routing", "Outcome", "tracking"],
    achievement: "Fit the same call intelligence engine to clinics, salons, real estate, services, education, and local businesses.",
  },
  resources: {
    layout: "library",
    title: "Resources that help buyers and builders evaluate Sauti clearly.",
    description:
      "Resources collect documentation, APIs, case studies, security information, and practical answers needed before launch.",
    metrics: ["Docs", "ready", "Security", "mapped", "Pilot", "guided"],
    achievement: "Give teams the evidence and implementation clarity they need before trusting AI with live calls.",
  },
};

export function DestinationScreen({ slug, section }: { slug: string; section: string }) {
  const destination = pageFor(section, slug);
  if (!destination) return null;
  const Icon = destination.icon;
  const accent = destinationAccents[destination.section as keyof typeof destinationAccents] ?? "booking";
  const visualMode = visualModeBySlug[destination.slug] ?? destination.section;
  const related = allDestinations()
    .filter((item) => item.section === destination.section && item.slug !== destination.slug)
    .slice(0, 3);
  const detail = detailBySlug[destination.slug] ?? {
    proof: proofBySection[destination.section] ?? proofBySection.platform,
    title: `${destination.title} is focused on a specific operational result.`,
    text: destination.description,
    flow: flowBySection[destination.section] ?? flowBySection.platform,
  };

  return (
    <main className={`product-page destination-page product-${accent} destination-${visualMode}`}>
      <section className="destination-hero">
        <div className="destination-copy">
          <div className="eyebrow">
            <Icon size={15} />
            {destination.group.label}
          </div>
          <h1>{destination.title}</h1>
          <p>{destination.description}</p>
          <div className="hero-actions">
            <Link className="solid-button large" href="https://cal.com/sauti/demo" target="_blank">
              Book a demo <ArrowRight size={18} />
            </Link>
            <Link className="demo-button large" href="/#dashboard">
              See live operations
            </Link>
          </div>
        </div>
        <DestinationVisual destination={destination} mode={visualMode} />
      </section>

      <section className="destination-proof">
        {detail.proof.map((item) => (
          <div key={item}>
            <CheckCircle2 size={20} />
            <span>{item}</span>
          </div>
        ))}
      </section>

      <section className="destination-section destination-split">
        <div>
          <span>{destination.eyebrow}</span>
          <h2>{detail.title}</h2>
          <p>{detail.text}</p>
        </div>
        <div className="destination-flow">
          {detail.flow.map((step, index) => (
            <article key={step}>
              <b>{String(index + 1).padStart(2, "0")}</b>
              <span>{step}</span>
            </article>
          ))}
        </div>
      </section>

      <section className="destination-section">
        <div className="product-section-heading">
          <span>Related destinations</span>
          <h2>Keep exploring this category.</h2>
        </div>
        <div className="destination-related-grid">
          {related.map((item) => {
            const RelatedIcon = item.icon;
            return (
              <Link href={item.href} key={item.slug}>
                <RelatedIcon size={23} />
                <strong>{item.title}</strong>
                <span>{item.description}</span>
              </Link>
            );
          })}
        </div>
      </section>

      <section className="product-page-cta">
        <span>Ready to test it?</span>
        <h2>Launch one agent, measure the outcome, then expand the workflow.</h2>
        <Link className="solid-button large" href="https://cal.com/sauti/demo" target="_blank">
          Start your pilot <ArrowRight size={18} />
        </Link>
      </section>
    </main>
  );
}

export function CategoryScreen({ section }: { section: string }) {
  const group = groupFor(section);
  if (!group) return null;
  const sectionPath = sectionPathFor(group);
  const contentKey = group.label.toLowerCase();
  const content = categoryOutcomes[contentKey] ?? categoryOutcomes.product;
  const accent = destinationAccents[section as keyof typeof destinationAccents] ?? "booking";
  const leadItems = group.items.slice(0, 3);
  const secondaryItems = group.items.slice(3);

  return (
    <main className={`product-page destination-page category-page category-${content.layout} product-${accent}`}>
      <section className="category-hero">
        <div className="category-copy">
          <div className="eyebrow">
            <Sparkles size={15} />
            {group.eyebrow}
          </div>
          <h1>{content.title}</h1>
          <p>{content.description}</p>
          <div className="hero-actions">
            <Link className="solid-button large" href={`/${sectionPath}/${group.items[0][0]}`}>
              Explore {group.label.toLowerCase()} <ArrowRight size={18} />
            </Link>
            <Link className="demo-button large" href="https://cal.com/sauti/demo" target="_blank">
              Book a demo
            </Link>
          </div>
        </div>
        <div className="category-achievement-card">
          <span>What this achieves</span>
          <h2>{content.achievement}</h2>
          <div className="category-metrics">
            {[0, 2, 4].map((index) => (
              <div key={content.metrics[index]}>
                <strong>{content.metrics[index]}</strong>
                <small>{content.metrics[index + 1]}</small>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="category-feature-band">
        {leadItems.map(([slug, title, description, Icon], index) => (
          <Link href={`/${sectionPath}/${slug}`} key={slug} className={`category-primary-card card-${index + 1}`}>
            <Icon size={28} />
            <span>{String(index + 1).padStart(2, "0")}</span>
            <h2>{title}</h2>
            <p>{description}</p>
          </Link>
        ))}
      </section>

      <section className="category-section-grid">
        <div className="category-narrative">
          <span>{group.label}</span>
          <h2>Each page is focused on the result it delivers.</h2>
          <p>
            These are not generic feature cards. Each destination explains the call outcome, system role, integrations, and
            operational value that matter for that category.
          </p>
        </div>
        <div className="category-secondary-list">
          {secondaryItems.map(([slug, title, description, Icon]) => (
            <Link href={`/${sectionPath}/${slug}`} key={slug}>
              <Icon size={22} />
              <div>
                <strong>{title}</strong>
                <span>{description}</span>
              </div>
              <ArrowRight size={17} />
            </Link>
          ))}
        </div>
      </section>

    </main>
  );
}

export function PricingScreen() {
  const Icon = pricingPage.icon;
  const plans = [
    ["Pilot", "Validate one phone workflow", "1 agent", "Call monitoring", "Calendar or webhook booking"],
    ["Growth", "Scale across teams and locations", "Multiple agents", "Advanced analytics", "CRM and webhook routing"],
    ["Enterprise", "Production controls for regulated teams", "Custom providers", "SLA and compliance review", "Dedicated rollout support"],
  ];

  return (
    <main className="product-page destination-page product-ops">
      <section className="destination-hero pricing-hero">
        <div className="destination-copy">
          <div className="eyebrow">
            <Icon size={15} />
            Pricing
          </div>
          <h1>{pricingPage.title}</h1>
          <p>{pricingPage.description}</p>
          <div className="hero-actions">
            <Link className="solid-button large" href="https://cal.com/sauti/demo" target="_blank">
              Talk to sales <ArrowRight size={18} />
            </Link>
          </div>
        </div>
        <DestinationVisual destination={{ section: "pricing", slug: "pricing", group: menuGroups[0], eyebrow: "Plans", title: "Usage based plans", description: pricingPage.description, icon: Icon, href: "/pricing" }} mode="pricing" />
      </section>
      <section className="pricing-grid">
        {plans.map(([name, description, ...features]) => (
          <article key={name}>
            <span>{name}</span>
            <h2>{description}</h2>
            {features.map((feature) => (
              <p key={feature}><CheckCircle2 size={17} /> {feature}</p>
            ))}
            <Link className="demo-button" href="https://cal.com/sauti/demo" target="_blank">Discuss plan</Link>
          </article>
        ))}
      </section>
    </main>
  );
}

function DestinationVisual({ destination, mode }: { destination: VisualDestination; mode: string }) {
  const Icon = destination.icon;
  const nodeItems = destination.group.items.slice(0, 4);

  // 1. Booking Simulation State
  const [bookingState, setBookingState] = useState<"checking" | "found" | "booking" | "confirmed">("checking");
  
  useEffect(() => {
    if (mode !== "booking") return;
    const timer1 = setTimeout(() => setBookingState("found"), 1200);
    const timer2 = setTimeout(() => setBookingState("booking"), 2400);
    const timer3 = setTimeout(() => setBookingState("confirmed"), 3600);
    
    const interval = setInterval(() => {
      setBookingState("checking");
      setTimeout(() => setBookingState("found"), 1200);
      setTimeout(() => setBookingState("booking"), 2400);
      setTimeout(() => setBookingState("confirmed"), 3600);
    }, 7000);
    
    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
      clearTimeout(timer3);
      clearInterval(interval);
    };
  }, [mode]);

  // 2. Support Simulation State
  const [supportStep, setSupportStep] = useState(0);
  
  useEffect(() => {
    if (mode !== "support") return;
    const interval = setInterval(() => {
      setSupportStep((prev) => (prev + 1) % 3);
    }, 3500);
    return () => clearInterval(interval);
  }, [mode]);

  // 3. Lead Qualification Simulation State
  const [score, setScore] = useState(0);
  const [itemIndex, setItemIndex] = useState(-1);
  
  useEffect(() => {
    if (mode !== "lead") return;
    let frame = 0;
    
    const runSimulation = () => {
      setItemIndex(-1);
      setScore(0);
      
      const t1 = setTimeout(() => setItemIndex(0), 800);
      const t2 = setTimeout(() => setItemIndex(1), 1600);
      const t3 = setTimeout(() => setItemIndex(2), 2400);
      const t4 = setTimeout(() => setItemIndex(3), 3200);
      
      let currentScore = 0;
      const countUp = () => {
        if (currentScore < 94) {
          currentScore += 2;
          setScore(currentScore);
          frame = requestAnimationFrame(countUp);
        }
      };
      const t5 = setTimeout(() => {
        frame = requestAnimationFrame(countUp);
      }, 3400);

      return () => {
        clearTimeout(t1);
        clearTimeout(t2);
        clearTimeout(t3);
        clearTimeout(t4);
        clearTimeout(t5);
      };
    };

    let cleanup = runSimulation();
    
    const interval = setInterval(() => {
      if (cleanup) cleanup();
      cancelAnimationFrame(frame);
      cleanup = runSimulation();
    }, 8500);

    return () => {
      if (cleanup) cleanup();
      clearInterval(interval);
      cancelAnimationFrame(frame);
    };
  }, [mode]);

  // 4. Human Transfer Simulation State
  const [transferState, setTransferState] = useState<"idle" | "caller-sauti" | "analyzing" | "sauti-operator" | "connected">("idle");
  
  useEffect(() => {
    if (mode !== "transfer") return;
    
    const runTransferFlow = () => {
      setTransferState("idle");
      const t1 = setTimeout(() => setTransferState("caller-sauti"), 800);
      const t2 = setTimeout(() => setTransferState("analyzing"), 2200);
      const t3 = setTimeout(() => setTransferState("sauti-operator"), 4000);
      const t4 = setTimeout(() => setTransferState("connected"), 5800);

      return () => {
        clearTimeout(t1);
        clearTimeout(t2);
        clearTimeout(t3);
        clearTimeout(t4);
      };
    };

    let cleanup = runTransferFlow();

    const interval = setInterval(() => {
      cleanup();
      cleanup = runTransferFlow();
    }, 9000);
    
    return () => {
      cleanup();
      clearInterval(interval);
    };
  }, [mode]);

  // 5. Multilingual Simulation State
  const [langIndex, setLangIndex] = useState(0);
  const languages = [
    { name: "Spanish", flag: "ES", text: "Hola, quiero reservar una cita.", translation: "Hello, I want to book an appointment.", reply: "¡Por supuesto!..." },
    { name: "French", flag: "FR", text: "Bonjour, puis-je réserver une heure ?", translation: "Hello, can I reserve a time?", reply: "Bien sûr!..." },
    { name: "Hindi", flag: "IN", text: "नमस्ते, क्या मैं अपॉइंटमेंट ले सकता हूँ?", translation: "Hello, can I take an appointment?", reply: "बिल्कुल!..." },
    { name: "Swahili", flag: "TZ", text: "Habari, naomba kuweka nafasi.", translation: "Hello, I would like to book a slot.", reply: "Hakika!..." }
  ];
  
  useEffect(() => {
    if (mode !== "language") return;
    const interval = setInterval(() => {
      setLangIndex((prev) => (prev + 1) % languages.length);
    }, 4000);
    return () => clearInterval(interval);
  }, [mode, languages.length]);

  // 6. After-Hours Answering Simulation State
  const [timeText, setTimeText] = useState("11:42 PM");
  const [capturedLog, setCapturedLog] = useState<string[]>([]);
  
  useEffect(() => {
    if (mode !== "after-hours") return;
    
    const logs = [
      "10:15 PM — Inbound call answered",
      "10:17 PM — Reschedule completed (Jun 24, 1:00 PM)",
      "11:40 PM — Inbound call answered",
      "11:42 PM — Qualified lead captured (Urgent extraction)"
    ];
    
    const runAfterHoursFlow = () => {
      setCapturedLog([logs[0], logs[1]]);
      setTimeText("10:17 PM");
      
      const t1 = setTimeout(() => {
        setTimeText("11:40 PM");
        setCapturedLog(prev => [...prev, logs[2]]);
      }, 2500);
      
      const t2 = setTimeout(() => {
        setTimeText("11:42 PM");
        setCapturedLog(prev => [...prev, logs[3]]);
      }, 4000);

      return () => {
        clearTimeout(t1);
        clearTimeout(t2);
      };
    };

    let cleanup = runAfterHoursFlow();
    
    const interval = setInterval(() => {
      cleanup();
      cleanup = runAfterHoursFlow();
    }, 8500);
    
    return () => {
      cleanup();
      clearInterval(interval);
    };
  }, [mode]);

  if (mode === "speech") {
    return (
      <div className="destination-visual-card visual-speech">
        <VisualHeader section={destination.group.label} />
        <div className="speech-wave-card">
          <span>Inbound audio</span>
          <div className="speech-wave">
            {Array.from({ length: 26 }).map((_, index) => (
              <i key={index} style={{ animationDelay: `${index * 55}ms` }} />
            ))}
          </div>
        </div>
        <div className="speech-routing-grid">
          {[
            ["Language", "Auto detected"],
            ["STT provider", "Deepgram / Intron"],
            ["TTS voice", "Cartesia"],
            ["Output", "Twilio μ-law stream"],
          ].map(([label, value]) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (mode === "voice") {
    return (
      <div className="destination-visual-card visual-voice">
        <VisualHeader section={destination.group.label} />
        <div className="voice-call-path">
          {[
            ["Inbound call", "Twilio webhook"],
            ["Media stream", "WebSocket audio"],
            ["Status callback", "Duration + outcome"],
          ].map(([label, value], index) => (
            <div key={label} className={`voice-path-node voice-path-${index + 1}`}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
        <div className="voice-sid-card">
          <span>Call SID</span>
          <strong>CA_82f...live</strong>
          <small>Signature validated</small>
        </div>
      </div>
    );
  }

  if (mode === "pipeline") {
    return (
      <div className="destination-visual-card visual-pipeline">
        <VisualHeader section={destination.group.label} />
        <div className="pipeline-lanes">
          {["Twilio stream", "Speech event", "LLM turn", "Voice reply"].map((label, index) => (
            <div key={label} style={{ animationDelay: `${index * 140}ms` }}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <strong>{label}</strong>
            </div>
          ))}
        </div>
        <div className="pipeline-signal" />
      </div>
    );
  }

  if (mode === "models") {
    return (
      <div className="destination-visual-card visual-models">
        <VisualHeader section={destination.group.label} />
        <div className="model-router">
          <div className="model-context">
            <span>Prompt context</span>
            <strong>Agent config + caller history</strong>
          </div>
          <div className="model-core">
            <Bot size={34} />
            <strong>LLM router</strong>
            <small>GPT-4o / Gemini / Spring AI</small>
          </div>
          <div className="model-output">
            <span>Streaming response</span>
            <strong>Answer, tool call, or handoff</strong>
          </div>
        </div>
        <div className="model-tool-strip">
          {["check_availability", "book_slot", "transfer_to_human"].map((tool) => (
            <span key={tool}>{tool}</span>
          ))}
        </div>
      </div>
    );
  }

  if (mode === "builder") {
    return (
      <div className="destination-visual-card visual-builder">
        <VisualHeader section={destination.group.label} />
        <div className="builder-panel">
          {["Language", "Voice", "Greeting", "Tools"].map((label, index) => (
            <label key={label}>
              <span>{label}</span>
              <b>{index === 0 ? "Auto detect" : index === 1 ? "Warm voice" : index === 2 ? "Tenant prompt" : "Calendar + webhook"}</b>
            </label>
          ))}
        </div>
      </div>
    );
  }

  if (mode === "monitoring") {
    return (
      <div className="destination-visual-card visual-monitor">
        <VisualHeader section={destination.group.label} />
        <div className="monitor-rows">
          {["Call connected", "Intent detected", "Answer delivered", "Outcome recorded"].map((label, index) => (
            <div key={label}>
              <i />
              <strong>{label}</strong>
              <span>{index === 3 ? "Complete" : "Live"}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (mode === "booking") {
    return (
      <div className="destination-visual-card visual-booking">
        <VisualHeader section={destination.group.label} />
        <div className="calendar-sync-layout">
          <div className="calendar-mini-header">
            <span>Select Time Slot</span>
            <strong>June 2025</strong>
          </div>
          <div className="calendar-mini-board">
            {["09:00 AM", "10:30 AM", "01:30 PM", "03:00 PM"].map((time, index) => {
              let slotClass = "";
              let slotLabel = "Available";
              if (index === 1) {
                if (bookingState === "checking") {
                  slotClass = "checking-slot";
                  slotLabel = "Checking...";
                } else if (bookingState === "found") {
                  slotClass = "found-slot";
                  slotLabel = "Selected";
                } else if (bookingState === "booking") {
                  slotClass = "booking-slot";
                  slotLabel = "Reserving...";
                } else if (bookingState === "confirmed") {
                  slotClass = "confirmed-slot";
                  slotLabel = "Booked";
                }
              } else if (index === 2) {
                slotClass = "disabled-slot";
                slotLabel = "Blocked";
              }
              return (
                <div key={time} className={`mini-time-slot ${slotClass}`}>
                  <strong>{time}</strong>
                  <span>{slotLabel}</span>
                </div>
              );
            })}
          </div>
          {bookingState === "confirmed" && (
            <div className="sms-alert slide-up-sms">
              <CalendarCheck size={16} className="sms-icon" />
              <div>
                <strong>Appointment Booked</strong>
                <span>Confirmation text sent to caller</span>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  if (mode === "support") {
    return (
      <div className="destination-visual-card visual-support">
        <VisualHeader section={destination.group.label} />
        <div className="support-ticket-container">
          <div className="support-bubble caller-bubble">
            <span className="bubble-speaker">Caller</span>
            <p>Do you charge fees for rescheduling?</p>
          </div>
          {supportStep >= 1 && (
            <div className="support-bubble agent-bubble">
              <span className="bubble-speaker">Sauti AI</span>
              <p>Rescheduling is free if requested more than 24 hours prior. Within 24 hours, a $25 fee applies.</p>
            </div>
          )}
          {supportStep >= 2 && (
            <div className="support-status-row fade-in-status">
              <span className="confidence-pill">99.4% Confidence</span>
              <span className="resolved-badge">Auto Resolved ✅</span>
            </div>
          )}
        </div>
      </div>
    );
  }

  if (mode === "lead") {
    return (
      <div className="destination-visual-card visual-lead">
        <VisualHeader section={destination.group.label} />
        <div className="lead-qualification-container">
          <div className="lead-gauge-container">
            <div className="lead-gauge-ring" style={{ '--score-percentage': `${score}%` } as React.CSSProperties}>
              <div className="lead-gauge-inner">
                <strong>{score}%</strong>
                <span>Fit Score</span>
              </div>
            </div>
          </div>
          <div className="lead-attributes-grid">
            {[
              ["Interest", "Reschedule Cleaning", 0],
              ["Urgency", "High (Tooth Pain)", 1],
              ["Insurance", "Delta Dental", 2],
              ["Action", "Auto Rescheduled", 3],
            ].map(([label, value, idx]) => (
              <div key={label as string} className={`lead-attribute-card ${Number(idx) <= itemIndex ? "revealed" : "hidden"}`}>
                <span>{label as string}</span>
                <strong>{value as string}</strong>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (mode === "transfer") {
    return (
      <div className="destination-visual-card visual-transfer">
        <VisualHeader section={destination.group.label} />
        <div className="transfer-flow-container">
          <div className="transfer-flow-nodes">
            <div className={`transfer-node-circle caller ${transferState !== "idle" ? "active" : ""}`}>
              <span>Caller</span>
            </div>
            
            <div className="flow-path-connector path-1">
              <div className={`flow-pulse-dot ${transferState === "caller-sauti" ? "pulsing" : ""}`} />
            </div>

            <div className={`transfer-node-circle sauti ${["analyzing", "sauti-operator", "connected"].includes(transferState) ? "active" : ""}`}>
              <span>Sauti AI</span>
            </div>

            <div className="flow-path-connector path-2">
              <div className={`flow-pulse-dot ${transferState === "sauti-operator" ? "pulsing" : ""}`} />
            </div>

            <div className={`transfer-node-circle operator ${transferState === "connected" ? "active" : ""}`}>
              <span>Staff</span>
            </div>
          </div>

          {transferState === "analyzing" && (
            <div className="handoff-card slide-up-handoff">
              <div className="handoff-header">
                <strong>Warm Handoff Summary</strong>
                <span>Drafting context...</span>
              </div>
              <p>Patient needs dentist cleaning; urgent reschedule to Tuesday. Delta Dental confirmed.</p>
            </div>
          )}

          {transferState === "connected" && (
            <div className="handoff-card slide-up-handoff success">
              <div className="handoff-header">
                <strong>Handoff Complete</strong>
                <span className="live-badge">Connected</span>
              </div>
              <p>Call routed to front-desk queue. Full history sync completed.</p>
            </div>
          )}
        </div>
      </div>
    );
  }

  if (mode === "language") {
    return (
      <div className="destination-visual-card visual-language">
        <VisualHeader section={destination.group.label} />
        <div className="language-translation-container">
          <div className="language-nodes-ring">
            <div className="lang-center-orb">
              <Globe size={24} className="spinning-globe" />
              <span>Detect</span>
            </div>
            {languages.map((lang, idx) => (
              <div key={lang.name} className={`lang-ring-node lang-${idx} ${langIndex === idx ? "active" : ""}`}>
                <strong>{lang.flag}</strong>
                <span>{lang.name}</span>
              </div>
            ))}
          </div>
          <div className="language-log-card">
            <div className="lang-log-header">
              <span>Speech Input</span>
              <strong>{languages[langIndex].name}</strong>
            </div>
            <p className="log-text spoken">&ldquo;{languages[langIndex].text}&rdquo;</p>
            <div className="lang-log-divider" />
            <div className="lang-log-header">
              <span>Sauti Translation</span>
              <strong>English</strong>
            </div>
            <p className="log-text translation">&ldquo;{languages[langIndex].translation}&rdquo;</p>
          </div>
        </div>
      </div>
    );
  }

  if (mode === "after-hours") {
    return (
      <div className="destination-visual-card visual-after-hours">
        <VisualHeader section={destination.group.label} />
        <div className="after-hours-container">
          <div className="night-status-header">
            <div className="night-clock-card">
              <Clock size={16} className="clock-icon" />
              <strong>{timeText}</strong>
            </div>
            <div className="night-active-pill">
              <Moon size={14} className="glowing-moon" />
              <span>Always-On Answering</span>
            </div>
          </div>
          <div className="after-hours-feed">
            <strong>Closed Hours Operations Log</strong>
            <div className="log-rows-container">
              {capturedLog.map((log, idx) => (
                <div key={idx} className="after-hours-log-row animate-log-row">
                  <span className="log-dot-indicator" />
                  <p>{log}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (mode === "business-tools") {
    return (
      <div className="destination-visual-card visual-business-tools">
        <VisualHeader section={destination.group.label} />
        <div className="business-hub">
          <div className="business-center">Sauti event</div>
          {["HubSpot", "Salesforce", "Slack", "Pipedrive", "Notion", "Zapier"].map((tool, index) => (
            <span key={tool} className={`business-tool tool-${index + 1}`}>{tool}</span>
          ))}
        </div>
        <div className="business-event-row">
          <span>lead.qualified</span>
          <strong>Delivered to CRM + team channel</strong>
        </div>
      </div>
    );
  }

  if (mode === "developer") {
    return (
      <div className="destination-visual-card visual-developer">
        <VisualHeader section={destination.group.label} />
        <div className="developer-code-card">
          <span>POST /webhooks/tools</span>
          <pre>{`{
  "event": "booking.created",
  "signature": "hmac-sha256",
  "tenantId": "tenant_..."
}`}</pre>
        </div>
        <div className="developer-contracts">
          {["OpenAPI schema", "Signed payloads", "Tool result DTO"].map((item) => (
            <div key={item}>{item}</div>
          ))}
        </div>
      </div>
    );
  }

  if (mode === "calendar") {
    return (
      <div className="destination-visual-card visual-calendar-sync">
        <VisualHeader section={destination.group.label} />
        <div className="calendar-sync-layout">
          <div className="calendar-month">
            {Array.from({ length: 21 }).map((_, index) => (
              <span key={index} className={index === 10 ? "active-day" : index === 15 ? "booked-day" : ""}>
                {index + 1}
              </span>
            ))}
          </div>
          <div className="calendar-provider-list">
            {["Google Calendar", "Calendly", "Custom webhook"].map((provider, index) => (
              <div key={provider}>
                <span>{index === 0 ? "Primary" : index === 1 ? "Fallback" : "Tenant API"}</span>
                <strong>{provider}</strong>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (mode === "security" || mode === "developer") {
    return (
      <div className="destination-visual-card visual-security">
        <VisualHeader section={destination.group.label} />
        <div className="security-stack">
          {["Signed webhook", "Encrypted secret", "Tenant boundary", "Audit record"].map((label) => (
            <div key={label}>
              <Icon size={20} />
              <span>{label}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="destination-visual-card">
      <VisualHeader section={destination.group.label} />
      <div className="destination-main-orb">
        <Icon size={42} />
        <strong>{destination.title.split(" ").slice(0, 2).join(" ")}</strong>
      </div>
      <div className="destination-node-grid">
        {nodeItems.map(([slug, title, , NodeIcon]) => (
          <div key={slug}>
            <NodeIcon size={18} />
            <span>{title}</span>
          </div>
        ))}
      </div>
    </div>
  );
}



function VisualHeader({ section }: { section: string }) {
  return (
    <div className="destination-visual-top">
      <span>{section}</span>
      <b><span className="status-dot" /> Ready</b>
    </div>
  );
}
