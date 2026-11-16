"use client";

/** Sauti design system: console page building blocks. */

import * as React from "react";
import NumberFlow, { type Format } from "@number-flow/react";
import { cn } from "@/lib/utils";

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  className,
}: {
  /** Kept for API compatibility; the console header shows title and description only. */
  eyebrow?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  void eyebrow;
  return (
    <header className={cn("flex flex-wrap items-end justify-between gap-x-6 gap-y-3", className)}>
      <div className="min-w-0">
        <h1 className="m-0 text-[1.45rem] font-semibold leading-tight tracking-[-0.02em] text-ink">{title}</h1>
        {description ? <p className="m-0 mt-1 max-w-2xl text-[0.9rem] leading-relaxed text-fg-soft">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}

/** Signed percent change, coloured by whether the move is good. */
export function Change({ value, invert = false, className }: { value: number | null | undefined; invert?: boolean; className?: string }) {
  if (value == null || Number.isNaN(value)) return null;
  const good = invert ? value <= 0 : value >= 0;
  const sign = value > 0 ? "+" : value < 0 ? "−" : "";
  return (
    <span className={cn("tabular-nums", value === 0 ? "text-fg-muted" : good ? "text-success" : "text-danger", className)}>
      {sign}
      {Math.abs(value).toFixed(1)}%
    </span>
  );
}

/** KPI card: animated value (NumberFlow), optional % change. */
export function StatCard({
  label,
  value,
  format,
  suffix,
  change,
  invertChange = false,
  icon,
  hint,
  className,
}: {
  label: React.ReactNode;
  value: number;
  format?: Format;
  suffix?: string;
  /** Percent change vs previous period, e.g. 12.4 or -3. */
  change?: number | null;
  /** When lower is better (missed calls, latency). */
  invertChange?: boolean;
  icon?: React.ReactNode;
  hint?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex min-w-0 flex-col rounded-[var(--radius-card)] border border-line bg-surface px-4 py-3.5", className)}>
      <StatBody label={label} value={value} format={format} suffix={suffix} change={change} invertChange={invertChange} icon={icon} hint={hint} />
    </div>
  );
}

type StatItem = {
  label: React.ReactNode;
  value: number;
  format?: Format;
  suffix?: string;
  change?: number | null;
  invertChange?: boolean;
  hint?: React.ReactNode;
};

/** Several KPIs in one bordered strip, separated by hairlines. */
export function StatStrip({ items, className }: { items: StatItem[]; className?: string }) {
  return (
    <section
      className={cn(
        "grid grid-cols-2 overflow-hidden rounded-[var(--radius-card)] border border-line bg-line [&>*]:min-w-0",
        items.length >= 4 ? "lg:grid-cols-4" : "lg:grid-cols-3",
        items.length === 5 && "lg:grid-cols-5",
        "gap-px",
        className,
      )}
    >
      {items.map((item, index) => (
        <div key={index} className="flex flex-col bg-surface px-4 py-3.5 sm:px-5 sm:py-4">
          <StatBody {...item} />
        </div>
      ))}
    </section>
  );
}

function StatBody({ label, value, format, suffix, change, invertChange, icon, hint }: StatItem & { icon?: React.ReactNode }) {
  return (
    <>
      <span className="flex items-center gap-1.5 truncate text-[0.8rem] text-fg-soft [&_svg]:size-3.5 [&_svg]:text-fg-muted">
        {icon}
        {label}
      </span>
      <span className="mt-1.5 text-[1.6rem] font-medium leading-none tracking-[-0.02em] text-ink tabular-nums">
        <NumberFlow locales="en-US" value={value} format={format} suffix={suffix} />
      </span>
      {change != null || hint ? (
        <span className="mt-2 flex flex-wrap items-center gap-x-1.5 text-[0.75rem]">
          <Change value={change} invert={invertChange} className="font-medium" />
          {hint ? <span className="text-fg-muted">{hint}</span> : null}
        </span>
      ) : null}
    </>
  );
}

/** Section heading used inside console pages. */
export function SectionTitle({ title, meta, action, className }: { title: React.ReactNode; meta?: React.ReactNode; action?: React.ReactNode; className?: string }) {
  return (
    <div className={cn("flex items-baseline justify-between gap-4", className)}>
      <h2 className="m-0 flex items-baseline gap-2 text-[0.95rem] font-semibold text-ink">
        {title}
        {meta ? <span className="text-[0.8rem] font-normal text-fg-muted">{meta}</span> : null}
      </h2>
      {action}
    </div>
  );
}
