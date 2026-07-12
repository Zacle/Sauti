"use client";

import Link from "next/link";
import {
  Activity, ArrowRight, AudioWaveform, BarChart3, Bot, CalendarCheck, Check, CheckCircle2,
  Clock3, Database, Globe2, Headphones, Languages, MessageSquareText, Mic2,
  PhoneCall, Play, PlugZap, Radio, ShieldCheck, Sparkles, Users,
} from "lucide-react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import styles from "./ProductHome.module.css";

const flow = [
  [Bot, "Design", "Give your agent a role, voice, knowledge, languages, and guardrails."],
  [PhoneCall, "Connect", "Turn on browser voice or phone calls and connect the tools it may use."],
  [AudioWaveform, "Converse", "Listen, respond, handle interruptions, and capture caller details in real time."],
  [PlugZap, "Act", "Check availability, book appointments, update records, and trigger follow-ups."],
  [BarChart3, "Improve", "Review transcripts, outcomes, bookings, and operational trends in one place."],
] as const;

export default function ProductHome() {
  return <main className={styles.page}>
    <section className={styles.hero}>
      <div className={styles.heroGlow} data-parallax/><div className={styles.heroGrid}/>
      <div className={styles.heroCopy} data-reveal>
        <div className={styles.eyebrow}><span><Radio size={13}/></span> The operating system for AI conversations</div>
        <h1>Build voice agents that <em>listen, act, and improve.</em></h1>
        <p>Sauti brings live voice, business knowledge, scheduling, integrations, and conversation intelligence into one workspace—so every call can move work forward.</p>
        <div className={styles.actions}>
          <Link className={styles.primary} href="/register">Create your first agent <ArrowRight size={17}/></Link>
          <a className={styles.secondary} href="#product-tour"><Play size={16}/> Explore the product</a>
        </div>
        <div className={styles.heroProof}><span><Check size={14}/> Multilingual voice</span><span><Check size={14}/> Human handoff</span><span><Check size={14}/> Tenant-isolated data</span></div>
      </div>
      <div data-parallax className={styles.heroProduct}><CommandCenterPreview /></div>
    </section>

    <section className={styles.problemSection}>
      <div className={styles.sectionHead} data-reveal><span>From conversation to outcome</span><h2>More than a voice. A complete operating loop.</h2><p>Most voice demos stop after the AI replies. Sauti is designed around what happens before, during, and after every customer conversation.</p></div>
      <div className={styles.flow}>
        {flow.map(([Icon,title,text],index)=><article key={title} data-reveal style={{transitionDelay:`${index*70}ms`}}><span>0{index+1}</span><div><Icon size={21}/></div><h3>{title}</h3><p>{text}</p>{index<flow.length-1&&<ArrowRight className={styles.flowArrow} size={18}/>}</article>)}
      </div>
    </section>

    <section className={styles.tour} id="product-tour">
      <div className={styles.sectionHead} data-reveal><span>Inside Sauti</span><h2>Everything your team needs to run AI calls.</h2><p>Purpose-built surfaces keep configuration, live operations, and business outcomes connected without overwhelming operators.</p></div>
      <div className={styles.storyRail} aria-hidden="true"><i/></div>
      <ProductStory index="01" eyebrow="Agent Studio" title="Shape the agent around your business—not the other way around." text="Configure identity, language, voice, behavior, knowledge, call handling, tools, and post-call workflows from one focused studio." bullets={["Test the agent before going live","Assign integrations per agent","Keep prompts and business context editable"]} preview={<AgentStudioPreview/>}/>
      <ProductStory index="02" reverse eyebrow="Live conversations" title="See what the agent hears, says, and does." text="Follow the transcript, call state, captured intent, and routed actions while the conversation is happening. Interruption-aware voice keeps callers in control." bullets={["Realtime caller and agent transcript","Barge-in and human-transfer controls","Recordings, summaries, and collected details"]} preview={<CallPreview/>}/>
      <ProductStory index="03" eyebrow="Operations & analytics" title="Turn conversations into decisions your team can use." text="Track outcomes across agents and channels, inspect individual calls, review booking conversion, and understand where callers need help." bullets={["Call, answer, booking, and duration trends","Agent and intent breakdowns","Searchable call history with transcript detail"]} preview={<AnalyticsPreview/>}/>
    </section>

    <section className={styles.integrations}>
      <div data-reveal><span className={styles.kicker}>Connected work</span><h2>Your agent can do more than answer.</h2><p>Connect the systems your business already uses. Sauti gives each agent only the tools you enable and records the outcome after the call.</p><Link href="/integrations">Explore all integrations <ArrowRight size={16}/></Link></div>
      <IntegrationMap/>
    </section>

    <section className={styles.useCases}>
      <div className={styles.sectionHead} data-reveal><span>Built for real operations</span><h2>One platform, many call workflows.</h2></div>
      <div className={styles.caseGrid}>
        <UseCase icon={CalendarCheck} title="Appointments" text="Check availability, book, reschedule, cancel, and send confirmation." tags={["Clinics","Salons","Fitness"]}/>
        <UseCase icon={Headphones} title="Customer support" text="Answer known questions, look up configured data, and escalate safely." tags={["Service teams","Retail","Property"]}/>
        <UseCase icon={Users} title="Lead qualification" text="Capture intent and contact details, qualify the opportunity, and route follow-up." tags={["Sales","Real estate","Financial services"]}/>
      </div>
    </section>

    <section className={styles.trust}>
      <div className={styles.trustCopy}><span className={styles.kicker}>Control by design</span><h2>Your business stays in charge.</h2><p>AI should extend your team without becoming a black box. Sauti keeps access, data, and actions explicit at the workspace and agent level.</p></div>
      <div className={styles.trustGrid}><TrustItem icon={ShieldCheck} title="Tenant isolation" text="Customer data and provider connections stay scoped to the workspace."/><TrustItem icon={PlugZap} title="Explicit tools" text="Agents can only use integrations and actions that operators enable."/><TrustItem icon={Users} title="Human fallback" text="Escalation and transfer paths keep people available for sensitive cases."/><TrustItem icon={MessageSquareText} title="Reviewable history" text="Transcripts, outcomes, and tool activity make conversations auditable."/></div>
    </section>

    <section className={styles.cta}>
      <div className={styles.ctaOrb}><BrandLogo size={52}/></div><span>Ready to put every conversation to work?</span><h2>Launch an agent your team can actually operate.</h2><p>Start with a template, connect your workflow, and test the full conversation before it reaches a customer.</p><div className={styles.actions}><Link className={styles.primary} href="/register">Create an agent <ArrowRight size={17}/></Link><a className={styles.secondary} href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Book a demo</a></div>
    </section>
  </main>;
}

