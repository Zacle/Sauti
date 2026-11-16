"use client";

import Link from "next/link";
import { useState, type CSSProperties } from "react";
import * as Tabs from "@radix-ui/react-tabs";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import type { LucideIcon } from "lucide-react";
import { ArrowUpRight, BookCheck, Languages, Shuffle } from "lucide-react";
import { Eyebrow, Reveal } from "./motion";
import { PixelGlow } from "./PixelGlow";

const ease = [0.16, 1, 0.3, 1] as const;

/* ---------- Language model ---------- */

type Lang = "sw" | "en" | "fr";

const langMeta: Record<Lang, { name: string; flag: string; rgb: string }> = {
  sw: { name: "Kiswahili", flag: "ke", rgb: "94 234 212" },
  en: { name: "English", flag: "gb", rgb: "167 139 250" },
  fr: { name: "Français", flag: "fr", rgb: "96 165 250" },
};

type Segment = [Lang, string];
type Turn = { speaker: "Caller" | "Sauti"; segments: Segment[] };

const conversations: Record<"mixed" | Lang, { label: string; flag?: string; turns: Turn[] }> = {
  mixed: {
    label: "Code-switch",
    turns: [
      { speaker: "Caller", segments: [["sw", "Habari, nataka ku-"], ["en", "book a cleaning"], ["sw", " ya Ijumaa asubuhi."]] },
      { speaker: "Sauti", segments: [["sw", "Sawa kabisa. "], ["en", "Friday at 10:00 is free,"], ["sw", " nikuwekee?"]] },
      { speaker: "Caller", segments: [["en", "Yes please, "], ["sw", "asante sana."]] },
      { speaker: "Sauti", segments: [["sw", "Umewekwa Ijumaa saa nne. "], ["en", "I'll text you a confirmation."]] },
    ],
  },
  sw: {
    label: "Kiswahili",
    flag: "ke",
    turns: [
      { speaker: "Sauti", segments: [["sw", "Habari, Bright Smile Dental. Nikusaidie vipi leo?"]] },
      { speaker: "Caller", segments: [["sw", "Ningependa kuhamisha miadi yangu wiki ijayo."]] },
      { speaker: "Sauti", segments: [["sw", "Jumanne saa tatu na nusu au Alhamisi saa nane ziko wazi. Ipi inakufaa?"]] },
    ],
  },
  en: {
    label: "English",
    flag: "gb",
    turns: [
      { speaker: "Sauti", segments: [["en", "Hello, Bright Smile Dental. How can I help today?"]] },
      { speaker: "Caller", segments: [["en", "I'd like to move my appointment to next week."]] },
      { speaker: "Sauti", segments: [["en", "Tuesday at 9:30 or Thursday at 14:00 are open. Which suits you?"]] },
    ],
  },
  fr: {
    label: "Français",
    flag: "fr",
    turns: [
      { speaker: "Sauti", segments: [["fr", "Bonjour, Bright Smile Dental. Comment puis-je vous aider ?"]] },
      { speaker: "Caller", segments: [["fr", "Je voudrais déplacer mon rendez-vous à la semaine prochaine."]] },
      { speaker: "Sauti", segments: [["fr", "Mardi à 9 h 30 ou jeudi à 14 h sont libres. Lequel vous convient ?"]] },
    ],
  },
};

type TabKey = keyof typeof conversations;
const tabOrder: TabKey[] = ["mixed", "sw", "en", "fr"];

const greetings = ["Habari", "Hello", "Bonjour", "Olá", "Merhaba", "مرحبا", "Jambo", "Salut", "Karibu", "Bom dia"];

const languageChips = [
  ["English", "gb"],
  ["Kiswahili", "ke"],
  ["Français", "fr"],
  ["Português", "pt"],
  ["Türkçe", "tr"],
  ["العربية", "ae"],
] as const;

const points: Array<[LucideIcon, string, string]> = [
  [Languages, "Detects the caller's language", "No IVR menu. Sauti hears the first words and answers in kind."],
  [Shuffle, "Follows mid-sentence switches", "Kiswahili to English and back, without losing the booking."],
  [BookCheck, "Same rules in every language", "One prompt, one set of tools and knowledge, every language."],
];

/* ---------- Section ---------- */

