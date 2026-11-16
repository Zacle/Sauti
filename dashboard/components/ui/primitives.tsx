"use client";

/**
 * Sauti design system: small presentational primitives (Card, Badge, Switch,
 * Checkbox, RadioGroup, Tabs, Tooltip, Table, Skeleton, EmptyState, Kbd).
 * Radix provides behaviour and accessibility; Tailwind tokens from
 * styles/tailwind.css provide the look. See /design-system for every state.
 */

import * as React from "react";
import * as SwitchPrimitive from "@radix-ui/react-switch";
import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import * as RadioPrimitive from "@radix-ui/react-radio-group";
import * as TabsPrimitive from "@radix-ui/react-tabs";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import { cva, type VariantProps } from "class-variance-authority";
import { Check, Minus } from "lucide-react";
import { cn } from "@/lib/utils";

/* ---------- Card ---------- */

export function Card({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "rounded-[var(--radius-card)] border border-line bg-surface text-fg",
        className,
      )}
      {...props}
    />
  );
}

export function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex items-start justify-between gap-4 p-5 sm:p-6", className)} {...props} />;
}

export function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn("m-0 text-[0.98rem] font-medium tracking-tight text-ink", className)} {...props} />;
}

export function CardDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("m-0 mt-1 text-sm leading-relaxed text-fg-soft", className)} {...props} />;
}

export function CardContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-5 pb-5 sm:px-6 sm:pb-6", className)} {...props} />;
}

export function CardFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("flex items-center gap-3 border-t border-line px-5 py-4 sm:px-6", className)} {...props} />;
}

