"use client";

import Link from "next/link";
import { useState } from "react";
import {
  Activity, ArrowRight, BarChart3, Bot, CalendarCheck, Check,
  ChevronDown, Clock3, Headphones,
  LockKeyhole, MessageSquareText, Mic2, PhoneCall, PlugZap, Radio, Route,
  ShieldCheck, Sparkles, UserRoundCheck,
} from "lucide-react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import styles from "./ReferenceHome.module.css";

const chapters = [
  { title:"Configure your agent", text:"Customize identity, behavior, voice, languages, knowledge, tools, and handoff rules.", view:<AgentView/> },
  { title:"Handle calls in real time", text:"Track the call, follow the transcript, and inspect what the agent hears, says, and does.", view:<CallsView/> },
  { title:"Book more, automatically", text:"Check real availability, create appointments, and keep every booking synchronized.", view:<BookingView/> },
  { title:"Measure what matters", text:"Understand call volume, outcomes, conversion, languages, and agent performance.", view:<AnalyticsView/> },
  { title:"Connect your stack", text:"Give each agent approved access to calendars, sheets, CRMs, messaging, and automation.", view:<IntegrationsView/> },
];

const lifecycle = [
  [PhoneCall,"Inbound call","A customer calls your business."],
  [Mic2,"AI answers","The agent greets and understands intent."],
  [UserRoundCheck,"Qualify & route","Details are captured and the right path is chosen."],
  [CalendarCheck,"Take action","Book, create, update, or trigger a tool."],
  [MessageSquareText,"Confirm & follow up","Send confirmation and complete post-call work."],
  [BarChart3,"Stay informed","Review the transcript, outcome, and performance."],
] as const;

const faq = [
  ["How does the test call work?","Create or open an agent, choose Test agent, and speak from your browser. The same conversation pipeline, tools, and call history are used without requiring a phone number."],
  ["Can I use my own phone number?","Phone channels are configured per workspace through supported telephony providers. Browser voice can operate alongside phone calls."],
  ["What languages are supported?","Each agent has configured languages and voices. Speech recognition, reasoning, and voice output follow the selected language configuration."],
  ["Can I hand calls to my team?","Yes. Configure transfer and escalation behavior so callers can reach a person when the request is sensitive, urgent, or outside the agent's scope."],
  ["Is my business data isolated?","Connections, agents, calls, bookings, and customer records are tenant-scoped. Provider credentials are encrypted and only enabled agents can use connected tools."],
  ["What happens after a call?","Sauti stores the outcome and transcript, updates analytics, and runs enabled workflows such as sheet logging, CRM sync, notifications, or webhooks."],
];

