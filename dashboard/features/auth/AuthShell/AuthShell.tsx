"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Globe2, ShieldCheck, Zap } from "lucide-react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import { PixelGlow, glowPalettes } from "@/features/marketing/VoiceHome/PixelGlow";
import { Eyebrow } from "@/features/marketing/VoiceHome/motion";

type GlowPalette = (typeof glowPalettes)[keyof typeof glowPalettes];

/**
 * The two-panel layout shared by every sign-in page: brand story on the left (large screens),
 * the form on the right. Pages pass their own headline and the form as children.
 */
export function AuthShell({
  eyebrow,
  hero,
  glowSeed = 2,
  glowPalette = glowPalettes.signal,
  children,
}: {
  eyebrow: string;
  hero: { title: string; accent: string };
  glowSeed?: number;
  glowPalette?: GlowPalette;
  children: ReactNode;
}) {
  return (
    <main className="mk-v2 relative grid min-h-dvh bg-canvas lg:grid-cols-[1.05fr_1fr]">
      {/* Brand panel */}
      <aside className="relative isolate hidden flex-col justify-between overflow-hidden border-r border-line p-10 lg:flex xl:p-14">
        <PixelGlow
          seed={glowSeed}
          palette={glowPalette}
          intensity={0.85}
          className="pointer-events-none absolute -bottom-72 -left-32 -z-10 h-[46rem] w-[56rem] [mask-image:radial-gradient(closest-side,black_35%,transparent)]"
        />
        <Link href="/" className="inline-flex items-center gap-2.5 self-start text-lg font-semibold tracking-tight text-white">
          <BrandLogo size={30} />
          Sauti
        </Link>

        <div className="max-w-lg">
          <Eyebrow>{eyebrow}</Eyebrow>
          <h1 className="mt-6 text-5xl font-medium leading-[1.03] tracking-[-0.035em] text-white xl:text-[3.6rem]">
            {hero.title}{" "}
            <span className="block font-serif font-normal italic tracking-[-0.01em] text-fg-soft">{hero.accent}</span>
          </h1>
          <ul className="mt-12 grid gap-5">
            {[
              [Globe2, "Multilingual by default", "Natural conversations in English, Kiswahili, French and more."],
              [Zap, "Live operational insight", "Calls, transcripts and outcomes in one place."],
              [ShieldCheck, "Built for trusted access", "Workspace-isolated tools and customer data."],
            ].map(([Icon, title, text]) => {
              const I = Icon as typeof Globe2;
              return (
                <li key={title as string} className="flex gap-4">
                  <span className="grid size-9 shrink-0 place-items-center rounded-xl border border-signal/25 bg-signal/10 text-signal">
                    <I className="size-4" aria-hidden="true" />
                  </span>
                  <span>
                    <span className="block text-[0.95rem] font-medium text-fg">{title as string}</span>
                    <span className="mt-0.5 block text-sm text-fg-soft">{text as string}</span>
                  </span>
                </li>
              );
            })}
          </ul>
        </div>

        <p className="flex gap-4 text-xs text-fg-muted">
          <span>© {new Date().getFullYear()} Sauti</span>
          <Link href="/privacy" className="transition-colors hover:text-fg">Privacy</Link>
          <Link href="/terms" className="transition-colors hover:text-fg">Terms</Link>
        </p>
      </aside>

      {/* Form panel */}
      <section className="relative isolate flex min-h-dvh flex-col overflow-hidden">
        <PixelGlow
          seed={3}
          palette={glowPalettes.ocean}
          intensity={0.5}
          className="pointer-events-none absolute -top-40 left-1/2 -z-10 h-[30rem] w-[44rem] -translate-x-1/2 [mask-image:radial-gradient(closest-side,black_15%,transparent)] lg:hidden"
        />
        <header className="flex items-center justify-between px-5 py-5 sm:px-8">
          <Link href="/" className="inline-flex items-center gap-2.5 font-semibold tracking-tight text-white lg:invisible">
            <BrandLogo size={28} />
            Sauti
          </Link>
          <Link href="/" className="text-sm text-fg-soft transition-colors hover:text-white">
            Back to site
          </Link>
        </header>

        <div className="flex flex-1 items-center justify-center px-5 pb-16 sm:px-8">
          {children}
        </div>
      </section>
    </main>
  );
}
