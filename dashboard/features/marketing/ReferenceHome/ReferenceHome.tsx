"use client";

import Link from "next/link";
import Image from "next/image";
import { useState } from "react";
import {
  Activity, ArrowRight, BarChart3, Bot, CalendarCheck, Check,
  ChevronDown, Clock3, Globe2, Headphones, Layers3,
  LockKeyhole, MessageSquareText, Mic2, PhoneCall, PlugZap, Radio, Route,
  ShieldCheck, Sparkles, UserRoundCheck, Workflow, Zap, Play,
} from "lucide-react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import styles from "./ReferenceHome.module.css";

const proofPoints = [
  { icon: PhoneCall, value: "24/7", label: "Always available", desc: "Keep every caller covered.", featured: true },
  { icon: Globe2, value: "Multilingual", label: "Natural conversations", desc: "Configure language and voice per agent.", featured: true },
  { icon: Zap, value: "Real-time actions", label: "Complete the work", desc: "Book, route, update, and follow up.", featured: true },
  { icon: Workflow, value: "Safe workflows", label: "Tools with guardrails", desc: "Control what each agent can do.", featured: false },
  { icon: Layers3, value: "Complete visibility", label: "One unified workspace", desc: "Calls, bookings, tools, and analytics.", featured: false },
];

const outcomeCards = [
  { icon: PhoneCall, title: "Missed-call coverage", text: "Track answered and unanswered demand across every configured channel." },
  { icon: CalendarCheck, title: "Booking conversion", text: "See which conversations become confirmed appointments and follow-ups." },
  { icon: Activity, title: "Response quality", text: "Review transcripts, outcomes, duration, and agent actions in one place." },
];

const chapters = [
  { icon: Bot, title:"Configure your agent", text:"Customize identity, behavior, voice, languages, knowledge, tools, and handoff rules.", benefit:"Launch with clear guardrails", view:<AgentView/> },
  { icon: Radio, title:"Handle calls in real time", text:"Track the call, follow the transcript, and inspect what the agent hears, says, and does.", benefit:"Stay in control live", view:<CallsView/> },
  { icon: CalendarCheck, title:"Book more, automatically", text:"Check real availability, create appointments, and keep every booking synchronized.", benefit:"Turn intent into action", view:<BookingView/> },
  { icon: BarChart3, title:"Measure what matters", text:"Understand call volume, outcomes, conversion, languages, and agent performance.", benefit:"Improve from every call", view:<AnalyticsView/> },
  { icon: PlugZap, title:"Connect your stack", text:"Give each agent approved access to calendars, sheets, CRMs, messaging, and automation.", benefit:"Keep systems synchronized", view:<IntegrationsView/> },
];

const teamUses = [
  [CalendarCheck,"Appointment booking","Book, reschedule, and cancel consultations."],
  [Headphones,"Customer support","Resolve common questions and escalate safely."],
  [Route,"Lead qualification","Capture intent and route valuable opportunities."],
  [Clock3,"Follow-ups","Keep customers informed after every conversation."],
  [Activity,"Healthcare","Handle enquiries while protecting clear handoff paths."],
  [Layers3,"Property","Qualify enquiries and coordinate viewings."],
  [Workflow,"Retail","Answer product, order, and availability questions."],
  [Globe2,"Education","Support admissions, scheduling, and multilingual callers."],
] as const;

const lifecycle = [
  [PhoneCall,"Inbound call","A customer calls your business."],
  [Mic2,"AI answers","The agent greets and understands intent."],
  [UserRoundCheck,"Qualify & route","Details are captured and the right path is chosen."],
  [CalendarCheck,"Take action","Book, create, update, or trigger a tool."],
  [MessageSquareText,"Confirm & follow up","Send confirmation and complete post-call work."],
  [BarChart3,"Track results","Review the transcript, outcome, and performance."],
] as const;

