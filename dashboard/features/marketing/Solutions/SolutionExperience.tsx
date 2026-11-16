"use client";

import type { ReactNode } from "react";
import { BookOpen, CalendarDays, Check, CheckCheck, Clock3, Globe2, MessageSquare, Moon, Phone, PhoneForwarded, ShieldCheck, UserRound } from "lucide-react";
import { Badge } from "@/components/ui/primitives";
import { InteractiveDemo } from "./InteractiveDemo";
import { cn } from "@/lib/utils";
import { Section, SectionHeading } from "../shared/primitives";
import { walkthroughs, type SolutionSlug } from "./walkthrough-content";

const panel = "rounded-2xl border border-line bg-surface p-5 sm:p-6";
const micro = "font-mono text-[0.65rem] uppercase tracking-[0.14em] text-fg-muted";

function Caption({ children }: { children: ReactNode }) { return <p className={micro}>{children}</p>; }

/** Purpose-built visuals rather than screenshots: text remains readable at every size. */
function CalendarPreview({ compact = false }: { compact?: boolean }) {
  return <div className={panel}>
    <div className="flex items-center justify-between gap-3"><CalendarDays className="size-5 text-signal" /><Badge tone="signal">Confirmed</Badge></div>
    <p className="mt-5 text-lg font-medium text-white">Friday, 14 June</p><p className="mt-1 text-xs text-fg-muted">Illustrative schedule · Africa/Nairobi</p>
    <div className="mt-6 grid grid-cols-[3rem_1fr] gap-x-3 text-xs">
      {(compact ? ["09:30", "10:00", "10:30"] : ["09:00", "09:30", "10:00", "10:30"]).map(time => <div className="contents" key={time}>
        <span className="pt-3 font-mono text-fg-muted">{time}</span>
        <div className={cn("min-h-12 border-t border-line p-3", time === "10:00" && "rounded-xl border border-signal/30 bg-signal/10")}>
          {time === "10:00" ? <><p className="font-medium text-signal">Dental cleaning <CheckCheck className="ml-1 inline size-3.5" /></p><p className="mt-1 text-fg-soft">Amani · 30 min</p></> : <span className="text-fg-muted">{time === "09:30" ? "Unavailable" : "Available"}</span>}
        </div>
      </div>)}
    </div>
  </div>;
}

function KnowledgePreview() {
  return <div className={panel}>
    <div className="flex items-center gap-3"><span className="rounded-xl bg-info/10 p-3 text-info"><BookOpen className="size-5" /></span><div><p className="text-sm text-white">Front-desk guide</p><p className="mt-1 text-xs text-fg-muted">Approved business information</p></div></div>
    <div className="mt-6 space-y-4 border-l border-line-strong pl-4"><Caption>Opening hours</Caption><p className="text-sm text-fg-soft">Monday–Friday <span className="float-right">09:00–17:00</span></p><p className="-ml-2 rounded-lg border border-signal/25 bg-signal/10 p-2 text-sm text-signal">Saturday <span className="float-right">09:00–13:00</span></p><p className="text-sm text-fg-soft">Sunday <span className="float-right">Closed</span></p></div>
    <div className="mt-6 flex gap-3 rounded-xl bg-white/[0.03] p-4 text-xs leading-relaxed text-fg-soft"><ShieldCheck className="size-4 shrink-0 text-signal" />An exception to this policy? Refer it to the team.</div>
  </div>;
}

function LeadPreview() {
  return <div className={panel}>
    <div className="flex items-center gap-3"><span className="grid size-11 place-items-center rounded-full bg-violet/15 text-violet"><UserRound className="size-5" /></span><div><p className="font-medium text-white">New rental enquiry</p><p className="text-xs text-fg-muted">Example call summary</p></div></div>
    <dl className="mt-6 divide-y divide-line">{[["Looking for", "3-bedroom rental"], ["Location", "Kilimani"], ["Monthly budget", "KES 120,000"], ["Moving", "Next month"]].map(([label,value]) => <div key={label} className="flex justify-between gap-4 py-3 text-sm"><dt className="text-fg-muted">{label}</dt><dd className="text-right text-fg">{value}</dd></div>)}</dl>
    <div className="mt-4 flex items-center gap-2 text-xs text-violet"><CheckCheck className="size-4" />Details ready for your team to review</div>
  </div>;
}