function ProductStory({index,eyebrow,title,text,bullets,preview,reverse=false}:{index:string;eyebrow:string;title:string;text:string;bullets:string[];preview:React.ReactNode;reverse?:boolean}){return <article className={`${styles.story} ${reverse?styles.reverse:""}`} data-scroll-progress><div className={styles.storyMarker}>{index}</div><div className={styles.storyCopy} data-reveal><span>{eyebrow}</span><h3>{title}</h3><p>{text}</p><ul>{bullets.map(item=><li key={item}><CheckCircle2 size={17}/>{item}</li>)}</ul></div><div className={styles.storyPreview} data-reveal>{preview}</div></article>}

function CommandCenterPreview(){return <div className={styles.commandWrap} data-reveal><div className={styles.floatBadge}><span/><div><small>Agent status</small><strong>Ready for calls</strong></div></div><div className={styles.command}>
  <div className={styles.commandTop}><div className={styles.miniBrand}><BrandLogo size={27}/><strong>Sauti</strong></div><div className={styles.search}>Search calls, agents, bookings…</div><span className={styles.liveBadge}><i/> Live</span></div>
  <div className={styles.commandBody}><aside><b>Workspace</b>{([[Activity,"Overview"],[Bot,"Agents"],[PhoneCall,"Calls"],[CalendarCheck,"Bookings"],[BarChart3,"Analytics"]] as const).map(([Icon,label],i)=><div className={i===0?styles.activeNav:""} key={label}><Icon size={15}/>{label}</div>)}</aside><section><div className={styles.welcome}><div><small>Good morning</small><h3>Your voice operation</h3></div><button>+ Create agent</button></div><div className={styles.statRow}><MiniStat label="Calls today" value="128" delta="+18%"/><MiniStat label="Bookings" value="34" delta="+12%"/><MiniStat label="Answer rate" value="82%" delta="+7%"/></div><div className={styles.commandPanels}><div className={styles.chart}><div><strong>Call activity</strong><span>Last 14 days</span></div><svg viewBox="0 0 440 130" preserveAspectRatio="none"><defs><linearGradient id="area" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#26e6d2" stopOpacity=".38"/><stop offset="1" stopColor="#26e6d2" stopOpacity="0"/></linearGradient></defs><path d="M0 108 C35 100 38 47 78 69 S125 112 157 61 S210 105 241 72 S287 30 319 61 S365 83 393 39 S425 55 440 21 L440 130 L0 130Z" fill="url(#area)"/><path d="M0 108 C35 100 38 47 78 69 S125 112 157 61 S210 105 241 72 S287 30 319 61 S365 83 393 39 S425 55 440 21" fill="none" stroke="#26e6d2" strokeWidth="3"/></svg></div><div className={styles.activity}><strong>Live activity</strong><Event icon={PhoneCall} title="Incoming call" text="Consultation"/><Event icon={CalendarCheck} title="Booking created" text="Today, 14:30"/><Event icon={MessageSquareText} title="Call completed" text="Lead qualified"/></div></div></section></div>
  </div><div className={styles.floatOutcome}><CalendarCheck size={18}/><div><small>Outcome</small><strong>Appointment booked</strong></div></div></div>}
function MiniStat({label,value,delta}:{label:string;value:string;delta:string}){return <div><span>{label}</span><strong>{value}</strong><small>{delta}</small></div>}
function Event({icon:Icon,title,text}:{icon:typeof PhoneCall;title:string;text:string}){return <div><span><Icon size={14}/></span><p><strong>{title}</strong><small>{text}</small></p><i/></div>}

