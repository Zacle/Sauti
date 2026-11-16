"use client";

import Image from "next/image";
import Link from "next/link";
import { useState, type CSSProperties, type MouseEvent } from "react";
import { motion } from "motion/react";
import type { LucideIcon } from "lucide-react";
import {
  ArrowRight,
  ArrowUpRight,
  Check,
  LockKeyhole,
  Mail,
  MessageCircle,
  ShieldCheck,
  UserRoundCheck,
  Workflow,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { PixelGlow, glowPalettes } from "./PixelGlow";
import { Eyebrow, Reveal } from "./motion";

const ease = [0.16, 1, 0.3, 1] as const;

/* ======================================================================
 * Industries: expanding panels (hover/focus to open one) on desktop,
 * a swipeable snap carousel on mobile.
 * ==================================================================== */

const industries = [
  {
    title: "Healthcare",
    text: "Patient bookings, reminders and after-hours calls, without tying up the front desk.",
    image: "/images/marketing/industries/healthcare.png",
    href: "/industries/clinics-healthcare",
    uses: ["Appointment booking", "Reminders", "After-hours calls"],
    quote: "Can I see Dr. Achieng on Thursday morning?",
  },
  {
    title: "Professional services",
    text: "Qualify enquiries and put consultations straight into the right diary.",
    image: "/images/marketing/industries/professional-services.png",
    href: "/industries/professional-services",
    uses: ["Lead qualification", "Consultations", "Client intake"],
    quote: "I need advice on a land transfer. Who should I speak to?",
  },
  {
    title: "Home services",
    text: "Book jobs, collect the details your team needs and keep customers updated.",
    image: "/images/marketing/industries/home-services.png",
    href: "/industries/local-businesses",
    uses: ["Job booking", "Job details", "Status updates"],
    quote: "My water heater stopped working this morning.",
  },
  {
    title: "Retail & ecommerce",
    text: "Answer product, order and availability questions around the clock.",
    image: "/images/marketing/industries/retail.png",
    href: "/industries/local-businesses",
    uses: ["Order questions", "Stock checks", "Store hours"],
    quote: "Do you have the blue one in size 42?",
  },
  {
    title: "Education",
    text: "Handle admissions and campus enquiries in the languages families speak.",
    image: "/images/marketing/industries/education.png",
    href: "/industries/education",
    uses: ["Admissions", "Campus visits", "Multilingual"],
    quote: "When do applications close for September?",
  },
];

export function IndustryShowcase() {
  const [active, setActive] = useState(0);

  return (
    <section className="border-t border-line">
      <div className="mx-auto max-w-[1240px] px-4 py-20 sm:px-8 sm:py-28">
        <Reveal className="flex flex-wrap items-end justify-between gap-6">
          <div className="max-w-xl">
            <Eyebrow>Built for how you work</Eyebrow>
            <h2 className="mt-5 text-4xl font-medium leading-[1.05] tracking-[-0.03em] text-white sm:text-5xl">
              Start with <span className="font-serif font-normal italic text-fg-soft">one journey.</span>
            </h2>
            <p className="mt-5 text-[1.02rem] leading-relaxed text-fg-soft">
              Pick the calls that cost you most today. Expand once your team trusts the results.
            </p>
          </div>
          <Link
            href="/industries"
            className="group inline-flex items-center gap-1.5 rounded-full border border-line px-4 py-2 text-sm text-fg-soft transition-colors hover:border-line-strong hover:text-fg"
          >
            All industries
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
          </Link>
        </Reveal>

        <Reveal delay={0.08}>
          <ul
            className="mt-12 flex gap-3 max-lg:-mx-4 max-lg:snap-x max-lg:snap-mandatory max-lg:scroll-px-4 max-lg:overflow-x-auto max-lg:px-4 max-lg:pb-2 max-lg:[scrollbar-width:none] lg:h-[32rem]"
          >
            {industries.map((industry, index) => {
              const isActive = active === index;
              return (
                <li
                  key={industry.title}
                  data-active={isActive}
                  onMouseEnter={() => setActive(index)}
                  onFocus={() => setActive(index)}
                  style={{ flexGrow: isActive ? 3.4 : 1 } as CSSProperties}
                  className="group relative min-w-0 shrink basis-0 overflow-hidden rounded-3xl border border-line bg-surface transition-[flex-grow,border-color] duration-700 ease-out-expo data-[active=true]:border-line-strong max-lg:h-[30rem] max-lg:min-w-[82%] max-lg:flex-none max-lg:snap-start sm:max-lg:min-w-[60%]"
                >
                  <Link href={industry.href} className="absolute inset-0 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-signal">
                    <Image
                      src={industry.image}
                      alt=""
                      fill
                      sizes="(max-width: 1024px) 82vw, 40vw"
                      className="object-cover transition duration-1000 ease-out-expo lg:group-data-[active=false]:scale-110 lg:group-data-[active=false]:opacity-45 lg:group-data-[active=false]:grayscale"
                    />
                    <div className="absolute inset-0 bg-[linear-gradient(to_top,#000_8%,rgb(0_0_0/0.7)_40%,rgb(0_0_0/0.1)_75%)]" />

                    <span className="absolute left-5 top-5 font-mono text-[0.68rem] text-fg-soft">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <ArrowUpRight
                      className="absolute right-5 top-5 size-5 text-white opacity-0 transition duration-500 group-data-[active=true]:opacity-100 max-lg:opacity-100"
                      aria-hidden="true"
                    />

                    {/* Collapsed label (desktop only) */}
                    <span className="absolute bottom-6 left-5 hidden origin-bottom-left -rotate-90 translate-x-5 whitespace-nowrap text-lg font-medium text-white transition-opacity duration-300 group-data-[active=true]:opacity-0 lg:block">
                      {industry.title}
                    </span>

                    {/* Expanded details */}
                    <div className="absolute inset-x-0 bottom-0 p-6 transition-opacity duration-500 lg:w-[30rem] lg:group-data-[active=false]:pointer-events-none lg:group-data-[active=false]:opacity-0 lg:group-data-[active=false]:duration-150 lg:group-data-[active=true]:delay-300">
                      <div className="mb-5 inline-flex max-w-full items-center gap-2 rounded-2xl rounded-bl-md border border-white/10 bg-black/50 px-3.5 py-2 text-sm text-fg backdrop-blur-md">
                        <span className="size-1.5 shrink-0 rounded-full bg-signal" />
                        <span className="truncate">&ldquo;{industry.quote}&rdquo;</span>
                      </div>
                      <h3 className="text-2xl font-medium tracking-tight text-white">{industry.title}</h3>
                      <p className="mt-2 max-w-sm text-[0.93rem] leading-relaxed text-fg-soft">{industry.text}</p>
                      <ul className="mt-4 flex flex-wrap gap-1.5">
                        {industry.uses.map((use) => (
                          <li
                            key={use}
                            className="rounded-full border border-white/10 bg-white/[0.06] px-2.5 py-1 font-mono text-[0.62rem] uppercase tracking-[0.12em] text-fg-soft"
                          >
                            {use}
                          </li>
                        ))}
                      </ul>
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
        </Reveal>
      </div>
    </section>
  );
}

/* ======================================================================
 * Trust: four spotlight cards
 * ==================================================================== */

const trust: Array<{ icon: LucideIcon; title: string; text: string; tag: string }> = [
  { icon: ShieldCheck, title: "Isolated workspaces", text: "Each business's calls, contacts and settings are scoped to its own workspace.", tag: "tenant-scoped" },
  { icon: LockKeyhole, title: "Encrypted credentials", text: "Provider keys are encrypted when saved and never shown again in the dashboard.", tag: "write-only secrets" },
  { icon: Workflow, title: "Explicit permissions", text: "An agent can only use the tools you switch on for it. Nothing else.", tag: "per-agent tools" },
  { icon: UserRoundCheck, title: "Human handoff", text: "Transfer rules send sensitive or out-of-scope calls to the right person.", tag: "transfer rules" },
];

export function TrustSection() {
  return (
    <section className="relative isolate overflow-hidden border-t border-line">
      <PixelGlow
        seed={4}
        palette={glowPalettes.ocean}
        intensity={0.55}
        className="pointer-events-none absolute -left-48 -top-10 -z-10 h-[26rem] w-[52rem] max-w-[160%] [mask-image:radial-gradient(closest-side,black_20%,transparent)]"
      />
      <div className="mx-auto max-w-[1240px] px-4 py-20 sm:px-8 sm:py-28">
        <Reveal className="max-w-2xl">
          <Eyebrow>Built for trust</Eyebrow>
          <h2 className="mt-5 text-4xl font-medium leading-[1.05] tracking-[-0.03em] text-white sm:text-5xl">
            Safe actions. <span className="block font-serif font-normal italic text-fg-soft">Human when it matters.</span>
          </h2>
          <p className="mt-5 max-w-lg text-[1.02rem] leading-relaxed text-fg-soft">
            Clear boundaries protect your data, your customers and every action an agent takes.
          </p>
        </Reveal>

        <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {trust.map(({ icon: Icon, title, text, tag }, index) => (
            <Reveal key={title} delay={index * 0.06} className="h-full">
              <SpotlightCard>
                <span className="grid size-11 place-items-center rounded-2xl border border-signal/25 bg-signal/10 text-signal shadow-[0_0_30px_-8px_rgb(94_234_212/0.5)]">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <h3 className="mt-8 text-lg font-medium tracking-tight text-white">{title}</h3>
                <p className="mt-2 text-[0.92rem] leading-relaxed text-fg-soft">{text}</p>
                <p className="mt-6 flex items-center gap-1.5 border-t border-line pt-4 font-mono text-[0.62rem] uppercase tracking-[0.14em] text-fg-muted">
                  <Check className="size-3 text-signal" aria-hidden="true" /> {tag}
                </p>
              </SpotlightCard>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}

function SpotlightCard({ children }: { children: React.ReactNode }) {
  const onMove = (event: MouseEvent<HTMLElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    event.currentTarget.style.setProperty("--x", `${event.clientX - rect.left}px`);
    event.currentTarget.style.setProperty("--y", `${event.clientY - rect.top}px`);
  };
  return (
    <article
      onMouseMove={onMove}
      className="group relative isolate flex h-full flex-col overflow-hidden rounded-3xl border border-line bg-[linear-gradient(180deg,#0d0d0d,#060606)] p-6 transition-colors duration-500 hover:border-signal/25 sm:p-7"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 opacity-0 transition-opacity duration-500 group-hover:opacity-100"
        style={{ background: "radial-gradient(360px circle at var(--x, 50%) var(--y, 0%), rgb(94 234 212 / 0.09), transparent 65%)" }}
      />
      {children}
    </article>
  );
}

/* ======================================================================
 * FAQ: heading + contact card, accordion on the right
 * ==================================================================== */

const faq = [
  ["How quickly can I get started?", "Create an agent, configure its behaviour and tools, then test it from your browser before enabling a phone number or WhatsApp."],
  ["Which languages does Sauti speak?", "Agents can be configured for English, Kiswahili, French and more, and can switch when a caller does mid-conversation."],
  ["Is my data secure?", "Workspace data is tenant-scoped, provider credentials are encrypted, and agents only use integrations explicitly enabled for them."],
  ["What if Sauti can't handle a request?", "Set transfer and escalation rules so sensitive or out-of-scope calls reach the right person on your team."],
  ["Can I customise Sauti for my business?", "Yes. Set the agent's identity, languages, knowledge, voice, business rules, tools and post-call workflows."],
] as const;

export function FaqSection() {
  return (
    <section className="border-t border-line">
      <div className="mx-auto grid max-w-[1240px] grid-cols-1 gap-12 px-4 py-20 sm:px-8 sm:py-28 lg:grid-cols-[0.8fr_1.2fr] lg:gap-20 [&>*]:min-w-0">
        <Reveal className="lg:sticky lg:top-28 lg:self-start">
          <Eyebrow>FAQ</Eyebrow>
          <h2 className="mt-5 text-4xl font-medium leading-[1.05] tracking-[-0.03em] text-white sm:text-5xl">
            Questions, <span className="font-serif font-normal italic text-fg-soft">answered.</span>
          </h2>
          <div className="mt-10 rounded-3xl border border-line bg-[linear-gradient(180deg,#0d0d0d,#060606)] p-6">
            <span className="grid size-10 place-items-center rounded-xl bg-white/[0.05] text-fg">
              <MessageCircle className="size-[18px]" aria-hidden="true" />
            </span>
            <p className="mt-5 text-[1.02rem] font-medium text-white">Still have a question?</p>
            <p className="mt-1.5 text-sm leading-relaxed text-fg-soft">
              Tell us about your calls and we&rsquo;ll show you how Sauti would handle them.
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              <Button asChild size="sm">
                <Link href="/request-demo">
                  Book a demo <ArrowRight aria-hidden="true" />
                </Link>
              </Button>
              <Button asChild size="sm" variant="secondary">
                <a href="mailto:support@sauti.uk">
                  <Mail aria-hidden="true" /> Email us
                </a>
              </Button>
            </div>
          </div>
        </Reveal>

        <Reveal delay={0.08}>
          <Accordion type="single" collapsible defaultValue={faq[0][0]} className="border-t border-line">
            {faq.map(([question, answer], index) => (
              <AccordionItem key={question} value={question}>
                <AccordionTrigger className="py-6 text-[1.08rem]">
                  <span className="flex items-baseline gap-4">
                    <span className="font-mono text-[0.68rem] text-fg-muted">{String(index + 1).padStart(2, "0")}</span>
                    {question}
                  </span>
                </AccordionTrigger>
                <AccordionContent className="pl-9">{answer}</AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </Reveal>
      </div>
    </section>
  );
}

/* ======================================================================
 * Closing call to action
 * ==================================================================== */

const promises = ["No code required", "Test from your browser", "Human handoff built in"];

export function ClosingCta() {
  return (
    <section className="relative isolate overflow-hidden border-t border-line">
      <PixelGlow
        seed={5}
        palette={glowPalettes.dusk}
        block={22}
        className="pointer-events-none absolute inset-x-0 bottom-0 -z-10 mx-auto h-full w-full max-w-[1400px] [mask-image:radial-gradient(52%_42%_at_50%_56%,black_25%,transparent)]"
      />
      <Reveal className="mx-auto flex max-w-3xl flex-col items-center px-4 py-24 text-center sm:py-32">
        <Eyebrow>Ready when you are</Eyebrow>
        <h2 className="mt-6 text-4xl font-medium leading-[1.02] tracking-[-0.04em] text-white sm:text-6xl">
          Hear it on <span className="font-serif font-normal italic text-fg-soft">your</span> calls.
        </h2>
        <p className="mt-6 max-w-md text-[1.05rem] leading-relaxed text-fg-soft">
          We&rsquo;ll configure an agent for one of your customer journeys and walk you through it live.
        </p>
        <div className="mt-10 flex flex-wrap justify-center gap-3">
          <Button asChild size="lg">
            <Link href="/request-demo">
              Book a demo <ArrowRight aria-hidden="true" />
            </Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link href="/pricing">View pricing</Link>
          </Button>
        </div>
        <ul className="mt-10 flex flex-wrap justify-center gap-x-6 gap-y-2">
          {promises.map((item, index) => (
            <motion.li
              key={item}
              initial={{ opacity: 0, y: 6 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: 0.3 + index * 0.08, ease }}
              className="flex items-center gap-1.5 text-sm text-fg-soft"
            >
              <Check className="size-3.5 text-signal" aria-hidden="true" />
              {item}
            </motion.li>
          ))}
        </ul>
      </Reveal>
    </section>
  );
}

