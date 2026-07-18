"use client";

import * as Popover from "@radix-ui/react-popover";
import { addDays, endOfMonth, format, isSameDay, startOfMonth } from "date-fns";
import { CalendarDays, Check, ChevronDown, RotateCcw, X } from "lucide-react";
import { useState } from "react";
import { DayPicker, type DateRange } from "react-day-picker";
import styles from "./BookingDateRangePicker.module.css";

type Props = {
  start: string;
  end: string;
  onApply: (range: { start: string; end: string }) => void;
};

const PRESETS = [
  { label: "Today", range: () => ({ from: startToday(), to: startToday() }) },
  { label: "Next 7 days", range: () => ({ from: startToday(), to: addDays(startToday(), 7) }) },
  { label: "Next 30 days", range: () => ({ from: startToday(), to: addDays(startToday(), 30) }) },
  { label: "This month", range: () => ({ from: startOfMonth(startToday()), to: endOfMonth(startToday()) }) },
] satisfies Array<{ label: string; range: () => DateRange }>;

export function BookingDateRangePicker({ start, end, onApply }: Props) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<DateRange | undefined>(() => parseRange(start, end));

  function handleOpenChange(next: boolean) {
    if (next) setDraft(parseRange(start, end));
    setOpen(next);
  }

  function apply() {
    onApply({
      start: draft?.from ? toDateInput(draft.from) : "",
      end: draft?.to ? toDateInput(draft.to) : draft?.from ? toDateInput(draft.from) : "",
    });
    setOpen(false);
  }

  function selectRange(range: DateRange | undefined) {
    setDraft(range);
  }

  return (
    <Popover.Root onOpenChange={handleOpenChange} open={open}>
      <Popover.Trigger asChild>
        <button className={`${styles.trigger} ${open ? styles.triggerOpen : ""}`} type="button">
          <CalendarDays size={16} />
          <span>{formatRangeLabel(start, end)}</span>
          <ChevronDown className={open ? styles.chevronOpen : ""} size={15} />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content align="start" className={styles.popover} collisionPadding={16} sideOffset={9}>
          <header className={styles.header}>
            <div>
              <span>Date range</span>
              <strong>{draftLabel(draft)}</strong>
            </div>
            <Popover.Close aria-label="Close date picker" className={styles.close}><X size={17} /></Popover.Close>
          </header>

          <div className={styles.content}>
            <aside className={styles.presets} aria-label="Quick date ranges">
              <span>Quick select</span>
              {PRESETS.map((preset) => {
                const range = preset.range();
                const selected = sameRange(draft, range);
                return (
                  <button className={selected ? styles.presetSelected : ""} key={preset.label} onClick={() => setDraft(range)} type="button">
                    <span>{preset.label}</span>{selected && <Check size={14} />}
                  </button>
                );
              })}
              <button className={!draft?.from ? styles.presetSelected : ""} onClick={() => setDraft(undefined)} type="button">
                <span>All dates</span>{!draft?.from && <Check size={14} />}
              </button>
            </aside>

            <section className={styles.calendarPanel}>
              <div className={styles.rangeSummary}>
                <div><small>Start</small><strong>{draft?.from ? format(draft.from, "EEE, MMM d") : "Any date"}</strong></div>
                <span aria-hidden="true" />
                <div><small>End</small><strong>{draft?.to ? format(draft.to, "EEE, MMM d") : draft?.from ? "Select end" : "Any date"}</strong></div>
              </div>
              <DayPicker
                classNames={{
                  root: styles.calendar,
                  months: styles.months,
                  month: styles.month,
                  month_caption: styles.monthCaption,
                  caption_label: styles.captionLabel,
                  nav: styles.nav,
                  button_previous: styles.previous,
                  button_next: styles.next,
                  month_grid: styles.monthGrid,
                  weekdays: styles.weekdays,
                  weekday: styles.weekday,
                  weeks: styles.weeks,
                  week: styles.week,
                  day: styles.day,
                  day_button: styles.dayButton,
                  range_start: styles.rangeStart,
                  range_middle: styles.rangeMiddle,
                  range_end: styles.rangeEnd,
                  selected: styles.selected,
                  today: styles.today,
                  outside: styles.outside,
                  disabled: styles.disabled,
                  hidden: styles.hidden,
                }}
                defaultMonth={draft?.from ?? new Date()}
                fixedWeeks
                mode="range"
                onSelect={selectRange}
                selected={draft}
                showOutsideDays
              />
            </section>
          </div>

          <footer className={styles.footer}>
            <button className={styles.reset} onClick={() => setDraft(parseRange(start, end))} type="button"><RotateCcw size={14} /> Reset</button>
            <div>
              <Popover.Close className={styles.cancel}>Cancel</Popover.Close>
              <button className={styles.apply} onClick={apply} type="button">Apply range</button>
            </div>
          </footer>
          <Popover.Arrow className={styles.arrow} />
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}

function parseRange(start: string, end: string): DateRange | undefined {
  if (!start && !end) return undefined;
  return { from: parseDate(start || end), to: parseDate(end || start) };
}

function parseDate(value: string) {
  return new Date(`${value}T00:00:00`);
}

function startToday() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
}

function toDateInput(value: Date) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function sameRange(left: DateRange | undefined, right: DateRange) {
  return Boolean(left?.from && left.to && right.from && right.to && isSameDay(left.from, right.from) && isSameDay(left.to, right.to));
}

function formatRangeLabel(start: string, end: string) {
  if (!start && !end) return "All dates";
  const from = parseDate(start || end);
  const to = parseDate(end || start);
  const sameYear = from.getFullYear() === to.getFullYear();
  const sameMonth = sameYear && from.getMonth() === to.getMonth();
  if (isSameDay(from, to)) return format(from, "MMM d, yyyy");
  if (sameMonth) return `${format(from, "MMM d")} – ${format(to, "d, yyyy")}`;
  if (sameYear) return `${format(from, "MMM d")} – ${format(to, "MMM d, yyyy")}`;
  return `${format(from, "MMM d, yyyy")} – ${format(to, "MMM d, yyyy")}`;
}

function draftLabel(range: DateRange | undefined) {
  if (!range?.from) return "Showing every booking";
  if (!range.to) return `Starting ${format(range.from, "MMMM d, yyyy")}`;
  return `${format(range.from, "MMMM d")} to ${format(range.to, "MMMM d, yyyy")}`;
}
