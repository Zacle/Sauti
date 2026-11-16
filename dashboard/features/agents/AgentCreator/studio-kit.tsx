"use client";

/**
 * Agent Studio layout kit.
 *
 * Every studio section is built from these pieces so spacing, alignment and
 * controls match: one column, labels above controls, every control starting
 * on the same left edge. Controls come from the shared design system
 * (components/ui); this file only arranges them.
 *
 * Sections render inside `.studio-kit`, which shields them from the older
 * studio stylesheets (see AgentStudioLayout.css).
 */

import * as React from "react";
import * as SliderPrimitive from "@radix-ui/react-slider";
import { Check, Minus, Plus } from "lucide-react";
import { Input } from "@/components/ui/field";
import { Switch } from "@/components/ui/primitives";
import { cn } from "@/lib/utils";

/* ---------- Section and groups ---------- */

export function StudioSection({
  eyebrow,
  title,
  description,
  actions,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="studio-kit mx-auto w-full max-w-[760px] font-sans text-fg">
      <header className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3 pb-6">
        <div className="min-w-0">
          <p className="m-0 text-[0.75rem] font-medium text-fg-muted">{eyebrow}</p>
          <h2 className="m-0 mt-1 text-[1.35rem] font-semibold leading-tight tracking-[-0.02em] text-ink">{title}</h2>
          <p className="m-0 mt-1.5 max-w-xl text-[0.88rem] leading-relaxed text-fg-soft">{description}</p>
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </header>
      <div className="grid grid-cols-[minmax(0,1fr)]">{children}</div>
    </div>
  );
}

export function StudioGroup({
  title,
  description,
  action,
  children,
  className,
  id,
  highlight = false,
  groupRef,
}: {
  title?: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  id?: string;
  highlight?: boolean;
  groupRef?: React.Ref<HTMLElement>;
}) {
  return (
    <section
      id={id}
      ref={groupRef}
      tabIndex={id ? -1 : undefined}
      className={cn(
        "scroll-mt-6 border-t border-line py-6 outline-none transition-[box-shadow,background-color] duration-500",
        highlight && "rounded-[var(--radius-card)] bg-signal/[0.04] shadow-[0_0_0_2px_var(--color-signal)]",
        className,
      )}
    >
      {title || action ? (
        <div className="mb-4 flex items-start justify-between gap-4">
          <div className="min-w-0">
            {title ? <h3 className="m-0 text-[0.95rem] font-semibold tracking-[-0.01em] text-ink">{title}</h3> : null}
            {description ? <p className="m-0 mt-1 text-[0.82rem] leading-relaxed text-fg-muted">{description}</p> : null}
          </div>
          {action ? <div className="shrink-0">{action}</div> : null}
        </div>
      ) : null}
      <div className="grid grid-cols-[minmax(0,1fr)] gap-5">{children}</div>
    </section>
  );
}

/* ---------- Fields ---------- */

export function Field({
  label,
  hint,
  badge,
  htmlFor,
  children,
  className,
}: {
  label: React.ReactNode;
  hint?: React.ReactNode;
  badge?: React.ReactNode;
  htmlFor?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("grid min-w-0 content-start gap-1.5", className)}>
      <label htmlFor={htmlFor} className="flex min-h-5 items-center justify-between gap-2 text-[0.84rem] font-medium text-ink">
        <span>{label}</span>
        {badge}
      </label>
      {children}
      {hint ? <p className="m-0 text-[0.76rem] leading-relaxed text-fg-muted">{hint}</p> : null}
    </div>
  );
}

/** Fields side by side on wider screens; they stack on phones. */
export function FieldGrid({ columns = 2, children, className }: { columns?: 2 | 3; children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("grid gap-x-4 gap-y-5", columns === 3 ? "sm:grid-cols-3" : "sm:grid-cols-2", className)}>{children}</div>
  );
}

/** Text input sized for the studio (matches Select size="md" with h-10). */
export const StudioInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  function StudioInput({ className, ...props }, ref) {
    return <Input ref={ref} className={cn("h-10 text-[0.88rem]", className)} {...props} />;
  },
);

export const studioSelectClass = "h-10 text-[0.88rem]";

/* ---------- Number with unit and steppers ---------- */