export default function ReferenceHome(){return <main className={styles.page}>
  <section className={styles.hero}>
    <div className={styles.aurora}/>
    <div className={styles.heroCopy} data-reveal>
      <span className={styles.eyebrow}><Sparkles size={13}/> AI voice agents for real operations</span>
      <h1>AI voice agents that <em>answer, book, and automate.</em></h1>
      <p>Sauti handles calls, understands callers, schedules appointments, qualifies leads, and follows up—while your team stays in control from one workspace.</p>
      <div className={styles.actions}><Link className={styles.primary} href="/register">Start free trial <ArrowRight size={16}/></Link><a className={styles.secondary} href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Book a demo</a></div>
      <div className={styles.assurances}><span><Check size={13}/> No credit card</span><span><Check size={13}/> Browser test calls</span><span><Check size={13}/> Cancel anytime</span></div>
    </div>
    <div className={styles.heroScreen} data-reveal><DashboardView/></div>
  </section>

  <section className={styles.features}>
    <Header kicker="Product features" title="Everything you need to run AI calls" text="From setup to analytics, Sauti keeps the entire voice operation visible and under your control."/>
    <div className={styles.chapterList}>{chapters.map((chapter,index)=><article className={styles.chapter} key={chapter.title} data-reveal><div className={styles.chapterCopy}><span>0{index+1}</span><div><h3>{chapter.title}</h3><p>{chapter.text}</p></div></div><div className={styles.chapterView}>{chapter.view}</div></article>)}</div>
  </section>

  <section className={styles.lifecycle}>
    <Header kicker="How it works" title="From inbound call to completed work" text="Sauti manages the full conversation lifecycle—not only the spoken response."/>
    <div className={styles.lifecycleGrid}>{lifecycle.map(([Icon,title,text],index)=><article key={title} data-reveal style={{transitionDelay:`${index*55}ms`}}><div><Icon size={23}/></div><span>{index+1}</span><h3>{title}</h3><p>{text}</p>{index<lifecycle.length-1&&<ArrowRight className={styles.connector} size={17}/>}</article>)}</div>
  </section>

  <section className={styles.teams}>
    <Header kicker="Built for every team" title="Use Sauti across your business"/>
    <div className={styles.teamGrid}><Team icon={CalendarCheck} title="Appointment booking" text="Book, reschedule, and cancel consultations automatically."/><Team icon={Headphones} title="Customer support" text="Answer common questions and escalate when needed."/><Team icon={Route} title="Lead qualification" text="Capture intent and route high-value opportunities."/><Team icon={Clock3} title="Follow-up & reminders" text="Run post-call workflows and keep customers informed."/></div>
  </section>

  <section className={styles.security}>
    <div><span className={styles.eyebrow}><ShieldCheck size={13}/> Control by design</span><h2>Secure. Reliable.<br/>Ready for business.</h2><p>Your workspace data is isolated, provider credentials are protected, and every agent action is explicitly configured.</p></div>
    <div className={styles.securityItems}><Security icon={LockKeyhole} title="Encrypted credentials"/><Security icon={ShieldCheck} title="Tenant isolation"/><Security icon={Activity} title="Reviewable activity"/><Security icon={UserRoundCheck} title="Human fallback"/></div>
  </section>

  <section className={styles.faq}><Header kicker="FAQ" title="Frequently asked questions"/><div className={styles.faqGrid}>{faq.map(([q,a])=><Faq question={q} answer={a} key={q}/>)}</div></section>

  <section className={styles.cta}><div className={styles.ctaWave}/><h2>Ready to launch your AI voice agent?</h2><p>Build an agent, test the full conversation, and connect your workflow when you are ready.</p><div className={styles.actions}><Link className={styles.primary} href="/register">Start free trial <ArrowRight size={16}/></Link><a className={styles.secondary} href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Book a demo</a></div></section>
</main>}

function Header({kicker,title,text}:{kicker:string;title:string;text?:string}){return <div className={styles.header} data-reveal><span>{kicker}</span><h2>{title}</h2>{text&&<p>{text}</p>}</div>}
function Faq({question,answer}:{question:string;answer:string}){const[open,setOpen]=useState(false);return <div className={`${styles.faqItem} ${open?styles.open:""}`}><button onClick={()=>setOpen(!open)} aria-expanded={open}><span>{question}</span><ChevronDown size={17}/></button><div><p>{answer}</p></div></div>}
function Team({icon:Icon,title,text}:{icon:typeof CalendarCheck;title:string;text:string}){return <article data-reveal><Icon size={24}/><h3>{title}</h3><p>{text}</p></article>}
function Security({icon:Icon,title}:{icon:typeof ShieldCheck;title:string}){return <div><span><Icon size={20}/></span><strong>{title}</strong></div>}
function Frame({title,children,action}:{title:string;children:React.ReactNode;action?:string}){return <div className={styles.frame}><div className={styles.frameTop}><div><BrandLogo size={19}/><strong>{title}</strong></div><span>{action??"Live preview"}</span></div>{children}</div>}
function DashboardView(){return <Frame title="Sauti" action="Workspace overview"><div className={styles.dashboard}><aside><b>Tranquil AI</b>{[[Activity,"Overview"],[Bot,"Agents"],[PhoneCall,"Calls"],[CalendarCheck,"Bookings"],[BarChart3,"Analytics"],[PlugZap,"Integrations"]].map(([Icon,label],i)=><span className={i===0?styles.active:""} key={String(label)}><Icon size={13}/>{String(label)}</span>)}</aside><section><div className={styles.dashTitle}><div><small>Good evening, Tranquil AI</small><strong>Monitor your voice operation</strong></div><button>+ Create agent</button></div><div className={styles.dashStats}><Stat label="Total calls" value="1,248"/><Stat label="Bookings" value="324"/><Stat label="Answer rate" value="68%"/><Stat label="Minutes left" value="2,420"/></div><div className={styles.dashPanels}><div className={styles.lineChart}><b>Call activity</b><svg viewBox="0 0 420 125" preserveAspectRatio="none"><path d="M0 105 C35 97 39 48 78 68 S125 111 158 59 S209 103 243 70 S289 29 321 60 S367 81 395 37 S425 49 440 20"/><path className={styles.area} d="M0 105 C35 97 39 48 78 68 S125 111 158 59 S209 103 243 70 S289 29 321 60 S367 81 395 37 S425 49 440 20 L440 125 L0 125Z"/></svg></div><div className={styles.ops}><b>Operations summary</b><Event icon={PhoneCall} title="Incoming call" value="Completed"/><Event icon={CalendarCheck} title="Booking created" value="Confirmed"/><Event icon={MessageSquareText} title="Call ended" value="Completed"/></div></div></section></div></Frame>}
function Stat({label,value}:{label:string;value:string}){return <div><span>{label}</span><strong>{value}</strong><small>↑ active</small></div>}
function Event({icon:Icon,title,value}:{icon:typeof PhoneCall;title:string;value:string}){return <div><Icon size={13}/><span>{title}</span><em>{value}</em></div>}

