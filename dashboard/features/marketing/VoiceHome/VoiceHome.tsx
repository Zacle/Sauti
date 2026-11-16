"use client";

import Image from "next/image";
import Link from "next/link";
import { motion, useReducedMotion } from "motion/react";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PublicDemoVoice } from "../PublicDemoVoice/PublicDemoVoice";
import { VoiceField } from "./VoiceField";
import { PixelGlow, glowPalettes } from "./PixelGlow";
import { LiveCallCard } from "./LiveCallCard";
import { LanguageSection } from "./LanguageSection";
import { Stats } from "./Stats";
import { PlatformBento } from "./PlatformBento";
import { Eyebrow, Reveal } from "./motion";
import { ClosingCta, FaqSection, IndustryShowcase, TrustSection } from "./BottomSections";

// Full-colour marks (public/logos/color); the originals in public/logos are
// monochrome and still used by the console integrations page.
const integrations = [
  ["Google Calendar", "/logos/color/google-calendar.svg"],
  ["HubSpot", "/logos/color/hubspot.svg"],
  ["Salesforce", "/logos/color/salesforce.svg"],
  ["Slack", "/logos/color/slack.svg"],
  ["Google Sheets", "/logos/color/google-sheets.svg"],
  ["Zapier", "/logos/color/zapier.svg"],
  ["WhatsApp", "/logos/color/whatsapp.svg"],
  ["Calendly", "/logos/color/calendly.svg"],
  ["Pipedrive", "/logos/color/pipedrive.svg"],
  ["M-Pesa", "/logos/color/mpesa.svg"],
] as const;

const ease = [0.16, 1, 0.3, 1] as const;

export default function VoiceHome() {
  const reduce = useReducedMotion();
  const enter = (delay: number) =>
    reduce
      ? {}
      : {
          initial: { opacity: 0, y: 16 },
          animate: { opacity: 1, y: 0 },
          transition: { duration: 0.6, delay, ease },
        };

  return (
    <main className="mk-v2 overflow-x-clip">
      {/* Hero */}
      <section className="relative isolate overflow-hidden pt-28 sm:pt-36">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[38rem] bg-[radial-gradient(60%_50%_at_50%_0%,rgba(94,234,212,0.10),transparent_70%)]"
        />
        <PixelGlow
          seed={1}
          intensity={0.75}
          className="pointer-events-none absolute -top-16 left-[30%] -z-10 h-[50rem] w-[78%] [mask-image:radial-gradient(closest-side,black_40%,transparent)] max-lg:left-[-15%] max-lg:top-[20rem] max-lg:w-[130%]"
        />
        <div className="mx-auto grid max-w-[1240px] grid-cols-1 gap-14 px-4 sm:px-8 lg:grid-cols-[1.1fr_0.9fr] lg:items-center lg:gap-10 [&>*]:min-w-0">
          <div>
            <motion.div {...enter(0)}>
              <Eyebrow>AI voice agents for bookings &amp; support</Eyebrow>
            </motion.div>

            <motion.h1
              {...enter(0.08)}
              className="mt-7 text-[2.75rem] font-medium leading-[1.02] tracking-[-0.035em] text-white sm:text-6xl lg:text-[4.6rem]"
            >
              Every call answered,{" "}
              <span className="font-serif text-[1.08em] font-normal italic tracking-[-0.01em] text-fg-soft">
                in your customer&rsquo;s language.
              </span>
            </motion.h1>

            <motion.p {...enter(0.16)} className="mt-6 max-w-[34rem] text-[1.06rem] leading-relaxed text-fg-soft">
              Sauti voice agents pick up, understand callers in English, Kiswahili and French, book straight into your
              calendar and hand over to your team when it matters.
            </motion.p>

            <motion.div {...enter(0.24)} className="mt-9 flex flex-wrap items-center gap-3">
              <Button asChild size="lg">
                <Link href="/request-demo">
                  Book a demo <ArrowRight aria-hidden="true" />
                </Link>
              </Button>
              <PublicDemoVoice />
            </motion.div>

            <motion.ul
              {...enter(0.32)}
              className="mt-10 flex flex-wrap gap-x-6 gap-y-2 font-mono text-[0.7rem] uppercase tracking-[0.14em] text-fg-muted"
            >
              <li>Native tool calling</li>
              <li>Barge-in aware</li>
              <li>Human handoff</li>
            </motion.ul>
          </div>

          <motion.div
            initial={reduce ? false : { opacity: 0, y: 24, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ duration: 0.9, delay: 0.3, ease }}
            className="relative mx-auto w-full max-w-[460px] lg:mr-0"
          >
            <LiveCallCard />
          </motion.div>
        </div>

        <div className="relative mt-16 h-44 sm:mt-20 sm:h-60">
          <VoiceField className="absolute inset-0 size-full [mask-image:linear-gradient(to_bottom,black_40%,transparent)]" />
        </div>
      </section>

      {/* Integrations marquee */}
      <section aria-label="Integrations" className="border-y border-line py-8">
        <p className="mb-6 text-center font-mono text-[0.68rem] uppercase tracking-[0.18em] text-fg-muted">
          Connects to the tools you already run
        </p>
        <div className="relative overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_12%,black_88%,transparent)]">
          <ul className="flex w-max animate-marquee gap-3 pr-3 hover:[animation-play-state:paused]">
            {[...integrations, ...integrations].map(([name, logo], index) => (
              <li
                key={`${name}-${index}`}
                aria-hidden={index >= integrations.length}
                className="flex items-center gap-3 rounded-full border border-line bg-white/[0.025] py-1.5 pl-1.5 pr-4 text-sm text-fg-soft transition-colors duration-300 hover:border-line-strong hover:bg-white/[0.05] hover:text-fg"
              >
                <span className="grid size-8 place-items-center rounded-full bg-white/[0.06]">
                  <Image src={logo} alt="" width={18} height={18} className="size-[18px] object-contain" />
                </span>
                {name}
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* Platform */}
      <section className="relative isolate mx-auto max-w-[1240px] px-4 py-20 sm:px-8 sm:py-28">
        <PixelGlow
          seed={2}
          palette={glowPalettes.ocean}
          intensity={0.6}
          className="pointer-events-none absolute -left-48 top-0 -z-10 h-[30rem] w-[52rem] max-w-[140%] [mask-image:radial-gradient(closest-side,black_30%,transparent)]"
        />
        <Reveal className="max-w-2xl">
          <Eyebrow>The platform</Eyebrow>
          <h2 className="mt-5 text-4xl font-medium leading-[1.05] tracking-[-0.03em] text-white sm:text-5xl">
            From hello to <span className="font-serif font-normal italic text-fg-soft">booked.</span>
          </h2>
          <p className="mt-5 text-[1.02rem] leading-relaxed text-fg-soft">
            Design an agent, test it against real conversations, connect your tools and watch every outcome, without
            writing code.
          </p>
        </Reveal>

        <PlatformBento />
      </section>

      {/* Stats */}
      <section aria-label="Results" className="mx-auto max-w-[1240px] px-4 sm:px-8">
        <Stats />
      </section>

      <LanguageSection />

      <IndustryShowcase />
      <TrustSection />
      <FaqSection />
      <ClosingCta />
    </main>
  );
}
