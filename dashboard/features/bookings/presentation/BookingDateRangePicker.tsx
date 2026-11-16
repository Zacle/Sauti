"use client";

import { addDays, endOfMonth, startOfMonth } from "date-fns";
import { DateRangePicker, type DateRange, type DateRangePreset } from "@/components/ui/date-range-picker";

type Props = {
  start: string;
  end: string;
  onApply: (range: { start: string; end: string }) => void;
};

const PRESETS: DateRangePreset[] = [
  { label: "Today", range: () => ({ from: startToday(), to: startToday() }) },
  { label: "Next 7 days", range: () => ({ from: startToday(), to: addDays(startToday(), 7) }) },
  { label: "Next 30 days", range: () => ({ from: startToday(), to: addDays(startToday(), 30) }) },
  { label: "This month", range: () => ({ from: startOfMonth(startToday()), to: endOfMonth(startToday()) }) },
];

/** Bookings keep their range as yyyy-mm-dd strings; this adapts them to the shared picker. */
export function BookingDateRangePicker({ start, end, onApply }: Props) {
  return (
    <DateRangePicker
      value={parseRange(start, end)}
      presets={PRESETS}
      onChange={(range?: DateRange) =>
        onApply({
          start: range?.from ? toDateInput(range.from) : "",
          end: range?.to ? toDateInput(range.to) : range?.from ? toDateInput(range.from) : "",
        })}
    />
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