export function NumberField({
  id,
  value,
  onChange,
  min,
  max,
  step = 1,
  unit,
  ariaLabel,
}: {
  id?: string;
  value: number;
  onChange: (value: number) => void;
  min: number;
  max: number;
  step?: number;
  unit?: string;
  ariaLabel?: string;
}) {
  const clamp = (next: number) => Math.min(max, Math.max(min, Number.isFinite(next) ? next : min));
  const [draft, setDraft] = React.useState(String(value));
  React.useEffect(() => setDraft(String(value)), [value]);
  const stepper =
    "grid h-full w-9 shrink-0 cursor-pointer place-items-center text-fg-muted transition-colors hover:bg-tint/[0.05] hover:text-ink disabled:cursor-not-allowed disabled:opacity-35 [&_svg]:size-3.5";
  return (
    <div className="flex h-10 w-full items-stretch overflow-hidden rounded-[var(--radius-control)] border border-line-strong bg-tint/[0.03] transition-[border-color,box-shadow] focus-within:border-signal/60 focus-within:ring-4 focus-within:ring-signal/10">
      <button type="button" aria-label="Decrease" className={cn(stepper, "border-r border-line")} disabled={value <= min} onClick={() => onChange(clamp(value - step))}>
        <Minus />
      </button>
      <div className="flex min-w-0 flex-1 items-center gap-1.5 px-3">
        <input
          id={id}
          aria-label={ariaLabel}
          inputMode="numeric"
          value={draft}
          onChange={(event) => setDraft(event.target.value.replace(/[^\d]/g, ""))}
          onBlur={() => onChange(clamp(Number(draft)))}
          onKeyDown={(event) => {
            if (event.key === "Enter") onChange(clamp(Number(draft)));
            if (event.key === "ArrowUp") { event.preventDefault(); onChange(clamp(value + step)); }
            if (event.key === "ArrowDown") { event.preventDefault(); onChange(clamp(value - step)); }
          }}
          className="w-full min-w-0 border-0 bg-transparent p-0 text-center font-sans text-[0.88rem] tabular-nums text-fg outline-none"
        />
        {unit ? <span className="shrink-0 text-[0.78rem] text-fg-muted">{unit}</span> : null}
      </div>
      <button type="button" aria-label="Increase" className={cn(stepper, "border-l border-line")} disabled={value >= max} onClick={() => onChange(clamp(value + step))}>
        <Plus />
      </button>
    </div>
  );
}

/* ---------- Slider ---------- */

/**
 * A labelled slider: the current value sits beside the label, the ends are
 * explained in words ("Patient" ↔ "Quick"), and optional marks show
 * recommended settings you can click to jump to.
 */
export function SliderField({
  label,
  description,
  value,
  min,
  max,
  step,
  format,
  onChange,
  lowLabel,
  highLabel,
  marks = [],
}: {
  label: string;
  description?: string;
  value: number;
  min: number;
  max: number;
  step: number;
  format: (value: number) => string;
  onChange: (value: number) => void;
  lowLabel?: string;
  highLabel?: string;
  marks?: { value: number; label: string }[];
}) {
  const [dragging, setDragging] = React.useState(false);
  const percent = (point: number) => ((point - min) / (max - min)) * 100;
  return (
    <div className="grid gap-3">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="m-0 text-[0.84rem] font-medium text-ink">{label}</p>
          {description ? <p className="m-0 mt-0.5 text-[0.76rem] leading-relaxed text-fg-muted">{description}</p> : null}
        </div>
        <output className="shrink-0 rounded-[var(--radius-badge)] border border-line bg-tint/[0.04] px-2 py-1 font-sans text-[0.78rem] font-medium tabular-nums text-ink">
          {format(value)}
        </output>
      </div>
      <div className="relative pt-1">
        <SliderPrimitive.Root
          className="relative flex h-5 w-full touch-none select-none items-center"
          value={[value]}
          min={min}
          max={max}
          step={step}
          aria-label={label}
          onValueChange={([next]) => onChange(next)}
          onPointerDown={() => setDragging(true)}
          onPointerUp={() => setDragging(false)}
          onValueCommit={() => setDragging(false)}
        >
          <SliderPrimitive.Track className="relative h-1.5 w-full grow overflow-hidden rounded-full bg-tint/[0.1]">
            <SliderPrimitive.Range className="absolute h-full rounded-full bg-signal" />
          </SliderPrimitive.Track>
          <SliderPrimitive.Thumb
            className="group relative block size-[18px] cursor-grab rounded-full border-2 border-signal bg-surface shadow-[0_1px_3px_rgb(0_0_0/0.2)] outline-none transition-[box-shadow,transform] duration-150 hover:shadow-[0_0_0_6px_color-mix(in_oklab,var(--color-signal)_14%,transparent)] focus-visible:shadow-[0_0_0_6px_color-mix(in_oklab,var(--color-signal)_22%,transparent)] active:scale-110 active:cursor-grabbing"
          >
            <span
              aria-hidden="true"
              className={cn(
                "pointer-events-none absolute bottom-[calc(100%+8px)] left-1/2 -translate-x-1/2 whitespace-nowrap rounded-[5px] bg-primary px-1.5 py-1 text-[0.7rem] font-medium tabular-nums leading-none text-primary-fg shadow-sm transition-opacity duration-150",
                dragging ? "opacity-100" : "opacity-0 group-focus-visible:opacity-100",
              )}
            >
              {format(value)}
            </span>
          </SliderPrimitive.Thumb>
        </SliderPrimitive.Root>
        {marks.length ? (
          <div className="relative mx-[9px] mt-1.5 h-4">
            {marks.map((mark) => {
              const at = percent(mark.value);
              const edge = at <= 2 ? "start" : at >= 98 ? "end" : "middle";
              return (
                <button
                  key={mark.value}
                  type="button"
                  onClick={() => onChange(mark.value)}
                  style={{ left: `${at}%` }}
                  className={cn(
                    "absolute top-0 flex cursor-pointer flex-col whitespace-nowrap text-[0.7rem] leading-none transition-colors hover:text-ink",
                    edge === "middle" && "-translate-x-1/2 items-center",
                    edge === "start" && "items-start",
                    edge === "end" && "-translate-x-full items-end",
                    mark.value === value ? "font-medium text-ink" : "text-fg-muted",
                  )}
                >
                  <span aria-hidden="true" className="mb-1 block h-1 w-px bg-line-strong" />
                  {mark.label}
                </button>
              );
            })}
          </div>
        ) : null}
      </div>
      {lowLabel || highLabel ? (
        <div className="-mt-1 flex justify-between text-[0.72rem] text-fg-muted">
          <span>{lowLabel}</span>
          <span>{highLabel}</span>
        </div>
      ) : null}
    </div>
  );
}

