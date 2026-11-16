"use client";

/**
 * Sauti design system: error and empty-line states.
 *
 * One component for every "something isn't here / didn't work" moment, so a
 * missing page, a crash, a lost connection and a missing permission all read
 * the same way: what happened, why it isn't the user's fault (when it isn't),
 * and the one or two things they can do next.
 *
 * The illustration is a phone line, Sauti's own metaphor: a live waveform that
 * goes flat (not found), breaks (error), or is disconnected (offline).
 */

import * as React from "react";
import { Check, Copy } from "lucide-react";
import { cn } from "@/lib/utils";

export type ErrorKind = "not-found" | "error" | "offline" | "forbidden";

export function LineIllustration({ kind, className }: { kind: ErrorKind; className?: string }) {
  // A short burst of speech, then whatever happens to the line.
  const speech = "M4 32 H34 L39 22 L44 42 L49 14 L54 50 L59 26 L64 38 L69 30 L74 34 H82";
  return (
    <svg
      viewBox="0 0 240 64"
      fill="none"
      aria-hidden="true"
      className={cn("h-16 w-60 overflow-visible", className)}
    >
      {kind !== "offline" ? (
        <path
          d={speech}
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="text-signal [stroke-dasharray:220] [animation:line-draw_1.4s_cubic-bezier(0.16,1,0.3,1)_both]"
        />
      ) : null}

      {kind === "not-found" ? (
        <>
          <path d="M82 32 H200" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeDasharray="1 7" className="text-fg-muted" />
          <circle cx="212" cy="32" r="5" stroke="currentColor" strokeWidth="2" className="text-fg-muted" />
        </>
      ) : null}

      {kind === "error" ? (
        <>
          <path d="M82 32 H104 L110 20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-signal" />
          <path d="M124 44 L130 32 H236" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-fg-muted" />
          <path d="M113 26 l8 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="text-danger" />
        </>
      ) : null}

      {kind === "offline" ? (
        <>
          <path d="M4 32 H96" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="text-fg-muted" />
          <path d="M144 32 H236" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="text-fg-muted" />
          <circle cx="104" cy="32" r="4" fill="currentColor" className="text-fg-muted" />
          <circle cx="136" cy="32" r="4" fill="currentColor" className="text-fg-muted" />
        </>
      ) : null}

      {kind === "forbidden" ? (
        <>
          <path d="M82 32 H104" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="text-signal" />
          <rect x="108" y="20" width="24" height="24" rx="6" stroke="currentColor" strokeWidth="2" className="text-fg-soft" />
          <path d="M115 32 h10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="text-fg-soft" />
          <path d="M136 32 H236" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeDasharray="1 7" className="text-fg-muted" />
        </>
      ) : null}
    </svg>
  );
}

/** A copyable reference so support can find the exact failure in the logs. */
export function ErrorReference({ value, className }: { value: string; className?: string }) {
  const [copied, setCopied] = React.useState(false);
  return (
    <button
      type="button"
      onClick={() => {
        void navigator.clipboard?.writeText(value).then(() => {
          setCopied(true);
          window.setTimeout(() => setCopied(false), 1600);
        });
      }}
      className={cn(
        "inline-flex cursor-pointer items-center gap-1.5 rounded-[var(--radius-badge)] border border-line bg-tint/[0.03] px-2 py-1 font-mono text-[0.72rem] text-fg-muted transition-colors hover:border-line-strong hover:text-fg-soft",
        className,
      )}
      aria-label={`Copy error reference ${value}`}
    >
      Ref {value}
      {copied ? <Check className="size-3 text-signal" /> : <Copy className="size-3" />}
    </button>
  );
}

export function ErrorState({
  kind = "error",
  code,
  title,
  description,
  actions,
  reference,
  children,
  className,
  compact = false,
}: {
  kind?: ErrorKind;
  /** Small label above the title, e.g. "404" or "Connection lost". */
  code?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  reference?: string;
  /** Extra content below the actions, such as suggested links. */
  children?: React.ReactNode;
  className?: string;
  /** Smaller version for use inside a card or panel. */
  compact?: boolean;
}) {
  return (
    <section
      role={kind === "error" || kind === "offline" ? "alert" : undefined}
      className={cn(
        "mx-auto flex w-full max-w-lg flex-col items-center text-center font-sans",
        compact ? "gap-3 py-10" : "gap-4 py-16",
        className,
      )}
    >
      <LineIllustration kind={kind} className={compact ? "mb-1 h-12 w-48" : "mb-3"} />
      {code ? (
        <span className="font-mono text-[0.72rem] font-medium uppercase tracking-[0.14em] text-fg-muted">{code}</span>
      ) : null}
      <h1 className={cn("m-0 font-semibold tracking-[-0.02em] text-ink text-balance", compact ? "text-lg" : "text-[1.7rem] leading-tight sm:text-[2rem]")}>
        {title}
      </h1>
      {description ? (
        <p className={cn("m-0 max-w-md leading-relaxed text-fg-soft text-pretty", compact ? "text-sm" : "text-[0.95rem]")}>{description}</p>
      ) : null}
      {actions ? <div className="mt-2 flex flex-wrap items-center justify-center gap-2">{actions}</div> : null}
      {reference ? (
        <p className="m-0 mt-1 flex flex-wrap items-center justify-center gap-2 text-[0.75rem] text-fg-muted">
          Quote this if you contact support <ErrorReference value={reference} />
        </p>
      ) : null}
      {children}
    </section>
  );
}

/** Suggested destinations under a full-page error. */
export function ErrorLinks({ links }: { links: { href: string; label: string; description: string }[] }) {
  return (
    <nav aria-label="Suggested pages" className="mt-8 grid w-full gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line text-left sm:grid-cols-3">
      {links.map((link) => (
        <a
          key={link.href}
          href={link.href}
          className="group flex flex-col gap-1 bg-surface px-4 py-3.5 no-underline transition-colors hover:bg-surface-2"
        >
          <span className="flex items-center justify-between text-[0.88rem] font-medium text-ink">
            {link.label}
            <span aria-hidden="true" className="text-fg-muted transition-transform group-hover:translate-x-0.5">→</span>
          </span>
          <span className="text-[0.78rem] leading-snug text-fg-muted">{link.description}</span>
        </a>
      ))}
    </nav>
  );
}
