import {
  Bot,
  CalendarCheck,
  Clock3,
  Globe2,
  PhoneCall,
  ShieldCheck,
  SquareActivity,
  Zap,
} from "lucide-react";

export const metrics = [
  { value: 24, suffix: "/7", label: "AI agents working non-stop", icon: Clock3 },
  { value: 800, prefix: "<", suffix: "ms", label: "Average response time", icon: Zap },
  { value: 30, suffix: "+", label: "Languages supported", icon: Globe2 },
  { value: 100, suffix: "%", label: "Data security and compliance", icon: ShieldCheck },
];

export const partners = ["twilio", "Deepgram", "Cartesia", "OpenAI", "Google Calendar"];

export const workflow = [
  { icon: PhoneCall, title: "Answers the call", text: "Natural greeting, understands who you are." },
  { icon: Bot, title: "Understands intent", text: "AI captures caller needs with precision." },
  { icon: CalendarCheck, title: "Checks availability", text: "Real-time calendar sync across systems." },
  { icon: SquareActivity, title: "Confirms and books", text: "Instant confirmation, reminders, and records." },
];

export const integrations = [
  { name: "Twilio", category: "Voice infrastructure", tone: "red", logo: "/logos/twilio.svg" },
  { name: "Deepgram", category: "Speech intelligence", tone: "teal", logo: "/logos/deepgram.svg" },
  { name: "Cartesia", category: "Realtime voice", tone: "slate", logo: "/logos/cartesia.svg" },
  { name: "OpenAI", category: "AI reasoning", tone: "green", logo: "/logos/openai.svg" },
  { name: "Google Calendar", category: "Scheduling", tone: "blue", logo: "/logos/google-calendar.svg" },
  { name: "HubSpot", category: "Customer platform", tone: "amber", logo: "/logos/hubspot.svg" },
  { name: "Salesforce", category: "CRM", tone: "blue", logo: "/logos/salesforce.svg" },
  { name: "Slack", category: "Team alerts", tone: "violet", logo: "/logos/slack.svg" },
  { name: "Zapier", category: "Automation", tone: "amber", logo: "/logos/zapier.svg" },
  { name: "Notion", category: "Knowledge base", tone: "slate", logo: "/logos/notion.svg" },
  { name: "Pipedrive", category: "Sales pipeline", tone: "green", logo: "/logos/pipedrive.svg", logoStyle: "wide" },
  { name: "Teams", category: "Collaboration", tone: "indigo", logo: "/logos/microsoft-teams.svg" },
];

export const faq = [
  ["Is Sauti PCI & HIPAA compliant?", "The platform is built with production controls: tenant isolation, signed webhooks, encrypted secrets, and audit-friendly call records."],
  ["Does it support multiple languages?", "Yes. The call pipeline detects language and routes STT, TTS, and response behavior for multilingual agents."],
  ["How does billing work?", "Plans can meter usage by call minutes, booked outcomes, and tenant subscription rules."],
  ["How is my data protected?", "Dashboard access uses JWTs, Twilio webhooks are signature validated, and outbound webhooks are HMAC signed."],
  ["Can I customize my AI agent?", "Yes. Each agent has configurable language, voice, prompt, greeting, schedule, transfer rules, and tools."],
  ["What happens when the AI cannot answer?", "The agent can escalate to a human, end safely, or route the call based on tenant-configured tools."],
];
