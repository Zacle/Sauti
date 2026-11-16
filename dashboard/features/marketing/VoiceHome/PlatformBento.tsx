"use client";

import Image from "next/image";
import { useRef, type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { motion, useInView, useReducedMotion } from "motion/react";
import NumberFlow from "@number-flow/react";
import type { LucideIcon } from "lucide-react";
import { Activity, AudioLines, CalendarCheck, FlaskConical, Mic, Webhook } from "lucide-react";
import { Reveal } from "./motion";

const ease = [0.16, 1, 0.3, 1] as const;

/**
 * "The platform" bento: four cards, each with a small live illustration of the
 * product instead of a bare icon, a per-card accent colour, and a soft
 * spotlight that follows the cursor.
 */
export function PlatformBento() {
  return (
    <div className="mt-14 grid grid-cols-1 gap-4 lg:grid-cols-6">
      <Reveal className="lg:col-span-4">
        <BentoCard
          accent="167 139 250"
          icon={AudioLines}
          step="01"
          title="Agent studio"
          text="Write the instructions, pick a voice and languages, and add your knowledge. Templates get you to a first draft in minutes."
        >
          <StudioVisual />
        </BentoCard>
      </Reveal>
      <Reveal className="lg:col-span-2" delay={0.06}>
        <BentoCard
          accent="94 234 212"
          icon={FlaskConical}
          step="02"
          title="Test before you go live"
          text="Talk to your agent from the browser and read back what it heard and did."
        >
          <TestCallVisual />
        </BentoCard>
      </Reveal>
      <Reveal className="lg:col-span-2" delay={0.12}>
        <BentoCard
          accent="251 191 36"
          icon={CalendarCheck}
          step="03"
          title="Tools that do the work"
          text="Book, reschedule, message or call your webhook, only with permissions you grant."
        >
          <ToolsVisual />
        </BentoCard>
      </Reveal>
      <Reveal className="lg:col-span-4" delay={0.18}>
        <BentoCard
          accent="96 165 250"
          icon={Activity}
          step="04"
          title="Every call, reviewable"
          text="Recordings, transcripts, outcomes and handle times in one place, so you can see what works and fix what doesn't."
        >
          <AnalyticsVisual />
        </BentoCard>
      </Reveal>
    </div>
  );
}

function BentoCard({
  accent,
  icon: Icon,
  step,
  title,
  text,
  children,
}: {
  /** Space-separated RGB channels, e.g. "167 139 250". */
  accent: string;
  icon: LucideIcon;
  step: string;
  title: string;
  text: string;
  children: ReactNode;
}) {
  const onMove = (event: MouseEvent<HTMLElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    event.currentTarget.style.setProperty("--x", `${event.clientX - rect.left}px`);
    event.currentTarget.style.setProperty("--y", `${event.clientY - rect.top}px`);
  };

  return (
    <article
      onMouseMove={onMove}
      style={{ "--accent": accent } as CSSProperties}
      className="group relative isolate flex h-full flex-col overflow-hidden rounded-3xl border border-line bg-[linear-gradient(180deg,#0d0d0d,#060606)] transition-colors duration-500 hover:border-[rgb(var(--accent)/0.28)]"
    >
      {/* cursor spotlight */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 opacity-0 transition-opacity duration-500 group-hover:opacity-100"
        style={{
          background:
            "radial-gradient(420px circle at var(--x, 50%) var(--y, 0%), rgb(var(--accent) / 0.10), transparent 65%)",
        }}
      />
      {/* resting top glow */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-10 top-0 -z-10 h-px bg-[linear-gradient(90deg,transparent,rgb(var(--accent)/0.55),transparent)]"
      />

      <div className="relative min-h-[13.5rem] flex-1 overflow-hidden px-6 pt-6 sm:px-8 sm:pt-8">{children}</div>

      <div className="flex items-start gap-4 border-t border-line/60 p-6 sm:p-8">
        <span className="grid size-10 shrink-0 place-items-center rounded-xl border border-[rgb(var(--accent)/0.25)] bg-[rgb(var(--accent)/0.10)] text-[rgb(var(--accent))]">
          <Icon className="size-[18px]" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <h3 className="flex items-baseline gap-3 text-lg font-medium tracking-tight text-white">
            {title}
            <span className="font-mono text-[0.65rem] font-normal text-fg-muted">{step}</span>
          </h3>
          <p className="mt-1.5 max-w-lg text-[0.93rem] leading-relaxed text-fg-soft">{text}</p>
        </div>
      </div>
    </article>
  );
}

/* ---------- Visuals ---------- */

function StudioVisual() {
  return (
    <div className="grid h-full gap-3 sm:grid-cols-[1fr_11rem]">
      <div className="rounded-2xl border border-line bg-black/60 p-4 font-mono text-[0.72rem] leading-[1.9] text-fg-soft">
        <div className="mb-2 flex items-center gap-1.5">
          <span className="size-2 rounded-full bg-white/10" />
          <span className="size-2 rounded-full bg-white/10" />
          <span className="size-2 rounded-full bg-white/10" />
          <span className="ml-2 text-[0.62rem] uppercase tracking-[0.16em] text-fg-muted">system_prompt.md</span>
        </div>
        <p>
          You are the receptionist for <Var>business_name</Var>.
        </p>
        <p>
          Book cleanings between <Var>opening_hours</Var>. Speak
        </p>
        <p>
          <Var>languages</Var>, warmly and briefly.
        </p>
        <p className="text-fg-muted">
          If the caller mentions pain, transfer to <Var>on_call_dentist</Var>
          <span className="ml-0.5 inline-block h-3.5 w-[2px] translate-y-0.5 animate-pulse bg-[rgb(var(--accent))]" />
        </p>
      </div>
      <div className="hidden flex-col gap-2 sm:flex">
        <Setting label="Voice" value="Amani · warm" />
        <Setting label="Languages" value="EN · SW · FR" />
        <Setting label="Model" value="Standard" />
      </div>
    </div>
  );
}

function Var({ children }: { children: ReactNode }) {
  return (
    <span className="rounded-md border border-[rgb(var(--accent)/0.3)] bg-[rgb(var(--accent)/0.12)] px-1 py-px text-[rgb(var(--accent))]">
      {`{{${children}}}`}
    </span>
  );
}

function Setting({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-line bg-black/60 px-3 py-2.5">
      <p className="font-mono text-[0.6rem] uppercase tracking-[0.16em] text-fg-muted">{label}</p>
      <p className="mt-0.5 text-[0.8rem] text-fg">{value}</p>
    </div>
  );
}

function TestCallVisual() {
  const reduce = useReducedMotion();
  const bars = [0.35, 0.6, 0.9, 0.5, 0.75, 1, 0.55, 0.8, 0.4, 0.7, 0.95, 0.5, 0.3, 0.65, 0.45];

  return (
    <div className="flex h-full flex-col items-center justify-center gap-5 pb-2">
      <div className="relative grid size-16 place-items-center">
        {!reduce ? (
          <>
            <span className="absolute inset-0 animate-ping rounded-full bg-[rgb(var(--accent)/0.15)] [animation-duration:2.4s]" />
            <span className="absolute -inset-3 rounded-full border border-[rgb(var(--accent)/0.12)]" />
          </>
        ) : null}
        <span className="relative grid size-14 place-items-center rounded-full bg-[rgb(var(--accent))] text-black shadow-[0_0_40px_rgb(var(--accent)/0.35)]">
          <Mic className="size-5" aria-hidden="true" />
        </span>
      </div>
      <div className="flex h-10 items-center gap-[3px]" aria-hidden="true">
        {bars.map((height, index) => (
          <motion.span
            key={index}
            className="w-[3px] rounded-full bg-[rgb(var(--accent)/0.8)]"
            style={{ height: `${height * 100}%` }}
            animate={reduce ? undefined : { scaleY: [height, 1, height * 0.5, height] }}
            transition={{ duration: 1.4, repeat: Infinity, delay: index * 0.07, ease: "easeInOut" }}
          />
        ))}
      </div>
      <p className="rounded-full border border-line bg-black/60 px-3 py-1 font-mono text-[0.65rem] text-fg-soft">
        Test call · 00:42 · intent: <span className="text-[rgb(var(--accent))]">book_cleaning</span>
      </p>
    </div>
  );
}

function ToolsVisual() {
  const tools = [
    { name: "Google Calendar", action: "check_availability", logo: "/logos/color/google-calendar.svg" },
    { name: "WhatsApp", action: "send_confirmation", logo: "/logos/color/whatsapp.svg" },
    { name: "Webhook", action: "POST /bookings", logo: null },
  ];

  return (
    <ul className="flex h-full flex-col justify-center gap-2.5 pb-2">
      {tools.map((tool, index) => (
        <motion.li
          key={tool.name}
          initial={{ opacity: 0, x: -8 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.5, delay: 0.2 + index * 0.1, ease }}
          className="flex items-center gap-3 rounded-xl border border-line bg-black/60 px-3 py-2.5"
        >
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/[0.05]">
            {tool.logo ? (
              <Image src={tool.logo} alt="" width={18} height={18} />
            ) : (
              <Webhook className="size-4 text-[rgb(var(--accent))]" aria-hidden="true" />
            )}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[0.8rem] text-fg">{tool.name}</span>
            <span className="block truncate font-mono text-[0.62rem] text-fg-muted">{tool.action}</span>
          </span>
          <span
            aria-hidden="true"
            className="relative h-4 w-7 shrink-0 rounded-full bg-[rgb(var(--accent)/0.85)] after:absolute after:right-0.5 after:top-0.5 after:size-3 after:rounded-full after:bg-black"
          />
        </motion.li>
      ))}
    </ul>
  );
}

function AnalyticsVisual() {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { once: true, margin: "0px 0px -60px 0px" });
  const reduce = useReducedMotion();
  const series = [38, 52, 45, 61, 58, 72, 66, 80, 74, 88, 83, 94];

  return (
    <div ref={ref} className="grid h-full gap-6 pb-2 sm:grid-cols-[11rem_1fr]">
      <dl className="flex gap-6 sm:flex-col sm:gap-4">
        <Metric label="Calls this week" value={inView ? 1284 : 0} />
        <Metric label="Booked" value={inView ? 87 : 0} suffix="%" />
        <Metric label="Handed to staff" value={inView ? 6 : 0} suffix="%" />
      </dl>
      <div className="flex h-40 items-end gap-1.5 self-end sm:h-full sm:max-h-48" aria-hidden="true">
        {series.map((value, index) => (
          <motion.span
            key={index}
            className="flex-1 origin-bottom rounded-t-md bg-[linear-gradient(180deg,rgb(var(--accent)/0.9),rgb(var(--accent)/0.15))]"
            style={{ height: `${value}%` }}
            initial={reduce ? false : { scaleY: 0 }}
            animate={inView ? { scaleY: 1 } : undefined}
            transition={{ duration: 0.8, delay: index * 0.04, ease }}
          />
        ))}
      </div>
    </div>
  );
}

function Metric({ label, value, suffix = "" }: { label: string; value: number; suffix?: string }) {
  return (
    <div className="flex flex-col-reverse">
      <dt className="font-mono text-[0.6rem] uppercase tracking-[0.16em] text-fg-muted">{label}</dt>
      <dd className="text-2xl font-medium tracking-tight text-white tabular-nums">
        <NumberFlow locales="en-US" value={value} suffix={suffix} />
      </dd>
    </div>
  );
}