function TransferPreview() {
  return <div className={panel}><Caption>The escalation path</Caption><div className="mt-6 space-y-0">
    {[[Phone, "Caller asks for a person", "“Can I speak to your team?”"], [PhoneForwarded, "Agent offers a transfer", "Your configured destination"], [UserRound, "Your team takes it from here", "Test availability before launch"]].map(([Icon,title,text],i) => { const Glyph = Icon as typeof Phone; return <div key={String(title)}><div className="flex items-center gap-4"><span className={cn("grid size-11 shrink-0 place-items-center rounded-xl border", i === 1 ? "border-signal/40 bg-signal/10 text-signal" : "border-line bg-white/[0.03] text-fg-soft")}><Glyph className="size-5" /></span><div><p className="text-sm font-medium text-white">{String(title)}</p><p className="mt-1 text-xs text-fg-muted">{String(text)}</p></div></div>{i < 2 && <div className="ml-[21px] h-7 border-l border-dashed border-line-strong" />}</div>; })}
    </div><p className="mt-6 border-t border-line pt-4 text-xs leading-relaxed text-fg-soft">If unavailable: follow the fallback instructions in your agent’s Prompt.</p></div>;
}

function LanguagesPreview() {
  return <div className={panel}><div className="flex items-center justify-between"><Globe2 className="size-6 text-signal" /><Badge>One agent</Badge></div><p className="mt-5 text-xl font-medium text-white">A familiar voice.<br /><span className="font-serif text-3xl italic text-fg-soft">In their language.</span></p><div className="mt-6 space-y-3">{[["EN", "English", "Hello, how can I help?"], ["SW", "Kiswahili", "Habari, nikusaidie vipi?"], ["FR", "Français", "Bonjour, comment vous aider ?"]].map(([code,name,greeting]) => <div key={code} className="flex gap-3 rounded-xl border border-line p-3"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-signal/10 font-mono text-xs text-signal">{code}</span><div><p className="text-sm text-white">{name}</p><p className="mt-1 text-xs text-fg-soft">{greeting}</p></div></div>)}</div></div>;
}

function HoursPreview({ message = "Please call me tomorrow morning." }: { message?: string }) {
  return <div className={panel}><div className="flex items-center justify-between"><Moon className="size-5 text-violet" /><Badge tone="violet">After hours</Badge></div><p className="mt-5 font-mono text-5xl tracking-tight text-white">21:42<span className="ml-2 text-sm text-fg-muted">EAT</span></p><p className="mt-2 text-sm text-fg-soft">The office is closed. Sauti can still answer.</p><div className="mt-7"><div className="flex justify-between font-mono text-[0.65rem] text-fg-muted"><span>00:00</span><span>09:00</span><span>17:00</span><span>24:00</span></div><div className="mt-3 flex h-8 overflow-hidden rounded-lg border border-line"><div className="w-[37.5%] bg-violet/15" /><div className="w-1/3 bg-signal/60" /><div className="flex-1 bg-violet/15" /></div><div className="mt-3 flex justify-between text-xs"><span className="text-signal">Office · 09:00–17:00</span><span className="text-violet">After-hours policy</span></div></div><div className="mt-6 rounded-xl border border-line p-4 text-sm"><MessageSquare className="mr-2 inline size-4 text-violet" />“{message}”</div></div>;
}

export function SolutionVisual({ slug }: { slug: SolutionSlug }) {
  return <div className="w-full min-w-0">{slug === "appointment-booking" ? <CalendarPreview compact /> : slug === "customer-service" ? <KnowledgePreview /> : slug === "lead-qualification" ? <LeadPreview /> : slug === "human-transfer" ? <TransferPreview /> : slug === "multilingual-call-handling" ? <LanguagesPreview /> : <HoursPreview />}</div>;
}

export function SolutionExperience({ slug }: { slug: SolutionSlug }) {
  const item = walkthroughs[slug];

  return <>
    <Section id="product-walkthrough" bordered={false}>
      <SectionHeading eyebrow="Inside Sauti" title={item.heading} accent={item.accent} description={item.intro} />
      <InteractiveDemo key={slug} slug={slug} />
    </Section>
    <Section>
      <div className="grid gap-10 lg:grid-cols-[0.85fr_1.15fr]">
        <div><SectionHeading eyebrow="Before your first real call" title="Try the" accent="tricky case." /><p className="mt-5 max-w-md text-sm leading-relaxed text-fg-soft">Use Test call in the agent editor. A successful happy path is only half the check.</p><ul className="mt-7 space-y-4">{item.ready.map(text => <li key={text} className="flex gap-3 text-sm text-fg-soft"><Check className="size-4 shrink-0 text-signal" />{text}</li>)}</ul></div>
        <div className="overflow-hidden rounded-3xl border border-line bg-surface"><div className="flex items-center gap-2 border-b border-line px-6 py-4 text-xs text-fg-muted"><Phone className="size-4" />Test call · Suggested scenario</div><div className="p-6 sm:p-8"><Caption>01 / Ask this</Caption><p className="mt-4 text-2xl font-medium leading-snug tracking-tight text-white">{item.test}</p><div className="my-6 h-8 border-l border-dashed border-signal/40" /><Caption>02 / Check this</Caption><p className="mt-4 text-lg leading-relaxed text-fg-soft">{item.expected}</p><div className="mt-7 flex items-center gap-2 text-xs text-signal"><Clock3 className="size-4" />Review the transcript before going live</div></div></div>
      </div>
    </Section>
  </>;
}
