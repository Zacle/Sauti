"use client";

import Image from "next/image";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useState } from "react";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  ArrowRight,
  AudioLines,
  BarChart3,
  BookOpenCheck,
  CalendarCheck,
  Check,
  ChevronDown,
  Clock3,
  DatabaseZap,
  Languages,
  LockKeyhole,
  MessageCircleMore,
  Play,
  ShieldCheck,
  Sparkles,
  UserRoundCheck,
  Workflow,
} from "lucide-react";
import styles from "./ReferenceHome.module.css";

const HeroMotionOverlay = dynamic(
  () => import("./HeroMotionOverlay").then((module) => module.HeroMotionOverlay),
  { ssr: false },
);

const outcomes = [
  { value: "42%", label: "more calls answered", detail: "Day and night, without busy signals." },
  { value: "31%", label: "lower operating costs", detail: "Automate routine customer conversations." },
  { value: "4.8★", label: "average customer rating", detail: "Natural interactions that build loyalty." },
];

const conversationSteps: Array<[LucideIcon, string, string]> = [
  [MessageCircleMore, "Caller intent", "Understands what the caller needs."],
  [BookOpenCheck, "Knowledge lookup", "Finds the right answer in your resources."],
  [CalendarCheck, "Takes action", "Books, updates, or creates what is needed."],
  [DatabaseZap, "Updates systems", "Syncs with your CRM and approved tools."],
  [Check, "Confirms with care", "Shares confirmation and clear next steps."],
];

const capabilities: Array<[LucideIcon, string, string]> = [
  [AudioLines, "Understand naturally", "Advanced voice AI understands accents, context, and intent—so every call feels easy and human."],
  [LockKeyhole, "Take action safely", "Built-in guardrails and explicit permissions ensure Sauti takes only approved actions."],
  [Clock3, "Stay available", "Cover calls and browser conversations around the clock without making customers wait."],
  [BarChart3, "Improve continuously", "Every conversation becomes reviewable insight for better agents, workflows, and outcomes."],
];

const industries = [
  {
    title: "Healthcare",
    text: "Reduce admin calls and improve patient access.",
    image: "/images/marketing/industries/healthcare.png",
    href: "/industries/clinics-healthcare",
  },
  {
    title: "Professional services",
    text: "Capture enquiries, qualify leads, and coordinate consultations.",
    image: "/images/marketing/industries/professional-services.png",
    href: "/industries/professional-services",
  },
  {
    title: "Home services",
    text: "Book jobs, dispatch teams, and keep customers informed.",
    image: "/images/marketing/industries/home-services.png",
    href: "/industries/local-businesses",
  },
  {
    title: "Retail & ecommerce",
    text: "Answer product, order, and availability questions.",
    image: "/images/marketing/industries/retail.png",
    href: "/industries/local-businesses",
  },
  {
    title: "Education",
    text: "Support admissions, scheduling, and multilingual enquiries.",
    image: "/images/marketing/industries/education.png",
    href: "/industries/education",
  },
];

const languageSamples = {
  English: ["Hello, how can I help today?", "I’d like to book a consultation.", "Friday at 10:00 is available."],
  Kiswahili: ["Habari, ninaweza kukusaidiaje?", "Ningependa kuweka miadi.", "Ijumaa saa nne inapatikana."],
  Français: ["Bonjour, comment puis-je vous aider ?", "Je souhaite prendre rendez-vous.", "Vendredi à 10 h est disponible."],
} as const;

type Language = keyof typeof languageSamples;

const integrations = [
  ["Google Calendar", "/logos/google-calendar.svg"],
  ["HubSpot", "/logos/hubspot.svg"],
  ["Salesforce", "/logos/salesforce.svg"],
  ["Slack", "/logos/slack.svg"],
  ["Google Sheets", "/logos/google-sheets.svg"],
  ["Zapier", "/logos/zapier.svg"],
  ["WhatsApp", "/logos/whatsapp.svg"],
] as const;

const trustItems: Array<[LucideIcon, string, string]> = [
  [ShieldCheck, "Isolated workspaces", "Your data stays in your environment."],
  [LockKeyhole, "Encrypted credentials", "Stored securely and never exposed."],
  [Workflow, "Explicit permissions", "Agents act only within approved boundaries."],
  [UserRoundCheck, "Human handoff", "Escalation paths keep your team available."],
  [Activity, "Reviewable activity", "Every action is logged and auditable."],
];

