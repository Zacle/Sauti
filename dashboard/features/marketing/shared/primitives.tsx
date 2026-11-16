"use client";

/**
 * Building blocks shared by every redesigned marketing page, so each page is
 * mostly content: a hero, headed sections, spotlight cards and a closing band.
 */

import Link from "next/link";
import type { CSSProperties, MouseEvent, ReactNode } from "react";
import { motion, useReducedMotion } from "motion/react";
import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { PixelGlow, glowPalettes } from "../VoiceHome/PixelGlow";
import { Eyebrow, Reveal } from "../VoiceHome/motion";

export { Eyebrow, Reveal, PixelGlow, glowPalettes };

const ease = [0.16, 1, 0.3, 1] as const;

/** Root wrapper for a redesigned marketing page (opts into the scoped Tailwind preflight). */
export function MarketingPage({ children, className }: { children: ReactNode; className?: string }) {
  return <main className={cn("mk-v2 overflow-x-clip", className)}>{children}</main>;
}

export function Container({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("mx-auto w-full max-w-[1240px] px-4 sm:px-8", className)}>{children}</div>;
}

/** "Title with an *italic accent*": pass the accent separately so every heading keeps the same rhythm. */
export function Headline({
  as: Tag = "h2",
  children,
  accent,
  accentBlock = false,
  className,
}: {
  as?: "h1" | "h2";
  children: ReactNode;
  accent?: ReactNode;
  accentBlock?: boolean;
  className?: string;
}) {
  return (
    <Tag
      className={cn(
        "font-medium tracking-[-0.035em] text-white",
        Tag === "h1"
          ? "text-[2.6rem] leading-[1.02] sm:text-6xl lg:text-[4.2rem]"
          : "text-4xl leading-[1.05] sm:text-5xl",
        className,
      )}
    >
      {children}
      {accent ? (
        <>
          {" "}
          <span className={cn("font-serif font-normal italic tracking-[-0.01em] text-fg-soft", accentBlock && "block")}>
            {accent}
          </span>
        </>
      ) : null}
    </Tag>
  );
}

/** Page hero with eyebrow, headline, lede, actions and an optional pixel glow behind it. */
export function PageHero({
  eyebrow,
  title,
  accent,
  description,
  actions,
  children,
  align = "left",
  glowSeed = 11,
  palette = glowPalettes.signal,
}: {
  eyebrow: ReactNode;
  title: ReactNode;
  accent?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  /** Optional visual rendered beside (left align) or below (center align) the copy. */
  children?: ReactNode;
  align?: "left" | "center";
  glowSeed?: number;
  palette?: (typeof glowPalettes)[keyof typeof glowPalettes];
}) {
  const reduce = useReducedMotion();
  const enter = (delay: number) =>
    reduce
      ? {}
      : {
          initial: { opacity: 0, y: 14 },
          animate: { opacity: 1, y: 0 },
          transition: { duration: 0.6, delay, ease },
        };
  const centered = align === "center";

  return (
    <section className="relative isolate overflow-hidden pb-16 pt-32 sm:pb-24 sm:pt-40">
      <PixelGlow
        seed={glowSeed}
        palette={palette}
        intensity={centered ? 0.5 : 0.7}
        className={cn(
          "pointer-events-none absolute -z-10",
          // Centered heroes keep the glow low and wide so the headline sits on near-black.
          centered
            ? "bottom-[-14rem] left-1/2 h-[34rem] w-[80rem] max-w-[200%] -translate-x-1/2 [mask-image:radial-gradient(50%_50%_at_50%_50%,black_20%,transparent)]"
            : "-top-24 left-[38%] h-[40rem] w-[70%] [mask-image:radial-gradient(closest-side,black_35%,transparent)] max-lg:left-[-20%] max-lg:w-[140%]",
        )}
      />
      <Container
        className={cn(
          "grid gap-14",
          centered ? "justify-items-center text-center" : children ? "lg:grid-cols-[1.05fr_0.95fr] lg:items-center" : "",
          "[&>*]:min-w-0",
        )}
      >
        <div className={cn(centered && "flex max-w-3xl flex-col items-center")}>
          <motion.div {...enter(0)}>
            <Eyebrow>{eyebrow}</Eyebrow>
          </motion.div>
          <motion.div {...enter(0.07)} className="mt-6">
            <Headline as="h1" accent={accent}>
              {title}
            </Headline>
          </motion.div>
          {description ? (
            <motion.p
              {...enter(0.14)}
              className={cn("mt-6 max-w-[36rem] text-[1.06rem] leading-relaxed text-fg-soft", centered && "mx-auto")}
            >
              {description}
            </motion.p>
          ) : null}
          {actions ? (
            <motion.div {...enter(0.21)} className={cn("mt-9 flex flex-wrap items-center gap-3", centered && "justify-center")}>
              {actions}
            </motion.div>
          ) : null}
        </div>
        {children ? (
          <motion.div
            initial={reduce ? false : { opacity: 0, y: 20, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.25, ease }}
            className={cn("w-full", centered && "mt-4")}
          >
            {children}
          </motion.div>
        ) : null}
      </Container>
    </section>
  );
}