export function LanguageSection() {
  return (
    <section className="relative isolate overflow-hidden border-t border-line">
      <GreetingBackdrop />

      <div className="mx-auto grid max-w-[1240px] grid-cols-1 gap-14 px-4 py-20 sm:px-8 sm:py-28 lg:grid-cols-[0.85fr_1.15fr] lg:items-center lg:gap-16 [&>*]:min-w-0">
        <Reveal>
          <Eyebrow>Multilingual by default</Eyebrow>
          <h2 className="mt-5 text-4xl font-medium leading-[1.05] tracking-[-0.03em] text-white sm:text-5xl">
            One agent. <span className="block font-serif font-normal italic text-fg-soft">Many languages.</span>
          </h2>
          <p className="mt-5 max-w-lg text-[1.02rem] leading-relaxed text-fg-soft">
            Your callers don&rsquo;t stick to one language, so neither does Sauti. It keeps your tone, knowledge and
            booking rules in every language you enable.
          </p>

          <ul className="mt-10 grid gap-5">
            {points.map(([Icon, title, text]) => (
              <li key={title} className="flex gap-4">
                <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-signal/25 bg-signal/10 text-signal">
                  <Icon className="size-4" aria-hidden="true" />
                </span>
                <div>
                  <p className="text-[0.98rem] font-medium text-fg">{title}</p>
                  <p className="mt-0.5 text-sm leading-relaxed text-fg-soft">{text}</p>
                </div>
              </li>
            ))}
          </ul>

          <div className="mt-10">
            <p className="font-mono text-[0.65rem] uppercase tracking-[0.18em] text-fg-muted">Configure per agent</p>
            <ul className="mt-3 flex flex-wrap gap-2">
              {languageChips.map(([name, flag]) => (
                <li
                  key={name}
                  className="flex items-center gap-2 rounded-full border border-line bg-white/[0.03] py-1 pl-1 pr-3 text-[0.82rem] text-fg-soft"
                >
                  <Flag code={flag} />
                  {name}
                </li>
              ))}
            </ul>
          </div>

          <Link
            href="/solutions/appointment-booking"
            className="mt-10 inline-flex items-center gap-1.5 text-sm text-fg transition-colors hover:text-signal"
          >
            See appointment booking <ArrowUpRight className="size-4" aria-hidden="true" />
          </Link>
        </Reveal>

        <Reveal delay={0.1}>
          <TranscriptConsole />
        </Reveal>
      </div>
    </section>
  );
}

/** Oversized greetings drifting behind the section, in the serif accent face. */
function GreetingBackdrop() {
  const row = [...greetings, ...greetings];
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 -z-10 flex flex-col justify-center gap-2 overflow-hidden [mask-image:radial-gradient(70%_60%_at_50%_50%,black,transparent)]"
    >
      <div className="flex w-max animate-marquee gap-16 whitespace-nowrap font-serif text-[6rem] italic leading-none text-white/[0.035] sm:text-[9rem]">
        {row.map((word, index) => (
          <span key={index}>{word}</span>
        ))}
      </div>
      <div className="flex w-max animate-marquee gap-16 whitespace-nowrap font-serif text-[6rem] italic leading-none text-white/[0.035] [animation-direction:reverse] sm:text-[9rem]">
        {row.slice(4).concat(row.slice(0, 4)).map((word, index) => (
          <span key={index}>{word}</span>
        ))}
      </div>
    </div>
  );
}

/* ---------- Transcript console ---------- */

// Module-level so the glow's effect isn't restarted on every tab change.
const languageGlow = [
  [94, 234, 212],
  [167, 139, 250],
  [96, 165, 250],
] as const;

