"use client";

import { useEffect, useState } from "react";
import {
  Activity,
  ArrowRight,
  Bot,
  CalendarCheck,
  Check,
  ChevronDown,
  Clock3,
  Globe2,
  LockKeyhole,
  MessageSquareText,
  PhoneCall,
  PlugZap,
  ShieldCheck,
} from "lucide-react";
import { faq, integrations, metrics, partners, workflow } from "../HomePage/content";
export function PartnerStrip() {
  return (
    <section className="partner-strip" aria-label="Technology partners">
      <p>Trusted by innovative teams</p>
      <div>
        {partners.map((item) => <span key={item}>{item}</span>)}
      </div>
    </section>
  );
}

export function MetricsSection() {
  return (
    <section className="metrics-band" data-reveal>
      {metrics.map((metric) => (
        <div className="metric" key={metric.label}>
          <metric.icon size={31} />
          <MetricNumber {...metric} />
          <span>{metric.label}</span>
        </div>
      ))}
    </section>
  );
}

function MetricNumber({ value, prefix = "", suffix = "" }: { value: number; prefix?: string; suffix?: string }) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    const duration = 950;
    const start = performance.now();
    let frame = 0;
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / duration);
      setCount(Math.round(value * progress));
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [value]);

  return <strong>{prefix}{count}{suffix}</strong>;
}