/** Standard section: border on top, consistent vertical rhythm, optional heading block. */
export function Section({
  children,
  className,
  bordered = true,
  id,
}: {
  children: ReactNode;
  className?: string;
  bordered?: boolean;
  id?: string;
}) {
  return (
    <section id={id} className={cn("relative isolate scroll-mt-24", bordered && "border-t border-line", className)}>
      <Container className="py-20 sm:py-28">{children}</Container>
    </section>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  accent,
  description,
  action,
  align = "left",
  className,
}: {
  eyebrow: ReactNode;
  title: ReactNode;
  accent?: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  align?: "left" | "center";
  className?: string;
}) {
  const centered = align === "center";
  return (
    <Reveal
      className={cn(
        "flex flex-wrap items-end justify-between gap-6",
        centered && "flex-col items-center text-center",
        className,
      )}
    >
      <div className={cn("max-w-2xl", centered && "mx-auto")}>
        <Eyebrow>{eyebrow}</Eyebrow>
        <Headline className="mt-5" accent={accent}>
          {title}
        </Headline>
        {description ? (
          <p className={cn("mt-5 max-w-xl text-[1.02rem] leading-relaxed text-fg-soft", centered && "mx-auto")}>
            {description}
          </p>
        ) : null}
      </div>
      {action}
    </Reveal>
  );
}

/** Pill-shaped secondary link with a nudging arrow. */
export function ArrowLink({ href, children, className }: { href: string; children: ReactNode; className?: string }) {
  return (
    <Link
      href={href}
      className={cn(
        "group inline-flex items-center gap-1.5 rounded-full border border-line px-4 py-2 text-sm text-fg-soft transition-colors hover:border-line-strong hover:text-fg",
        className,
      )}
    >
      {children}
      <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
    </Link>
  );
}

/** Card with a cursor-following glow in the given accent (space-separated RGB). */
export function SpotlightCard({
  children,
  className,
  accent = "94 234 212",
  as: Tag = "article",
}: {
  children: ReactNode;
  className?: string;
  accent?: string;
  as?: "article" | "div" | "li";
}) {
  const onMove = (event: MouseEvent<HTMLElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    event.currentTarget.style.setProperty("--x", `${event.clientX - rect.left}px`);
    event.currentTarget.style.setProperty("--y", `${event.clientY - rect.top}px`);
  };
  return (
    <Tag
      onMouseMove={onMove}
      style={{ "--accent": accent } as CSSProperties}
      className={cn(
        "group relative isolate flex h-full flex-col overflow-hidden rounded-3xl border border-line bg-[linear-gradient(180deg,#0d0d0d,#060606)] p-6 transition-colors duration-500 hover:border-[rgb(var(--accent)/0.28)] sm:p-7",
        className,
      )}
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 opacity-0 transition-opacity duration-500 group-hover:opacity-100"
        style={{ background: "radial-gradient(380px circle at var(--x, 50%) var(--y, 0%), rgb(var(--accent) / 0.09), transparent 65%)" }}
      />
      {children}
    </Tag>
  );
}

/** Icon in an accent-tinted tile. */
export function IconTile({ children, accent = "94 234 212", className }: { children: ReactNode; accent?: string; className?: string }) {
  return (
    <span
      style={{ "--accent": accent } as CSSProperties}
      className={cn(
        "grid size-11 shrink-0 place-items-center rounded-2xl border border-[rgb(var(--accent)/0.25)] bg-[rgb(var(--accent)/0.10)] text-[rgb(var(--accent))] [&_svg]:size-5",
        className,
      )}
    >
      {children}
    </span>
  );
}

export function CheckList({ items, className }: { items: ReactNode[]; className?: string }) {
  return (
    <ul className={cn("grid gap-3", className)}>
      {items.map((item, index) => (
        <li key={index} className="flex gap-3 text-[0.95rem] leading-relaxed text-fg-soft">
          <Check className="mt-1 size-4 shrink-0 text-signal" aria-hidden="true" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

/** Closing call-to-action band with a warm pixel glow. */
export function CtaBand({
  title = "Hear it on",
  accent = "your",
  titleEnd = "calls.",
  description = "We’ll configure an agent for one of your customer journeys and walk you through it live.",
  seed = 5,
}: {
  title?: ReactNode;
  accent?: ReactNode;
  titleEnd?: ReactNode;
  description?: ReactNode;
  seed?: number;
}) {
  return (
    <section className="relative isolate overflow-hidden border-t border-line">
      <PixelGlow
        seed={seed}
        palette={glowPalettes.dusk}
        block={22}
        className="pointer-events-none absolute inset-x-0 bottom-0 -z-10 mx-auto h-full w-full max-w-[1400px] [mask-image:radial-gradient(52%_42%_at_50%_56%,black_25%,transparent)]"
      />
      <Reveal className="mx-auto flex max-w-3xl flex-col items-center px-4 py-24 text-center sm:py-32">
        <h2 className="text-4xl font-medium leading-[1.02] tracking-[-0.04em] text-white sm:text-6xl">
          {title}
          {accent ? <> <span className="font-serif font-normal italic text-fg-soft">{accent}</span></> : null}
          {titleEnd ? <> {titleEnd}</> : null}
        </h2>
        <p className="mt-6 max-w-md text-[1.05rem] leading-relaxed text-fg-soft">{description}</p>
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
      </Reveal>
    </section>
  );
}