/* ---------- Switch rows ---------- */

export function SwitchRow({
  title,
  description,
  checked,
  onChange,
  disabled,
  children,
}: {
  title: string;
  description?: React.ReactNode;
  checked: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
  /** Settings that only apply while the switch is on. */
  children?: React.ReactNode;
}) {
  const id = React.useId();
  return (
    <div className="py-3.5 first:pt-0 last:pb-0">
      <div className="flex items-start justify-between gap-6">
        <label htmlFor={id} className={cn("min-w-0", disabled ? "cursor-not-allowed" : "cursor-pointer")}>
          <span className="block text-[0.88rem] font-medium text-ink">{title}</span>
          {description ? <span className="mt-0.5 block text-[0.78rem] leading-relaxed text-fg-muted">{description}</span> : null}
        </label>
        <Switch id={id} checked={checked} disabled={disabled} onCheckedChange={onChange} className="mt-0.5" />
      </div>
      {checked && children ? <SubPanel className="mt-3.5">{children}</SubPanel> : null}
    </div>
  );
}

/** Rows separated by hairlines (switch rows, list items). */
export function RowList({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn("grid divide-y divide-line", className)}>{children}</div>;
}

/** Nested settings that belong to the control above them. */
export function SubPanel({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn("grid grid-cols-[minmax(0,1fr)] gap-5 rounded-[var(--radius-card)] border border-line bg-tint/[0.025] p-4 sm:p-5", className)}>{children}</div>;
}

/* ---------- Chips ---------- */

export function ChipGroup({ children, label }: { children: React.ReactNode; label?: string }) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      {children}
    </div>
  );
}

export function Chip({
  selected,
  onClick,
  disabled,
  locked,
  children,
  trailing,
}: {
  selected: boolean;
  onClick?: () => void;
  disabled?: boolean;
  /** Always on and cannot be changed (shown with a lock-style look). */
  locked?: boolean;
  children: React.ReactNode;
  trailing?: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled || locked}
      onClick={onClick}
      className={cn(
        "inline-flex h-8 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-full border px-3 font-sans text-[0.82rem] leading-none transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal [&_svg]:size-3.5",
        selected
          ? "border-signal/40 bg-signal/[0.1] font-medium text-ink"
          : "border-line-strong bg-transparent text-fg-soft hover:border-fg-muted/50 hover:text-ink",
        locked && "cursor-default opacity-100",
        disabled && !locked && "cursor-not-allowed opacity-45",
      )}
    >
      {selected ? <Check className="text-signal" /> : <Plus className="text-fg-muted" />}
      {children}
      {trailing}
    </button>
  );
}

/* ---------- Notes ---------- */

export function Note({
  icon,
  title,
  children,
  tone = "neutral",
  action,
}: {
  icon: React.ReactNode;
  title: string;
  children?: React.ReactNode;
  tone?: "neutral" | "warning" | "signal";
  action?: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-[var(--radius-card)] border p-4",
        tone === "warning" && "border-warning/30 bg-warning/[0.06]",
        tone === "signal" && "border-signal/25 bg-signal/[0.05]",
        tone === "neutral" && "border-line bg-tint/[0.025]",
      )}
    >
      <span
        className={cn(
          "mt-px shrink-0 [&_svg]:size-[18px]",
          tone === "warning" ? "text-warning" : tone === "signal" ? "text-signal" : "text-fg-muted",
        )}
      >
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className="m-0 text-[0.85rem] font-medium text-ink">{title}</p>
        {children ? <div className="mt-0.5 text-[0.78rem] leading-relaxed text-fg-soft">{children}</div> : null}
      </div>
      {action ? <div className="shrink-0 self-center">{action}</div> : null}
    </div>
  );
}

/** A count or status shown at the right of a group heading. */
export function Meta({ children }: { children: React.ReactNode }) {
  return <span className="text-[0.76rem] tabular-nums text-fg-muted">{children}</span>;
}