export function WorkflowSection() {
  return (
    <section className="section workflow-section" id="workflow">
      <SectionIntro eyebrow="How it works" title="A complete call pipeline, not a static bot." />
      <div className="workflow-grid">
        {workflow.map((item, index) => (
          <article className="workflow-card" key={item.title} data-reveal style={{ transitionDelay: `${index * 90}ms` }}>
            <div className="step-badge">0{index + 1}</div>
            <item.icon size={24} />
            <h3>{item.title}</h3>
            <p>{item.text}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

export function LiveOpsSection() {
  return (
    <section className="section split-section dashboard-section" id="dashboard">
      <div data-reveal>
        <SectionIntro eyebrow="Operator dashboard" title="Monitor every call as the agent works." align="left" />
        <p className="section-copy">
          Real-time visibility into every call with live transcripts, intent detection, call status, and performance metrics.
        </p>
        <ul className="check-list">
          <li><Check size={16} /> Live call transcript</li>
          <li><Check size={16} /> Interruption detection</li>
          <li><Check size={16} /> Call outcome and disposition</li>
          <li><Check size={16} /> Performance analytics</li>
        </ul>
      </div>
      <OperationsHub />
    </section>
  );
}

function OperationsHub() {
  return (
    <div className="ops-board" data-reveal>
      <div className="ops-header">
        <strong>Operations Hub</strong>
        <span>Online</span>
      </div>
      <div className="ops-metrics">
        <MetricPill label="Live Calls" value="12" />
        <MetricPill label="Avg Response Time" value="612ms" />
        <MetricPill label="Calls Today" value="38" />
      </div>
      <div className="ops-content">
        <div className="live-call-list">
          {["+1 (415) 213-4567", "+1 (917) 555-0109", "+1 (909) 506-0123", "+1 (212) 555-0941"].map((phone, index) => (
            <div className="call-row" key={phone}>
              <span className={`avatar a${index}`}><PhoneCall size={13} /></span>
              <div>
                <strong>{phone}</strong>
                <small>{index === 0 ? "Booking a dentist cleaning" : index === 1 ? "Reschedule request" : index === 2 ? "Human transfer requested" : "Appointment confirmed"}</small>
              </div>
              <div className="activity-track" aria-hidden="true"><i /><span /></div>
              <em>{index === 3 ? "Completed" : "In progress"}</em>
            </div>
          ))}
        </div>
        <div className="recent-activity">
          <strong>Recent Activity</strong>
          <EventLine icon={CalendarCheck} title="Appointment booked" text="2 min ago" confirmed />
          <EventLine icon={Clock3} title="Reminder sent" text="4 min ago" />
          <EventLine icon={MessageSquareText} title="Call completed" text="12 min ago" />
          <EventLine icon={Bot} title="New lead captured" text="25 min ago" />
        </div>
      </div>
    </div>
  );
}

export function SmartSection() {
  return (
    <section className="section smart-panel" id="calendar">
      <div className="smart-carousel" data-reveal>
        <div className="carousel-track">
          <div className="carousel-card scheduling-card">
            <div className="carousel-copy">
              <SectionIntro eyebrow="Smart scheduling" title="Check first. Confirm second. Book once." align="left" />
              <p className="section-copy">Real-time calendar sync across teams and locations. No double bookings, ever.</p>
              <a className="outline-link" href="#dashboard">See it in action</a>
            </div>
            <CalendarBoard />
          </div>
          <div className="carousel-card intelligence-card">
            <LanguagePanel />
          </div>
        </div>
      </div>
      <div className="carousel-dots" aria-hidden="true">
        <span className="active" />
        <span />
      </div>
    </section>
  );
}

function CalendarBoard() {
  const times = ["09:00 AM", "10:30 AM", "01:00 PM", "03:30 PM"];
  return (
    <div className="calendar-board" data-reveal>
      <div className="calendar-month">
        <span>‹</span>
        <strong>June 2025</strong>
        <span>›</span>
      </div>
      <div className="calendar-grid">
        {Array.from({ length: 30 }).map((_, index) => (
          <div className={index === 11 || index === 23 ? "day active" : "day"} key={index}>{index + 1}</div>
        ))}
      </div>
      <div className="time-slots">
        <strong>Tue, Jun 24</strong>
        {times.map((time, index) => (
          <div key={time}>
            <span>{time}</span>
            <small>Available</small>
            <button>{index === 1 ? "Book" : "Check"}</button>
          </div>
        ))}
      </div>
    </div>
  );
}

function LanguagePanel() {
  return (
    <div className="language-panel" data-reveal>
      <div className="language-copy">
        <SectionIntro eyebrow="Adaptive intelligence" title="Understands language, intent, and interruptions." align="left" />
        <p className="section-copy">Human-like conversations in 30+ languages. Handles interruptions naturally.</p>
        <a className="outline-link" href="#integrations">Learn more</a>
      </div>
      <div className="language-orb">
        <div className="orb-ring" />
        <strong>30+</strong>
        <span>Languages</span>
        {["English", "French", "Hindi", "Portuguese", "Spanish", "Arabic"].map((item, index) => (
          <i className={`lang-${index}`} key={item}>{item}</i>
        ))}
      </div>
    </div>
  );
}

export function ProductionSection() {
  return (
    <section className="section production-section" id="security">
      <div data-reveal>
        <SectionIntro eyebrow="Production ready" title="Built for scale, security, and reliability." align="left" />
        <p className="section-copy">Enterprise-grade infrastructure with global reach. Deploy anywhere, scale everywhere.</p>
      </div>
      <div className="production-grid">
        <SecurityTile icon={ShieldCheck} title="SOC 2 Type II" />
        <SecurityTile icon={Activity} title="99.99% Uptime" />
        <SecurityTile icon={Globe2} title="Global Infrastructure" />
        <SecurityTile icon={PlugZap} title="Webhooks and API" />
        <SecurityTile icon={LockKeyhole} title="Tenant Isolation" />
        <SecurityTile icon={PhoneCall} title="Phone Infrastructure" />
      </div>
    </section>
  );
}

export function IntegrationsSection() {
  const rows = [integrations.slice(0, 6), integrations.slice(6)];

  return (
    <section className="section integrations-panel" id="integrations">
      <SectionIntro eyebrow="Powerful integrations" title="Connect your stack. Automate everything." />
      <p className="integrations-copy">
        Sauti connects voice, AI, calendars, and customer tools into one continuous call workflow.
      </p>
      <div className="integration-showcase" data-reveal>
        {rows.map((row, rowIndex) => (
          <div className="integration-marquee" key={rowIndex}>
            <div className={`integration-track ${rowIndex === 1 ? "reverse" : ""}`}>
              {[...row, ...row].map((item, index) => (
                <article
                  className={`integration-card ${item.tone}`}
                  aria-hidden={index >= row.length}
                  key={`${item.name}-${index}`}
                >
                  <span className={`brand-dot ${"logoStyle" in item ? item.logoStyle : ""}`}>
                    <img src={item.logo} alt="" />
                  </span>
                  <span className="integration-card-copy">
                    <strong>{item.name}</strong>
                    <small>{item.category}</small>
                  </span>
                  <ArrowRight className="integration-card-arrow" size={17} />
                </article>
              ))}
            </div>
          </div>
        ))}
      </div>
      <a className="view-integrations" href="/integrations">Explore integrations <ArrowRight size={16} /></a>
    </section>
  );
}

export function FaqSection() {
  return (
    <section className="section faq-section">
      <SectionIntro eyebrow="FAQ" title="What teams ask before launching AI calls." />
      <div className="faq-list">
        {faq.map(([question, answer]) => (
          <details key={question}>
            <summary>{question}<ChevronDown size={18} /></summary>
            <p>{answer}</p>
          </details>
        ))}
      </div>
    </section>
  );
}

export function FinalCta() {
  return (
    <section className="final-cta" id="contact">
      <div data-reveal>
        <span>Ready to transform your phone calls?</span>
        <h2>Let every caller book without waiting.</h2>
        <div className="hero-actions cta-actions">
          <a className="solid-button large" href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">
            Start your pilot <ArrowRight size={18} />
          </a>
          <a className="demo-button large" href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Book a demo</a>
        </div>
      </div>
    </section>
  );
}

function Footer() {
  return (
    <footer className="footer">
      <div>
        <a className="brand" href="#"><span className="brand-mark">S</span><span>Sauti</span></a>
        <p>AI voice agents that turn conversations into conversions.</p>
      </div>
      <div className="footer-links">
        <a href="#workflow">Features</a>
        <a href="#integrations">Integrations</a>
      </div>
      <div className="footer-links">
        <a href="#security">Company</a>
        <a href="#security">Security</a>
        <a href="#contact">Contact</a>
      </div>
      <small>© 2025 Sauti. All rights reserved.</small>
    </footer>
  );
}

function SectionIntro({ eyebrow, title, align = "center" }: { eyebrow: string; title: string; align?: "center" | "left" }) {
  return (
    <div className={`section-intro ${align}`} data-reveal>
      <span>{eyebrow}</span>
      <h2>{title}</h2>
    </div>
  );
}

function MetricPill({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function EventLine({ icon: Icon, title, text, confirmed = false }: { icon: typeof PhoneCall; title: string; text: string; confirmed?: boolean }) {
  return (
    <div className={`event-line ${confirmed ? "confirmed" : ""}`}>
      <Icon size={16} />
      <div>
        <strong>{title}</strong>
        <span>{text}</span>
      </div>
    </div>
  );
}

function SecurityTile({ icon: Icon, title }: { icon: typeof LockKeyhole; title: string }) {
  return (
    <div className="security-tile" data-reveal>
      <Icon size={22} />
      <div>
        <strong>{title}</strong>
        <span>{title.includes("SOC") ? "Certified security" : title.includes("Uptime") ? "Enterprise SLA" : title.includes("Global") ? "Low latency worldwide" : title.includes("Webhooks") ? "Real-time integrations" : title.includes("Tenant") ? "Secure by design" : "Carrier-grade quality"}</span>
      </div>
    </div>
  );
}