const faq = [
  ["How quickly can I get started?", "Create an agent, configure its behavior and tools, then test it from your browser before enabling a customer channel."],
  ["Is my data secure?", "Workspace data is tenant-scoped, provider credentials are encrypted, and agents only use integrations explicitly enabled for them."],
  ["What if Sauti cannot handle a request?", "Configure clear transfer and escalation rules so sensitive or out-of-scope calls reach the right person."],
  ["Can I customize Sauti for my business?", "Yes. Set the agent identity, languages, knowledge, voice, business rules, tools, and post-call workflows."],
] as const;

export default function ReferenceHome() {
  const [language, setLanguage] = useState<Language>("English");
  const [period, setPeriod] = useState<"week" | "month">("month");

  return (
    <main className={styles.page} data-motion-page>
      <section className={styles.hero} data-hero-parallax>
        <div className={styles.heroMedia} aria-hidden="true">
          <Image
            src="/images/marketing/sauti-phone-hero.png"
            alt=""
            fill
            priority
            quality={100}
            unoptimized
            sizes="100vw"
          />
        </div>
        <div className={styles.heroShade} aria-hidden="true" />
        <HeroMotionOverlay />
        <div className={styles.heroCopy} data-reveal>
          <span className={styles.eyebrow}><Sparkles size={14} /> Sauti living voice system</span>
          <h1>Your best conversations never go unanswered.</h1>
          <p>AI voice agents that understand naturally, take action safely, and deliver real outcomes—day and night.</p>
          <div className={styles.actions}>
            <Link href="/register" className={styles.primary}>Get started <ArrowRight size={16} /></Link>
            <a href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer" className={styles.secondary}><Play size={15} /> Watch the demo</a>
          </div>
          <div className={styles.heroSignals}>
            <span><i /> Voice online</span>
            <span><Check size={13} /> 98.7% intent understood</span>
            <span><Clock3 size={13} /> &lt; 1.2s response time</span>
          </div>
        </div>

        <div className={styles.voiceRibbon} data-reveal>
          <RibbonStep icon={MessageCircleMore} label="Caller" text="I need a service appointment" />
          <RibbonStep icon={BookOpenCheck} label="Sauti" text="Checking availability" />
          <RibbonStep icon={CalendarCheck} label="Action" text="Book appointment" />
          <RibbonStep icon={DatabaseZap} label="Action" text="Update CRM" />
          <RibbonStep icon={Check} label="Sauti" text="You’re all set" />
        </div>
      </section>

      <section className={styles.outcomes} data-reveal-scale>
        <div className={styles.sectionIntro}>
          <span>Outcomes that matter</span>
          <h2>Real impact. Measurable growth.</h2>
        </div>
        <div className={styles.outcomeGrid}>
          {outcomes.map((item) => (
            <div key={item.label}>
              <strong>{item.value}</strong>
              <span>{item.label}</span>
              <small>{item.detail}</small>
            </div>
          ))}
        </div>
        <div className={styles.outcomePromise}>
          <AudioLines size={35} />
          <p>Sauti turns every conversation into action—so your business runs smoother and your customers keep moving.</p>
        </div>
      </section>

      <section className={styles.conversation}>
        <SectionHeading
          kicker="See Sauti think and act"
          title="From conversation to outcome."
          text=""
          dark
        />
        <div className={styles.flow} data-reveal>
          {conversationSteps.map(([Icon, title, text], index) => (
            <article key={title}>
              <div><Icon size={21} /></div>
              <span>{index + 1}. {title}</span>
              <p>{text}</p>
              {index < conversationSteps.length - 1 ? <ArrowRight className={styles.flowArrow} size={15} /> : null}
            </article>
          ))}
        </div>
        <div className={styles.flowDialogue} data-reveal>
          <Image
            className={styles.dialogueWaveform}
            src="/images/marketing/conversation-waveform.png"
            alt=""
            fill
            sizes="100vw"
          />
          <p>I need to reschedule my cleaning.</p>
          <p>Sure! I can help with that. What date works for you?</p>
          <p>How about Friday morning?</p>
          <p>You’re all set for Friday at 10:00 AM. See you then!</p>
        </div>
      </section>

      <section className={styles.capabilities}>
        <div className={styles.capabilityCore} aria-hidden="true"><AudioLines size={52} /></div>
        {capabilities.map(([Icon, title, text], index) => (
          <article key={title} data-reveal style={{ transitionDelay: `${index * 70}ms` }}>
            <Icon size={27} />
            <div><h3>{title}</h3><p>{text}</p></div>
          </article>
        ))}
      </section>

      <section className={styles.industries}>
        <SectionHeading
          kicker="Built for how you work"
          title="AI voice agents for every industry."
          text="Start with one customer journey, then expand across your operation."
          dark
          action={{ label: "View all solutions", href: "/industries" }}
        />
        <div className={styles.industryGrid}>
          {industries.map((industry, index) => (
            <Link href={industry.href} key={industry.title} data-reveal style={{ transitionDelay: `${index * 55}ms` }}>
              <Image
                src={industry.image}
                alt=""
                fill
                quality={94}
                sizes="(max-width: 760px) 100vw, 20vw"
              />
              <div><span>0{index + 1}</span><h3>{industry.title}</h3><p>{industry.text}</p><small>Learn more <ArrowRight size={13} /></small></div>
            </Link>
          ))}
        </div>
      </section>

      <section className={styles.languagesSection}>
        <div className={styles.languageCopy} data-reveal-left>
          <span>Speak every customer’s language</span>
          <h2>One experience.<br />Many languages.</h2>
          <p>Configure the languages each agent supports and keep every interaction natural and on brand.</p>
          <div className={styles.languageCascade} aria-label="Example supported languages">
            <strong>English</strong><strong>Kiswahili</strong><strong>Français</strong>
            <strong>Português</strong><strong>Türkçe</strong><strong>العربية</strong>
          </div>
        </div>
        <div className={styles.languageDemo} data-reveal-right>
          <div className={styles.languageTabs} role="tablist" aria-label="Conversation language">
            {(Object.keys(languageSamples) as Language[]).map((item) => (
              <button
                key={item}
                type="button"
                role="tab"
                aria-selected={language === item}
                className={language === item ? styles.active : ""}
                onClick={() => setLanguage(item)}
              >
                {item}
              </button>
            ))}
          </div>
          <div className={styles.languageTranscript}>
            <p><span>Sauti</span>{languageSamples[language][0]}</p>
            <p><span>Caller</span>{languageSamples[language][1]}</p>
            <p><span>Sauti</span>{languageSamples[language][2]}</p>
          </div>
          <footer><Languages size={16} /> {language} conversation <AudioLines size={24} /></footer>
        </div>
      </section>

      <section className={styles.integrations}>
        <div data-reveal-left>
          <span>Connect what you use</span>
          <h2>Works where your business runs.</h2>
          <p>Sauti connects calls to the calendars, CRMs, messaging, and automation tools your team already relies on.</p>
          <Link href="/integrations">View all integrations <ArrowRight size={14} /></Link>
        </div>
        <div className={styles.integrationFlow} data-reveal-right aria-label="Connected integrations">
          {integrations.map(([name, logo], index) => (
            <div key={name} className={name === "WhatsApp" ? styles.integrationWide : ""}>
              <Image src={logo} alt="" width={34} height={34} />
              <span>{name}</span>
              {index < integrations.length - 1 && name !== "WhatsApp" ? <ArrowRight size={14} aria-hidden="true" /> : null}
            </div>
          ))}
        </div>
      </section>

      <section className={styles.analytics}>
        <div data-reveal-left>
          <span>Insights that drive growth</span>
          <h2>See every conversation. Drive every decision.</h2>
          <p>Review volume, outcomes, responsiveness, and agent actions without losing the context behind the numbers.</p>
          <Link href="/analytics">Explore analytics <ArrowRight size={14} /></Link>
        </div>
        <div className={styles.analyticsPanel} data-reveal-right>
          <header>
            <div><strong>Conversation performance</strong><small>{period === "month" ? "Last 30 days" : "Last 7 days"}</small></div>
            <div><button className={period === "week" ? styles.active : ""} onClick={() => setPeriod("week")}>7 days</button><button className={period === "month" ? styles.active : ""} onClick={() => setPeriod("month")}>30 days</button></div>
          </header>
          <div className={styles.analyticsMetrics}>
            <Metric label="Conversations" value={period === "month" ? "12,842" : "3,108"} change="+18%" />
            <Metric label="Completion rate" value="87%" change="+9%" />
            <Metric label="Average handle time" value="2:14" change="-21%" />
            <Metric label="Caller rating" value="4.8 / 5" change="+0.6" />
          </div>
          <div className={styles.insightRows}>
            <p><span>Bookings completed</span><progress value={82} max={100} /><strong>82%</strong></p>
            <p><span>Questions resolved</span><progress value={74} max={100} /><strong>74%</strong></p>
            <p><span>Human handoffs</span><progress value={16} max={100} /><strong>16%</strong></p>
          </div>
        </div>
      </section>

      <section className={styles.trust}>
        <SectionHeading kicker="Built for trust" title="Enterprise-grade security. Human when it matters." text="Clear controls protect your data, your customers, and the actions every agent can take." dark />
        <div className={styles.trustGrid}>
          {trustItems.map(([Icon, title, text], index) => (
            <article key={title} data-reveal style={{ transitionDelay: `${index * 50}ms` }}>
              <Icon size={20} /><div><strong>{title}</strong><p>{text}</p></div>
            </article>
          ))}
        </div>
        <div className={styles.trustProof}><span>SOC 2-ready controls</span><span>Encrypted in transit and at rest</span><span>Role-based access</span><span>Human handoff anytime</span></div>
      </section>

      <section className={styles.plansFaq}>
        <div className={styles.planIntro} data-reveal>
          <span>Simple, transparent pricing</span>
          <h2>Start free. Scale when you’re ready.</h2>
          <p>Get started in minutes. No credit card required.</p>
          <div className={styles.actions}><Link href="/register" className={styles.primary}>Get started <ArrowRight size={15} /></Link><a href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer" className={styles.planLink}>Talk to sales</a></div>
        </div>
        <div className={styles.planCard} data-reveal>
          <small>Starter</small><strong>Free</strong>
          <span><Check size={14} /> 100 conversations / month</span>
          <span><Check size={14} /> 1 voice agent</span>
          <span><Check size={14} /> Basic integrations</span>
          <Link href="/register">Get started</Link>
        </div>
        <div className={styles.faq} data-reveal>
          <span>Frequently asked questions</span>
          {faq.map(([question, answer]) => <Faq key={question} question={question} answer={answer} />)}
        </div>
      </section>

      <section className={styles.closingCta}>
        <div data-reveal>
          <span>Ready to transform conversations?</span>
          <h2>Let’s build exceptional customer experiences.</h2>
        </div>
        <Link href="/register">Get started <ArrowRight size={16} /></Link>
        <AudioLines size={120} aria-hidden="true" />
      </section>
    </main>
  );
}

function RibbonStep({ icon: Icon, label, text }: { icon: LucideIcon; label: string; text: string }) {
  return <div><Icon size={17} /><p><strong>{label}</strong><span>{text}</span></p></div>;
}

function SectionHeading({
  kicker,
  title,
  text,
  dark = false,
  action,
}: {
  kicker: string;
  title: string;
  text?: string;
  dark?: boolean;
  action?: { label: string; href: string };
}) {
  return (
    <div className={`${styles.sectionHeading} ${dark ? styles.dark : ""}`} data-reveal>
      <div><span>{kicker}</span><h2>{title}</h2>{text ? <p>{text}</p> : null}</div>
      {action ? <Link href={action.href}>{action.label} <ArrowRight size={14} /></Link> : null}
    </div>
  );
}

function Metric({ label, value, change }: { label: string; value: string; change: string }) {
  return <div><small>{label}</small><strong>{value}</strong><span>{change}</span></div>;
}

function Faq({ question, answer }: { question: string; answer: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={`${styles.faqItem} ${open ? styles.open : ""}`}>
      <button type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
        {question}<ChevronDown size={17} />
      </button>
      <div><p>{answer}</p></div>
    </div>
  );
}