function AgentView(){return <Frame title="Agent configuration · Sarah"><div className={styles.agentView}><div className={styles.agentForm}><span>Identity and call setup</span><label>Agent name<div>Sarah</div></label><label>Primary role<div>Appointment booking agent</div></label><label>Business description<div>Front desk for Tranquil AI. Answer questions, collect required details, and book consultations.</div></label></div><div className={styles.voicePreview}><span><Radio size={11}/> Live preview</span><div><Mic2 size={25}/></div><strong>Warm & conversational</strong><small>French · English</small></div></div></Frame>}
function CallsView(){return <Frame title="Calls"><div className={styles.callsView}><div className={styles.callTable}><div><b>Appointment Booking</b><span>Browser test</span><em>Booking made</em></div><div><b>Check Business Hours</b><span>Phone call</span><em>Answered</em></div><div><b>General Inquiry</b><span>Browser test</span><em>Answered</em></div><div><b>Identity Verification</b><span>Phone call</span><em>Answered</em></div></div><div className={styles.transcript}><b>Call transcript</b><p><span>A</span><strong>Agent</strong><small>Bonjour, comment puis-je vous aider ?</small></p><p><span>C</span><strong>Caller</strong><small>Je voudrais prendre une consultation.</small></p><p><span>A</span><strong>Agent</strong><small>Bien sûr. Quel jour vous conviendrait ?</small></p></div></div></Frame>}
function BookingView(){return <Frame title="Bookings"><div className={styles.bookingView}><div className={styles.bookingStats}><Stat label="Upcoming" value="9"/><Stat label="Today" value="2"/><Stat label="Confirmed" value="8"/><Stat label="Cancelled" value="1"/></div><div className={styles.bookingRow}><span><CalendarCheck size={16}/></span><div><strong>Consultation with Sarah</strong><small>Wednesday · 13:00 · 30 minutes</small></div><em>Confirmed</em></div><div className={styles.bookingRow}><span><CalendarCheck size={16}/></span><div><strong>Follow-up visit</strong><small>Thursday · 10:30 · 45 minutes</small></div><em>Confirmed</em></div></div></Frame>}
function AnalyticsView(){return <Frame title="Analytics" action="Last 30 days"><div className={styles.analyticsView}><div className={styles.analyticsTop}><Stat label="Total calls" value="430"/><Stat label="Conversion" value="25.9%"/><Stat label="Avg duration" value="1m 27s"/></div><div className={styles.analyticsBottom}><div><b>Calls by day</b><div className={styles.barChart}>{[38,55,43,72,63,86,66,96,79,105,88,114].map((h,i)=><i style={{height:`${h}px`}} key={i}/>)}</div></div><div className={styles.donut}><div/><b>Language breakdown</b><span>FR 72% · EN 23% · Other 5%</span></div></div></div></Frame>}
function IntegrationsView(){return <Frame title="Integrations marketplace"><div className={styles.integrationView}>{[["Google Calendar","/logos/google-calendar.svg","Scheduling"],["WhatsApp","/logos/whatsapp.svg","Messaging"],["HubSpot","/logos/hubspot.svg","CRM"]].map(([name,logo,type],i)=><article key={name}><img src={logo} alt=""/><div><strong>{name}</strong><span>{type}</span></div><em>{i<2?"Connected":"Connect"}</em></article>)}</div></Frame>}
