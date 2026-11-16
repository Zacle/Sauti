"use client";

/**
 * Shared date-range picker (Radix Popover + react-day-picker): trigger in the
 * Select style, quick presets on the left, two-month calendar, Apply/Reset.
 */

import * as React from "react";
import * as Popover from "@radix-ui/react-popover";
import { format, isSameDay } from "date-fns";
import { DayPicker, type DateRange } from "react-day-picker";
import { CalendarDays, Check, ChevronDown, RotateCcw } from "lucide-react";
import { Button } from "./button";
import { cn } from "@/lib/utils";

export type { DateRange };
export type DateRangePreset = { label: string; range: () => DateRange };

// react-day-picker defines its variables on .rdp-root, so they must be set inline there.
const calendarStyle = {
  "--rdp-accent-color": "var(--color-signal)",
  "--rdp-accent-background-color": "color-mix(in oklab, var(--color-signal) 15%, transparent)",
  "--rdp-day-height": "2.25rem",
  "--rdp-day-width": "2.25rem",
  "--rdp-day_button-height": "2.1rem",
  "--rdp-day_button-width": "2.1rem",
  "--rdp-day_button-border-radius": "0.6rem",
  "--rdp-selected-border": "0",
  "--rdp-range_middle-background-color": "color-mix(in oklab, var(--color-signal) 12%, transparent)",
  "--rdp-range_middle-color": "var(--color-fg)",
  "--rdp-range_start-color": "var(--color-signal-fg)",
  "--rdp-range_end-color": "var(--color-signal-fg)",
  "--rdp-range_start-date-background-color": "var(--color-signal)",
  "--rdp-range_end-date-background-color": "var(--color-signal)",
  "--rdp-range_start-background": "linear-gradient(var(--rdp-gradient-direction), transparent 50%, color-mix(in oklab, var(--color-signal) 12%, transparent) 50%)",
  "--rdp-range_end-background": "linear-gradient(var(--rdp-gradient-direction), color-mix(in oklab, var(--color-signal) 12%, transparent) 50%, transparent 50%)",
  "--rdp-today-color": "var(--color-signal)",
  "--rdp-nav_button-height": "2rem",
  "--rdp-nav_button-width": "2rem",
  "--rdp-outside-opacity": "0.35",
} as React.CSSProperties;

const calendarText =
  "[&_.rdp-caption_label]:text-sm [&_.rdp-caption_label]:font-medium [&_.rdp-weekday]:font-mono [&_.rdp-weekday]:text-[0.6rem] [&_.rdp-weekday]:uppercase [&_.rdp-weekday]:text-fg-muted [&_.rdp-day_button]:font-sans [&_.rdp-day_button]:text-[0.82rem] [&_.rdp-chevron]:fill-fg-soft";

export function DateRangePicker({
  value,
  onChange,
  presets = [],
  allLabel = "All dates",
  className,
}: {
  value: DateRange | undefined;
  onChange: (range: DateRange | undefined) => void;
  presets?: DateRangePreset[];
  allLabel?: string;
  className?: string;
}) {
  const [open, setOpen] = React.useState(false);
  const [draft, setDraft] = React.useState<DateRange | undefined>(value);

  function handleOpenChange(next: boolean) {
    if (next) setDraft(value);
    setOpen(next);
  }

  function apply() {
    onChange(draft?.from ? { from: draft.from, to: draft.to ?? draft.from } : undefined);
    setOpen(false);
  }

  return (
    <Popover.Root open={open} onOpenChange={handleOpenChange}>
      <Popover.Trigger asChild>
        <button
          type="button"
          className={cn(
            "group/select inline-flex h-9 max-w-full cursor-pointer items-center gap-2 truncate rounded-[var(--radius-control)] border border-tint/[0.14] bg-tint/[0.03] px-3 font-sans text-[0.82rem] text-fg transition-colors hover:border-tint/25 hover:bg-tint/[0.05] data-[state=open]:border-signal/50 data-[state=open]:ring-4 data-[state=open]:ring-signal/10",
            className,
          )}
        >
          <CalendarDays className="size-4 text-signal" />
          {rangeLabel(value, allLabel)}
          <ChevronDown className="size-4 text-fg-muted transition-transform group-data-[state=open]/select:rotate-180" />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="start"
          sideOffset={6}
          collisionPadding={12}
          className="z-[200] overflow-hidden rounded-[var(--radius-popover)] border border-tint/[0.12] bg-popover font-sans text-fg shadow-[var(--shadow-pop)] data-[state=open]:animate-[select-in_160ms_cubic-bezier(0.16,1,0.3,1)]"
        >
          <div className="flex max-md:flex-col">
            {presets.length ? (
              <aside aria-label="Quick ranges" className="grid content-start gap-0.5 border-line p-2 md:w-40 md:border-r max-md:grid-cols-2 max-md:border-b">
                {[...presets, { label: allLabel, range: () => ({ from: undefined, to: undefined }) as unknown as DateRange }].map((preset) => {
                  const range = preset.range();
                  const selected = range.from ? sameRange(draft, range) : !draft?.from;
                  return (
                    <button
                      key={preset.label}
                      type="button"
                      onClick={() => setDraft(range.from ? range : undefined)}
                      className={cn(
                        "flex cursor-pointer items-center justify-between rounded-lg border-0 bg-transparent px-3 py-2 text-left font-sans text-sm transition-colors",
                        selected ? "bg-tint/[0.07] text-ink" : "text-fg-soft hover:bg-tint/[0.04] hover:text-ink",
                      )}
                    >
                      {preset.label}
                      {selected ? <Check className="size-3.5 text-signal" /> : null}
                    </button>
                  );
                })}
              </aside>
            ) : null}
            <div className={cn("p-3", calendarText)}>
              <p className="m-0 mb-1 px-1 text-xs text-fg-soft">{draftLabel(draft)}</p>
              <DayPicker style={calendarStyle} mode="range" selected={draft} onSelect={setDraft} defaultMonth={draft?.from ?? new Date()} numberOfMonths={2} showOutsideDays />
            </div>
          </div>
          <footer className="flex items-center justify-between gap-2 border-t border-line px-3 py-2.5">
            <Button variant="ghost" size="sm" onClick={() => setDraft(value)}><RotateCcw /> Reset</Button>
            <div className="flex gap-2">
              <Popover.Close asChild><Button variant="secondary" size="sm">Cancel</Button></Popover.Close>
              <Button size="sm" onClick={apply}>Apply</Button>
            </div>
          </footer>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}

function sameRange(left: DateRange | undefined, right: DateRange) {
  return Boolean(left?.from && left.to && right.from && right.to && isSameDay(left.from, right.from) && isSameDay(left.to, right.to));
}

function rangeLabel(range: DateRange | undefined, allLabel: string) {
  if (!range?.from) return allLabel;
  const to = range.to ?? range.from;
  if (isSameDay(range.from, to)) return format(range.from, "MMM d, yyyy");
  if (range.from.getFullYear() === to.getFullYear()) return `${format(range.from, "MMM d")} – ${format(to, "MMM d, yyyy")}`;
  return `${format(range.from, "MMM d, yyyy")} – ${format(to, "MMM d, yyyy")}`;
}

function draftLabel(range: DateRange | undefined) {
  if (!range?.from) return "Showing every date";
  if (!range.to) return `Starting ${format(range.from, "MMMM d, yyyy")}`;
  return `${format(range.from, "MMMM d")} to ${format(range.to, "MMMM d, yyyy")}`;
}