const faq = [
  ["Setup","How does the test call work?","Create or open an agent, choose Test agent, and speak from your browser. The same conversation pipeline, tools, and call history are used without requiring a phone number."],
  ["Phone numbers","Can I use my own phone number?","Phone channels are configured per workspace through supported telephony providers. Browser voice can operate alongside phone calls."],
  ["Languages","What languages are supported?","Each agent has configured languages and voices. Speech recognition, reasoning, and voice output follow the selected language configuration."],
  ["Handoffs","Can I hand calls to my team?","Yes. Configure transfer and escalation behavior so callers can reach a person when the request is sensitive, urgent, or outside the agent's scope."],
  ["Data and privacy","Is my business data isolated?","Connections, agents, calls, bookings, and customer records are tenant-scoped. Provider credentials are encrypted and only enabled agents can use connected tools."],
  ["Trial and workflows","What happens after a call?","Sauti stores the outcome and transcript, updates analytics, and runs enabled workflows such as sheet logging, CRM sync, notifications, or webhooks."],
];

export default function ReferenceHome(){return <main className={styles.page}>
  <section className={styles.hero} data-hero-parallax>
    <div className={styles.heroMedia} aria-hidden="true">
      <Image src="/images/marketing/sauti-phone-hero.png" alt="" fill priority sizes="100vw"/>
    </div>
    <div className={styles.heroShade} aria-hidden="true"/>
    <div className={styles.heroCopy} data-reveal>
      <span className={styles.eyebrow}><Sparkles size={13}/> ALWAYS-ON AI PHONE AGENTS</span>
      <h1><span>Every call answered.</span><em>Every opportunity moved forward.</em></h1>
      <p>Sauti&apos;s AI voice agents handle calls, book appointments, qualify leads, and update your tools—24/7. So you never miss what moves your business forward.</p>
      <div className={styles.actions}>
        <Link className={styles.primary} href="/register">Start building <ArrowRight size={16}/></Link>
        <a className={styles.secondary} href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer"><Play size={15}/> Hear a real conversation</a>
      </div>
      <div className={styles.assurances}><span><Check size={13}/> No credit card required</span><span><Check size={13}/> Setup in minutes</span></div>
    </div>
    <div className={styles.liveConversation} data-reveal-right>
      <div className={styles.liveSignal}>
        <span><i/> Live</span>
        <div className={styles.liveWave} aria-hidden="true">
          {Array.from({length:28}).map((_,index)=><i key={index} style={{animationDelay:`-${index*43}ms`}}/>)}
        </div>
      </div>
      <div className={styles.liveTranscript}>
        <p><strong>Caller</strong><span>Do you have a consultation this week?</span></p>
        <p><strong>Sauti</strong><span>Yes. Which day works best for you?</span></p>
      </div>
      <div className={styles.liveMeta}><span>Live conversation</span><strong>02:14</strong></div>
    </div>
  </section>

  <section className={styles.trustStrip} aria-label="Platform trust indicators" data-reveal><span><ShieldCheck size={17}/> Built for customer-facing teams</span><span><Mic2 size={17}/> Natural multilingual conversations</span><span><LockKeyhole size={17}/> Safe actions with clear guardrails</span></section>

  <section className={styles.metricsBand}>
    {proofPoints.map(({icon:Icon,...m}, i)=><div key={m.label} className={`${styles.metricCard} ${m.featured?styles.metricFeatured:styles.metricSecondary}`} data-reveal style={{transitionDelay:`${i*70}ms`}}>
      <div className={styles.metricTop}><div className={styles.metricIcon}><Icon size={21}/></div><strong>{m.value}</strong></div>
      <span>{m.label}</span>
      <small>{m.desc}</small>
    </div>)}
  </section>

  <section className={styles.features} data-feature-story>
    <div className={styles.featureSticky}>
      <Header kicker="Product features" title="Everything you need to run AI calls" text="From setup to analytics, Sauti keeps the entire voice operation visible and under your control."/>
      <div className={styles.storyProgress} aria-hidden="true"><span>Scroll to explore</span><div><i/></div><em><b data-story-current>01</b> / {String(chapters.length).padStart(2,"0")}</em></div>
      <div className={styles.chapterList}>{chapters.map(({icon:Icon,...chapter},index)=><article className={styles.chapter} key={chapter.title} data-story-panel><div className={styles.chapterAtmosphere} aria-hidden="true"><i/><i/><i/></div><div className={styles.chapterCopy}><span>{index+1}</span><div className={styles.chapterIcon}><Icon size={18}/></div><div className={styles.chapterBody}><small className={styles.chapterCount}>Feature {String(index+1).padStart(2,"0")} / {String(chapters.length).padStart(2,"0")}</small><h3>{chapter.title}</h3><p>{chapter.text}</p><small className={styles.chapterBenefit}><Check size={12}/>{chapter.benefit}</small></div></div><div className={styles.chapterView}>{chapter.view}</div></article>)}</div>
    </div>
  </section>

  <section className={styles.lifecycle}>
    <Header kicker="How it works" title="From inbound call to happy customer" text="Sauti manages the entire call lifecycle end-to-end."/>
    <div className={styles.lifecycleGrid}>{lifecycle.map(([Icon,title,text],index)=><article key={title} data-reveal style={{transitionDelay:`${index*55}ms`}}><div><Icon size={23}/></div><span>{index+1}</span><h3>{title}</h3><p>{text}</p>{index<lifecycle.length-1&&<ArrowRight className={styles.connector} size={17}/>}</article>)}</div>
  </section>

  <section className={styles.teams}>
    <Header kicker="Use cases and industries" title="Built for every customer-facing team" text="Start with one workflow, then expand across the conversations your business handles every day."/>
    <div className={styles.teamGrid}>{teamUses.map(([Icon,title,text])=><Team key={title} icon={Icon} title={title} text={text}/>)}</div>
  </section>

  <section className={styles.security}>
    <div><span className={styles.eyebrow}><ShieldCheck size={13}/> Control by design</span><h2>Secure, reliable and enterprise-ready</h2><p>Protect workspace data, define clear permissions, and keep people available for the conversations that need them.</p></div>
    <div className={styles.securityItems}><Security icon={LockKeyhole} top="Encrypted data" bottom="Protected credentials"/><Security icon={ShieldCheck} top="Secure isolation" bottom="Tenant-scoped records"/><Security icon={Activity} top="Reliable systems" bottom="Reviewable activity"/><Security icon={UserRoundCheck} top="Human handoff" bottom="Escalation paths"/><Security icon={Bot} top="Agent controls" bottom="Explicit permissions"/></div>
  </section>

  <section className={styles.outcomes}>
    <Header kicker="Measurable by design" title="See the outcomes that matter" text="Sauti keeps operational performance visible without making unsupported promises about your results."/>
    <div className={styles.outcomeGrid}>{outcomeCards.map(({icon:Icon,...item})=><Outcome key={item.title} icon={Icon} {...item}/>)}</div>
  </section>

  <section className={styles.faq}><Header kicker="FAQ" title="Frequently asked questions"/><div className={styles.faqGrid}>{faq.map(([category,q,a])=><Faq category={category} question={q} answer={a} key={q}/>)}</div></section>

  <section className={styles.cta}><div className={styles.ctaWave}/><h2>Ready to launch your AI voice agent?</h2><p>Build your first agent in minutes. No credit card required.</p><div className={styles.actions}><Link className={styles.primary} href="/register">Start free trial <ArrowRight size={16}/></Link><a className={styles.secondary} href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Book a demo</a></div><div className={styles.ctaAssurances}><span><Check size={12}/> Setup in minutes</span><span><Check size={12}/> Cancel anytime</span></div></section>
</main>}

