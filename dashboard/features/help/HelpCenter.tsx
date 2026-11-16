"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowUpRight, Bot, CalendarDays, CreditCard, Mail, Phone, Plug, Search, ShieldCheck } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Input } from "@/components/ui/field";
import { Card } from "@/components/ui/primitives";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { PageHeader, SectionTitle } from "@/components/ui/console";

type Guide = { title: string; summary: string; href: string; icon: LucideIcon; steps: string[] };

const guides: Guide[] = [
  {
    title: "Set up your first agent",
    summary: "Business details, voice, languages and what the agent should say.",
    href: "/agents/new",
    icon: Bot,
    steps: ["Pick a template or start blank", "Fill in business details and opening hours", "Choose a voice and languages", "Run a browser test call"],
  },
  {
    title: "Take bookings into your calendar",
    summary: "Connect a calendar so the agent offers real free slots.",
    href: "/dashboard/integrations",
    icon: CalendarDays,
    steps: ["Open Integrations and connect Google Calendar", "Set the default appointment length", "Enable booking on the agent", "Book a test appointment"],
  },
  {
    title: "Put the agent on a phone number",
    summary: "Get a local number, or forward your existing line.",
    href: "/agents",
    icon: Phone,
    steps: ["Open the agent and go to Channels", "Choose a country and number", "Switch the agent on", "Call the number to check it answers"],
  },
  {
    title: "Connect your other tools",
    summary: "Sheets, CRM, WhatsApp and webhooks for every booking.",
    href: "/dashboard/integrations",
    icon: Plug,
    steps: ["Pick a tool from Integrations", "Authorise access", "Choose which events to send", "Check the delivery log"],
  },
  {
    title: "Understand your bill",
    summary: "Plan minutes, overage and what counts as a call minute.",
    href: "/billing",
    icon: CreditCard,
    steps: ["Minutes are counted per connected call", "Your plan resets each billing cycle", "Alerts fire at 80% and 100%", "Change plans any time"],
  },
  {
    title: "Privacy and recordings",
    summary: "Retention periods, recording consent and deleting data.",
    href: "/settings?tab=privacy",
    icon: ShieldCheck,
    steps: ["Choose how long transcripts are kept", "Decide whether calls are recorded", "Tell callers about recording", "Remove data at its source when needed"],
  },
];

const faqs = [
  { q: "Why didn't my agent book an appointment?", a: "The agent only offers slots it can see as free. Check that a calendar is connected on the agent, that the opening hours cover the time asked for, and that the appointment length fits. The call's transcript in Calls shows exactly what the caller asked for." },
  { q: "Can callers interrupt the agent?", a: "Yes. The agent stops speaking when the caller talks. You can make it more patient or more responsive in Settings → Calls & AI → Interruption sensitivity." },
  { q: "Which languages are supported?", a: "English, Swahili and French work out of the box, and the agent switches language when the caller does. Add languages on the agent's Voice & language step." },
  { q: "How do I hand a call to a person?", a: "Turn on live transfer in the agent's tools, add your team's number and describe when the agent should transfer. If nobody answers, the agent takes a message instead." },
  { q: "What happens when I run out of minutes?", a: "You get an alert at 80% and 100% of your plan. Depending on your billing settings, extra minutes are charged as overage or new calls are paused until the next cycle." },
  { q: "Is my data shared with other businesses?", a: "No. Every workspace is isolated. Transcripts and recordings are only visible to people in your workspace and are deleted after your retention period." },
];