/* ---------- Badge ---------- */

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[var(--radius-badge)] border px-2 py-0.5 text-xs font-medium [&_svg]:size-3",
  {
    variants: {
      tone: {
        neutral: "border-line-strong bg-tint/[0.04] text-fg-soft",
        signal: "border-signal/30 bg-signal/10 text-signal",
        success: "border-success/30 bg-success/10 text-success",
        warning: "border-warning/30 bg-warning/10 text-warning",
        danger: "border-danger/30 bg-danger/10 text-danger",
        info: "border-info/30 bg-info/10 text-info",
        violet: "border-violet/30 bg-violet/10 text-violet",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

export function Badge({
  className,
  tone,
  dot = false,
  children,
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants> & { dot?: boolean }) {
  return (
    <span className={cn(badgeVariants({ tone }), className)} {...props}>
      {dot ? <span className="size-1.5 rounded-full bg-current" aria-hidden="true" /> : null}
      {children}
    </span>
  );
}

/* ---------- Switch ---------- */

export function Switch({ className, ...props }: React.ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      className={cn(
        "peer relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border border-line-strong bg-tint/[0.08] p-0 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-signal/60 data-[state=checked]:bg-signal",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="block size-[18px] translate-x-[3px] rounded-full bg-white shadow-sm transition-transform duration-200 ease-out-expo data-[state=checked]:translate-x-[21px] data-[state=checked]:bg-signal-fg" />
    </SwitchPrimitive.Root>
  );
}

/* ---------- Checkbox ---------- */

export function Checkbox({ className, ...props }: React.ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        "grid size-[18px] shrink-0 cursor-pointer place-items-center rounded-[6px] border border-line-strong bg-tint/[0.03] p-0 text-signal-fg transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:border-signal data-[state=checked]:bg-signal data-[state=indeterminate]:border-signal data-[state=indeterminate]:bg-signal",
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator>
        {props.checked === "indeterminate" ? <Minus className="size-3.5" strokeWidth={3} /> : <Check className="size-3.5" strokeWidth={3} />}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

/* ---------- Radio group (card style) ---------- */

export function RadioGroup({ className, ...props }: React.ComponentProps<typeof RadioPrimitive.Root>) {
  return <RadioPrimitive.Root className={cn("grid gap-2", className)} {...props} />;
}

export function RadioCard({
  className,
  title,
  description,
  icon,
  ...props
}: React.ComponentProps<typeof RadioPrimitive.Item> & { title: React.ReactNode; description?: React.ReactNode; icon?: React.ReactNode }) {
  return (
    <RadioPrimitive.Item
      className={cn(
        "group flex w-full cursor-pointer items-start gap-3 rounded-[var(--radius-popover)] border border-line bg-tint/[0.02] p-4 text-left font-sans text-fg transition-colors hover:border-line-strong focus-visible:outline-2 focus-visible:outline-signal data-[state=checked]:border-signal/45 data-[state=checked]:bg-signal/[0.07]",
        className,
      )}
      {...props}
    >
      {icon ? <span className="mt-0.5 text-fg-soft group-data-[state=checked]:text-signal [&_svg]:size-[18px]">{icon}</span> : null}
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-ink">{title}</span>
        {description ? <span className="mt-0.5 block text-xs leading-relaxed text-fg-muted">{description}</span> : null}
      </span>
      <span className="mt-0.5 grid size-4 shrink-0 place-items-center rounded-full border border-line-strong group-data-[state=checked]:border-signal">
        <RadioPrimitive.Indicator className="size-2 rounded-full bg-signal" />
      </span>
    </RadioPrimitive.Item>
  );
}

/* ---------- Tabs ---------- */

export const Tabs = TabsPrimitive.Root;

export function TabsList({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      className={cn("inline-flex h-9 items-center gap-0.5 rounded-[calc(var(--radius-button)+3px)] border border-line bg-tint/[0.035] p-[3px]", className)}
      {...props}
    />
  );
}

export function TabsTrigger({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        "inline-flex h-full cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-[var(--radius-button)] border border-transparent bg-transparent px-3 font-sans text-[0.85rem] font-medium leading-none text-fg-soft transition-[background-color,color,box-shadow] duration-150 hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-signal data-[state=active]:border-line data-[state=active]:bg-surface data-[state=active]:text-ink data-[state=active]:shadow-[0_1px_2px_rgb(0_0_0/0.08)] [&_svg]:size-3.5",
        className,
      )}
      {...props}
    />
  );
}

/** Small count shown inside a tab or segmented option, e.g. "Live 2". */
export function TabsCount({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) {
  return (
    <span
      className={cn("min-w-[1.15rem] rounded-[4px] bg-tint/[0.06] px-1 py-[3px] text-center text-[0.7rem] font-medium tabular-nums leading-none text-fg-muted", className)}
      {...props}
    />
  );
}

export const TabsContent = TabsPrimitive.Content;

/* ---------- Tooltip ---------- */

export const TooltipProvider = TooltipPrimitive.Provider;

export function Tooltip({ content, children, side = "top" }: { content: React.ReactNode; children: React.ReactNode; side?: "top" | "right" | "bottom" | "left" }) {
  return (
    <TooltipPrimitive.Root delayDuration={250}>
      <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          side={side}
          sideOffset={8}
          className="z-[300] max-w-xs rounded-lg border border-line-strong bg-popover px-2.5 py-1.5 font-sans text-xs text-fg shadow-[var(--shadow-pop)] data-[state=delayed-open]:animate-[select-in_140ms_cubic-bezier(0.16,1,0.3,1)]"
        >
          {content}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  );
}

/* ---------- Table ---------- */

export function Table({ className, ...props }: React.TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={cn("w-full border-collapse text-sm", className)} {...props} />
    </div>
  );
}

export function TableHead({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn("[&_th]:border-b [&_th]:border-line [&_th]:px-4 [&_th]:py-3 [&_th]:text-left [&_th]:text-xs [&_th]:font-medium [&_th]:text-fg-muted [&_th]:whitespace-nowrap", className)} {...props} />;
}

export function TableBody({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn("[&_td]:border-b [&_td]:border-line [&_td]:px-4 [&_td]:py-3.5 [&_td]:text-fg-soft [&_tr:last-child_td]:border-0 [&_tr]:transition-colors [&_tr:hover]:bg-tint/[0.02]", className)} {...props} />;
}

/* ---------- Skeleton / Empty / Kbd ---------- */

export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("animate-pulse rounded-lg bg-tint/[0.06]", className)} {...props} />;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-line-strong px-6 py-14 text-center", className)}>
      {icon ? <span className="grid size-12 place-items-center rounded-[var(--radius-popover)] border border-line bg-tint/[0.03] text-fg-soft [&_svg]:size-5">{icon}</span> : null}
      <p className="m-0 mt-5 text-[0.98rem] font-medium text-ink">{title}</p>
      {description ? <p className="m-0 mt-1.5 max-w-sm text-sm leading-relaxed text-fg-soft">{description}</p> : null}
      {action ? <div className="mt-6">{action}</div> : null}
    </div>
  );
}

export function Kbd({ className, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <kbd
      className={cn("inline-flex h-5 min-w-5 items-center justify-center rounded-md border border-line-strong bg-tint/[0.04] px-1.5 font-mono text-[0.65rem] text-fg-soft", className)}
      {...props}
    />
  );
}