function Header({kicker,title,text}:{kicker:string;title:string;text?:string}){return <div className={styles.header} data-reveal><span>{kicker}</span><h2>{title}</h2>{text&&<p>{text}</p>}</div>}
function Faq({category,question,answer}:{category:string;question:string;answer:string}){const[open,setOpen]=useState(false);return <div className={`${styles.faqItem} ${open?styles.open:""}`}><button onClick={()=>setOpen(!open)} aria-expanded={open}><span><small>{category}</small>{question}</span><ChevronDown size={17}/></button><div><p>{answer}</p></div></div>}
function Team({icon:Icon,title,text}:{icon:typeof CalendarCheck;title:string;text:string}){return <article data-reveal><Icon size={24}/><h3>{title}</h3><p>{text}</p></article>}
function Security({icon:Icon,top,bottom}:{icon:typeof ShieldCheck;top:string;bottom:string}){return <div><span><Icon size={19}/></span><strong>{top}</strong><small>{bottom}</small></div>}
function Outcome({icon:Icon,title,text}:{icon:typeof PhoneCall;title:string;text:string}){return <article className={styles.outcomeCard} data-reveal><span><Icon size={21}/></span><div><strong>{title}</strong><p>{text}</p></div><ArrowRight size={17}/></article>}
function Frame({title,children,action}:{title:string;children:React.ReactNode;action?:string}){return <div className={styles.frame}><div className={styles.frameTop}><div><BrandLogo size={19}/><strong>{title}</strong></div><span>{action??"Live preview"}</span></div>{children}</div>}
function Stat({label,value}:{label:string;value:string}){return <div><span>{label}</span><strong>{value}</strong><small>↑ active</small></div>}