function TranscriptConsole() {
  const [tab, setTab] = useState<TabKey>("mixed");
  const reduce = useReducedMotion();
  const conversation = conversations[tab];
  const detected = Array.from(new Set(conversation.turns.flatMap((turn) => turn.segments.map(([lang]) => lang))));

  return (
    <div className="relative isolate">
      <PixelGlow
        seed={7}
        palette={languageGlow}
        intensity={1}
        className="pointer-events-none absolute -left-12 -top-24 -z-10 h-[calc(100%+12rem)] w-[calc(100%+6rem)] [mask-image:radial-gradient(closest-side,black_55%,transparent)] sm:-left-40 sm:w-[calc(100%+20rem)]"
      />
      <Tabs.Root
        value={tab}
        onValueChange={(value) => setTab(value as TabKey)}
        className="overflow-hidden rounded-3xl border border-line-strong bg-[linear-gradient(180deg,#0e0e0e,#070707)] shadow-[0_40px_120px_-40px_rgba(0,0,0,0.9)]"
      >
        <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
          <div className="flex items-center gap-2.5">
            <span className="relative flex size-2">
              {!reduce ? <span className="absolute inline-flex size-full animate-ping rounded-full bg-signal opacity-60" /> : null}
              <span className="relative inline-flex size-2 rounded-full bg-signal" />
            </span>
            <span className="text-[0.82rem] font-medium text-fg">Live transcript</span>
          </div>
          <div className="flex items-center gap-1.5 font-mono text-[0.62rem] uppercase tracking-[0.14em] text-fg-muted">
            Detected
            <AnimatePresence mode="popLayout" initial={false}>
              {detected.map((lang) => (
                <motion.span
                  key={lang}
                  layout
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.8 }}
                  transition={{ duration: 0.3, ease }}
                  style={{ "--c": langMeta[lang].rgb } as CSSProperties}
                  className="rounded-md border border-[rgb(var(--c)/0.35)] bg-[rgb(var(--c)/0.12)] px-1.5 py-0.5 text-[rgb(var(--c))]"
                >
                  {lang}
                </motion.span>
              ))}
            </AnimatePresence>
          </div>
        </div>

        <Tabs.List aria-label="Conversation language" className="flex gap-1 overflow-x-auto px-3 pt-3">
          {tabOrder.map((key) => (
            <Tabs.Trigger
              key={key}
              value={key}
              className="relative flex shrink-0 items-center gap-2 rounded-full px-3.5 py-1.5 text-[0.82rem] text-fg-soft transition-colors hover:text-fg data-[state=active]:text-black focus-visible:outline-2 focus-visible:outline-signal"
            >
              {tab === key ? (
                <motion.span
                  layoutId="transcript-tab"
                  className="absolute inset-0 rounded-full bg-white"
                  transition={{ type: "spring", bounce: 0.18, duration: 0.5 }}
                />
              ) : null}
              <span className="relative flex items-center gap-2">
                {conversations[key].flag ? (
                  <Flag code={conversations[key].flag!} />
                ) : (
                  <Shuffle className="size-3.5" aria-hidden="true" />
                )}
                {conversations[key].label}
              </span>
            </Tabs.Trigger>
          ))}
        </Tabs.List>

        {tabOrder.map((key) => (
          <Tabs.Content key={key} value={key} className="focus-visible:outline-none">
            <motion.ol
              initial="hidden"
              animate="show"
              variants={{ show: { transition: { staggerChildren: reduce ? 0 : 0.12 } } }}
              className="flex min-h-[22rem] flex-col gap-4 p-5"
            >
              {conversations[key].turns.map((turn, index) => (
                <TurnRow key={`${key}-${index}`} turn={turn} highlight={key === "mixed"} />
              ))}
            </motion.ol>
          </Tabs.Content>
        ))}

        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-line px-5 py-3.5 font-mono text-[0.62rem] uppercase tracking-[0.14em] text-fg-muted">
          {(Object.keys(langMeta) as Lang[]).map((lang) => (
            <span key={lang} className="flex items-center gap-1.5">
              <span className="size-1.5 rounded-full" style={{ background: `rgb(${langMeta[lang].rgb})` }} />
              {langMeta[lang].name}
            </span>
          ))}
          <span className="ml-auto hidden normal-case tracking-normal text-fg-soft sm:inline">Replies in the caller&rsquo;s language</span>
        </div>
      </Tabs.Root>
    </div>
  );
}

function TurnRow({ turn, highlight }: { turn: Turn; highlight: boolean }) {
  const isAgent = turn.speaker === "Sauti";
  const langs = Array.from(new Set(turn.segments.map(([lang]) => lang)));

  return (
    <motion.li
      variants={{
        hidden: { opacity: 0, y: 10 },
        show: { opacity: 1, y: 0, transition: { duration: 0.5, ease } },
      }}
      className={`flex gap-3 ${isAgent ? "flex-row-reverse text-right" : ""}`}
    >
      <span
        aria-hidden="true"
        className={
          isAgent
            ? "mt-1 size-7 shrink-0 rounded-full bg-[radial-gradient(circle_at_30%_30%,#ffffff,rgb(94_234_212)_45%,rgb(96_165_250))] shadow-[0_0_18px_rgb(94_234_212/0.35)]"
            : "mt-1 grid size-7 shrink-0 place-items-center rounded-full border border-line-strong bg-white/[0.04] text-[0.65rem] text-fg-soft"
        }
      >
        {isAgent ? null : "C"}
      </span>
      <div className={`flex min-w-0 max-w-[85%] flex-col gap-1.5 ${isAgent ? "items-end" : "items-start"}`}>
        <p
          className={`rounded-2xl px-4 py-2.5 text-[0.95rem] leading-snug ${
            isAgent
              ? "rounded-tr-md border border-white/10 bg-white/[0.07] text-white"
              : "rounded-tl-md border border-line text-fg-soft"
          }`}
        >
          {turn.segments.map(([lang, text], index) =>
            highlight ? (
              <span
                key={index}
                style={{ "--c": langMeta[lang].rgb } as CSSProperties}
                className="underline decoration-[rgb(var(--c))] decoration-2 underline-offset-[5px]"
              >
                {text}
              </span>
            ) : (
              <span key={index}>{text}</span>
            ),
          )}
        </p>
        <span className="flex items-center gap-1.5 font-mono text-[0.6rem] uppercase tracking-[0.14em] text-fg-muted">
          {turn.speaker}
          {langs.map((lang) => (
            <span key={lang} className="inline-flex items-center gap-1">
              · <Flag code={langMeta[lang].flag} size="xs" /> {lang}
            </span>
          ))}
        </span>
      </div>
    </motion.li>
  );
}

function Flag({ code, size = "sm" }: { code: string; size?: "xs" | "sm" }) {
  return (
    <span
      aria-hidden="true"
      className={`fi fis fi-${code} shrink-0 overflow-hidden rounded-full bg-cover ring-1 ring-white/10 ${size === "xs" ? "size-3!" : "size-5!"}`}
    />
  );
}