function AgentStudioPreview(){return <div className={styles.preview}><div className={styles.previewBar}><span/><span/><span/><strong>Agent Studio · Amélie</strong><em>Saved</em></div><div className={styles.studioBody}><aside>{["Identity","Voice","Instructions","Knowledge","Integrations","Call handling"].map((x,i)=><span className={i===2?styles.selected:""} key={x}>{x}</span>)}</aside><div className={styles.form}><div className={styles.formHead}><div><span>Instructions</span><strong>What should Amélie know and do?</strong></div><button>Test agent</button></div><label>Agent role<div>Front-desk voice agent for MediCare Clinic</div></label><label>System instructions<div className={styles.prompt}>You are Amélie, the welcoming first point of contact. Help callers understand services, collect only required details, and book an available consultation…<i/></div></label><div className={styles.formRow}><label>Primary language<div>French</div></label><label>Voice<div>Warm · Conversational</div></label></div></div></div></div>}
function CallPreview(){return <div className={`${styles.preview} ${styles.callPanel}`}><div className={styles.previewBar}><span/><span/><span/><strong>Live conversation</strong><em><i/> Connected</em></div><div className={styles.callBody}><div className={styles.callMeta}><div className={styles.avatar}>A</div><div><strong>Amélie</strong><span>Appointment agent · French</span></div><button>Transfer</button></div><div className={styles.messages}><div className={styles.agentMsg}><small>Amélie · 00:08</small><p>Bonjour, c&apos;est Amélie de MediCare. Comment puis-je vous aider ?</p></div><div className={styles.callerMsg}><small>Caller · 00:15</small><p>Je voudrais prendre une consultation jeudi après-midi.</p></div><div className={styles.agentMsg}><small>Amélie · 00:21</small><p>Bien sûr. J&apos;ai un créneau à 14h30. Est-ce que cela vous convient ?</p></div></div><div className={styles.callAction}><span><CalendarCheck size={16}/></span><div><small>Tool completed</small><strong>Availability checked · Thursday 14:30</strong></div><em>286ms</em></div><div className={styles.listen}><AudioWaveform size={18}/><div><strong>Listening</strong><span>Caller can interrupt at any time</span></div><b><i/><i/><i/><i/><i/></b></div></div></div>}
function AnalyticsPreview(){return <div className={styles.preview}><div className={styles.previewBar}><span/><span/><span/><strong>Conversation intelligence</strong><em>Last 14 days</em></div><div className={styles.analyticsBody}><div className={styles.analyticsStats}><MiniStat label="Conversations" value="1,248" delta="+18%"/><MiniStat label="Booking rate" value="25.9%" delta="+4.2%"/><MiniStat label="Avg. duration" value="1m 42s" delta="-8s"/></div><div className={styles.analyticsGrid}><div className={styles.bigChart}><strong>Outcomes over time</strong><div className={styles.bars}>{[42,65,48,82,69,95,73,108,88,116,94,124].map((h,i)=><i key={i} style={{height:`${h}px`}}/>)}</div><div className={styles.axis}><span>Mon</span><span>Wed</span><span>Fri</span><span>Sun</span></div></div><div className={styles.funnel}><strong>Conversion funnel</strong>{[["Calls handled","1,248","100%"],["Qualified","672","53.8%"],["Booked","324","25.9%"]].map(([a,b,c],i)=><div key={a}><span>{a}</span><b>{b}</b><i><em style={{width:c}}/></i><small>{c}</small></div>)}</div></div></div></div>}

function IntegrationMap(){const items=[["Google Calendar","/logos/google-calendar.svg"],["Google Sheets","/logos/google-sheets.svg"],["HubSpot","/logos/hubspot.svg"],["Salesforce","/logos/salesforce.svg"],["Slack","/logos/slack.svg"],["Twilio","/logos/twilio.svg"]];return <div className={styles.integrationMap} data-reveal><div className={styles.mapCore}><BrandLogo size={42}/><strong>Sauti agent</strong><span>Approved tools</span></div>{items.map(([name,logo],i)=><div className={styles.mapItem} style={{"--n":i} as React.CSSProperties} key={name}><img src={logo} alt=""/><span>{name}</span></div>)}<svg viewBox="0 0 600 420" aria-hidden="true"><ellipse cx="300" cy="210" rx="230" ry="145"/><ellipse cx="300" cy="210" rx="165" ry="105"/></svg></div>}
function UseCase({icon:Icon,title,text,tags}:{icon:typeof CalendarCheck;title:string;text:string;tags:string[]}){return <article data-reveal><div><Icon size={23}/></div><h3>{title}</h3><p>{text}</p><footer>{tags.map(x=><span key={x}>{x}</span>)}</footer><ArrowRight size={18}/></article>}
function TrustItem({icon:Icon,title,text}:{icon:typeof ShieldCheck;title:string;text:string}){return <article><Icon size={22}/><div><h3>{title}</h3><p>{text}</p></div></article>}