function AgentView(){return <Frame title="Agent configuration · Sarah"><div className={styles.agentView}><div className={styles.agentForm}><span>Identity and call setup</span><div className={styles.formBanner}><PhoneCall size={14}/><div><strong>Enable a customer channel</strong><small>Connect a number when this agent is ready for live callers.</small></div><button>Choose channel</button></div><div className={styles.formGrid}><label>Agent name<div>Sarah</div></label><label>Primary role<div>Appointment booking agent</div></label></div><label>Business description<div>Front desk for Tranquil AI. Answer questions, collect required details, and book consultations.</div></label></div><div className={styles.voicePreview}><span><Radio size={11}/> Live preview</span><div><Mic2 size={25}/></div><strong>Warm & conversational</strong><small>French · English</small></div></div></Frame>}
function CallsView(){return <Frame title="Calls"><div className={styles.callsView}><div className={styles.callTable}><div className={styles.tableToolbar}><span>All</span><span>Calls</span><span>Tests</span><em>Latest activity</em></div><div><b>Appointment Booking<small>Jul 13 · 09:42</small></b><span>Browser test</span><em>Booking made</em></div><div><b>Check Business Hours<small>Jul 13 · 09:21</small></b><span>Phone call</span><em>Answered</em></div><div><b>General Inquiry<small>Jul 12 · 17:08</small></b><span>Browser test</span><em>Answered</em></div></div><div className={styles.transcript}><div className={styles.transcriptTitle}><b>Call transcript</b><span>2m 14s</span></div><p><span>A</span><strong>Agent</strong><small>Bonjour, comment puis-je vous aider ?</small></p><p><span>C</span><strong>Caller</strong><small>Je voudrais prendre une consultation.</small></p><p><span>A</span><strong>Agent</strong><small>Bien sûr. Quel jour vous conviendrait ?</small></p></div></div></Frame>}
function BookingView(){return <Frame title="Bookings"><div className={styles.bookingView}><div className={styles.bookingStats}><Stat label="Upcoming" value="9"/><Stat label="Today" value="2"/><Stat label="Confirmed" value="8"/><Stat label="Cancelled" value="1"/></div><div className={styles.bookingRow}><span><CalendarCheck size={16}/></span><div><strong>Consultation with Sarah</strong><small>Wednesday · 13:00 · 30 minutes</small></div><em>Confirmed</em></div><div className={styles.bookingRow}><span><CalendarCheck size={16}/></span><div><strong>Follow-up visit</strong><small>Thursday · 10:30 · 45 minutes</small></div><em>Confirmed</em></div></div></Frame>}
function AnalyticsView(){return <Frame title="Analytics" action="Last 30 days"><div className={styles.analyticsView}><div className={styles.analyticsTop}><Stat label="Total calls" value="430"/><Stat label="Conversion" value="25.9%"/><Stat label="Avg duration" value="1m 27s"/></div><div className={styles.analyticsBottom}><div><b>Calls by day</b><div className={styles.barChart}>{[38,55,43,72,63,86,66,96,79,105,88,114].map((h,i)=><i style={{height:`${h}px`}} key={i}/>)}</div></div><div className={styles.donut}><div/><b>Language breakdown</b><span>FR 72% · EN 23% · Other 5%</span></div></div></div></Frame>}
function IntegrationsView(){return <Frame title="Integrations marketplace"><div className={styles.integrationView}>{[["Google Calendar","/logos/google-calendar.svg","Scheduling","Book directly into the calendars your team already uses."],["WhatsApp","/logos/whatsapp.svg","Messaging","Confirm appointments and send useful call follow-ups."],["HubSpot","/logos/hubspot.svg","CRM","Create contacts and keep call context with the customer."]].map(([name,logo,type,desc],i)=><article key={name}><Image src={logo} alt="" width={28} height={28}/><div><strong>{name}</strong><span>{type}</span></div><p>{desc}</p><em>{i<2?"Connected":"Connect"} <ArrowRight size={11}/></em></article>)}</div></Frame>}