export function HelpCenter() {
  const [query, setQuery] = useState("");
  const needle = query.trim().toLowerCase();
  const visibleGuides = useMemo(
    () => guides.filter((guide) => !needle || [guide.title, guide.summary, ...guide.steps].some((text) => text.toLowerCase().includes(needle))),
    [needle],
  );
  const visibleFaqs = useMemo(
    () => faqs.filter((faq) => !needle || `${faq.q} ${faq.a}`.toLowerCase().includes(needle)),
    [needle],
  );

  return (
    <div className="grid min-w-0 grid-cols-1 gap-8 [&>*]:min-w-0">
      <PageHeader title="Help" description="Guides for the things people set up most, answers to common questions, and a way to reach us." />

      <label className="relative max-w-xl">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
        <Input aria-label="Search help" className="h-10 pl-9 text-[0.9rem]" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search guides and questions" />
      </label>

      <section className="grid gap-3">
        <SectionTitle title="Guides" meta={needle ? `${visibleGuides.length} found` : undefined} />
        {visibleGuides.length ? (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {visibleGuides.map((guide) => (
              <Card key={guide.title} className="group flex flex-col p-4 transition-colors hover:border-line-strong">
                <div className="flex items-start gap-3">
                  <span className="grid size-8 shrink-0 place-items-center rounded-[7px] border border-line bg-surface-2 text-fg-soft">
                    <guide.icon className="size-4" />
                  </span>
                  <div className="min-w-0">
                    <h3 className="m-0 text-[0.9rem] font-medium text-ink">{guide.title}</h3>
                    <p className="m-0 mt-0.5 text-[0.8rem] leading-snug text-fg-muted">{guide.summary}</p>
                  </div>
                </div>
                <ol className="m-0 mt-4 grid list-none gap-1.5 p-0">
                  {guide.steps.map((step, index) => (
                    <li key={step} className="flex gap-2.5 text-[0.82rem] text-fg-soft">
                      <span className="w-4 shrink-0 text-right tabular-nums text-fg-muted">{index + 1}.</span>
                      {step}
                    </li>
                  ))}
                </ol>
                <Link href={guide.href} className="mt-4 inline-flex items-center gap-1 self-start pt-1 text-[0.8rem] font-medium text-ink no-underline hover:underline hover:underline-offset-4">
                  Go there <ArrowUpRight className="size-3.5" />
                </Link>
              </Card>
            ))}
          </div>
        ) : (
          <p className="m-0 text-[0.85rem] text-fg-muted">No guides match &ldquo;{query}&rdquo;.</p>
        )}
      </section>

      <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="grid content-start gap-3">
          <SectionTitle title="Common questions" />
          <Card className="px-5">
            {visibleFaqs.length ? (
              <Accordion type="single" collapsible>
                {visibleFaqs.map((faq) => (
                  <AccordionItem key={faq.q} value={faq.q} className="last:border-0">
                    <AccordionTrigger className="py-3.5 text-[0.9rem]">{faq.q}</AccordionTrigger>
                    <AccordionContent className="pb-4 text-[0.86rem]">{faq.a}</AccordionContent>
                  </AccordionItem>
                ))}
              </Accordion>
            ) : (
              <p className="m-0 py-6 text-[0.85rem] text-fg-muted">No questions match &ldquo;{query}&rdquo;.</p>
            )}
          </Card>
        </div>
        <div className="grid content-start gap-3">
          <SectionTitle title="Still stuck?" />
          <Card className="divide-y divide-line">
            <a href="mailto:support@sauti.uk" className="flex items-center gap-3 px-4 py-3.5 text-inherit no-underline transition-colors hover:bg-surface-2">
              <Mail className="size-4 text-fg-muted" />
              <span className="min-w-0 flex-1">
                <span className="block text-[0.86rem] text-ink">Email support</span>
                <span className="block text-[0.78rem] text-fg-muted">support@sauti.uk · replies within a working day</span>
              </span>
            </a>
            <Link href="/calls" className="flex items-center gap-3 px-4 py-3.5 text-inherit no-underline transition-colors hover:bg-surface-2">
              <Phone className="size-4 text-fg-muted" />
              <span className="min-w-0 flex-1">
                <span className="block text-[0.86rem] text-ink">Send us a call</span>
                <span className="block text-[0.78rem] text-fg-muted">Include the call from Calls so we can see the transcript</span>
              </span>
            </Link>
          </Card>
        </div>
      </section>
    </div>
  );
}
