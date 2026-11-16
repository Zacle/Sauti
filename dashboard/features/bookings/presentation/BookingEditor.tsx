"use client";

import * as React from "react";
import { addMinutes, format, isSameDay, startOfDay } from "date-fns";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { ArrowRight, ChevronDown, LoaderCircle, X } from "lucide-react";
import { DayPicker } from "react-day-picker";
import type { BookingViewModel } from "../domain/bookings";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label } from "@/components/ui/field";
import { cn } from "@/lib/utils";

export type BookingEditorValues = {
  callerName: string;
  callerPhone: string;
  callerEmail: string;
  serviceType: string;
  appointmentAt: string;
  durationMinutes: number;
};

type Props = {
  booking: BookingViewModel;
  busy: boolean;
  error: string;
  onClose: () => void;
  onSave: (values: BookingEditorValues) => void;
};

const DURATIONS = [15, 30, 45, 60, 90];
const DAY_START = 7 * 60;
const DAY_END = 20 * 60;
const STEP = 15;

// react-day-picker reads its variables from .rdp-root, so they're set inline.
const calendarStyle = {
  "--rdp-accent-color": "var(--color-signal)",
  "--rdp-accent-background-color": "color-mix(in oklab, var(--color-signal) 14%, transparent)",
  "--rdp-day-height": "2.35rem",
  "--rdp-day-width": "2.35rem",
  "--rdp-day_button-height": "2.15rem",
  "--rdp-day_button-width": "2.15rem",
  "--rdp-day_button-border-radius": "7px",
  "--rdp-selected-border": "0",
  "--rdp-today-color": "var(--color-signal)",
  "--rdp-nav_button-height": "1.9rem",
  "--rdp-nav_button-width": "1.9rem",
  "--rdp-outside-opacity": "0.3",
  "--rdp-disabled-opacity": "0.3",
} as React.CSSProperties;

const calendarClasses =
  "m-0 font-sans text-fg [&_.rdp-caption_label]:text-[0.9rem] [&_.rdp-caption_label]:font-semibold [&_.rdp-caption_label]:text-ink [&_.rdp-weekday]:text-[0.72rem] [&_.rdp-weekday]:font-medium [&_.rdp-weekday]:text-fg-muted [&_.rdp-day_button]:font-sans [&_.rdp-day_button]:text-[0.84rem] [&_.rdp-day_button]:tabular-nums [&_.rdp-day_button:hover]:bg-surface-2 [&_.rdp-selected_.rdp-day_button]:bg-signal [&_.rdp-selected_.rdp-day_button]:font-semibold [&_.rdp-selected_.rdp-day_button]:text-signal-fg [&_.rdp-chevron]:fill-fg-soft [&_.rdp-button_previous]:rounded-[6px] [&_.rdp-button_next]:rounded-[6px] [&_.rdp-button_previous:hover]:bg-surface-2 [&_.rdp-button_next:hover]:bg-surface-2";

/** Reschedule a booking: pick a day, tap a time, check the before → after line, save. */
export function BookingEditor({ booking, busy, error, onClose, onSave }: Props) {
  const original = booking.appointmentDate;
  const [values, setValues] = React.useState<BookingEditorValues>({
    callerName: booking.callerName,
    callerPhone: booking.callerPhone,
    callerEmail: booking.callerEmail ?? "",
    serviceType: booking.serviceType,
    appointmentAt: toLocalDateTimeInput(booking.appointmentDate),
    durationMinutes: booking.durationMinutes,
  });
  const [detailsOpen, setDetailsOpen] = React.useState(false);
  const appointment = new Date(values.appointmentAt);
  const minutesOfDay = appointment.getHours() * 60 + appointment.getMinutes();
  const slotListRef = React.useRef<HTMLDivElement>(null);

  const slots = React.useMemo(() => {
    const list: number[] = [];
    for (let minute = DAY_START; minute < DAY_END; minute += STEP) list.push(minute);
    if (!list.includes(minutesOfDay)) list.push(minutesOfDay);
    return list.sort((left, right) => left - right);
  }, [minutesOfDay]);

  const durations = DURATIONS.includes(values.durationMinutes)
    ? DURATIONS
    : [...DURATIONS, values.durationMinutes].sort((left, right) => left - right);

  const now = Date.now();
  const changed = appointment.getTime() !== original.getTime() || values.durationMinutes !== booking.durationMinutes;
  const detailsChanged = values.callerName !== booking.callerName
    || values.callerPhone !== booking.callerPhone
    || values.callerEmail !== (booking.callerEmail ?? "")
    || values.serviceType !== booking.serviceType;

  // Keep the selected time in view when the dialog opens or the day changes.
  React.useEffect(() => {
    const selected = slotListRef.current?.querySelector<HTMLElement>("[aria-checked='true']");
    selected?.scrollIntoView({ block: "center" });
  }, [values.appointmentAt]);

  function setDateTime(next: Date) {
    setValues((current) => ({ ...current, appointmentAt: toLocalDateTimeInput(next) }));
  }

  function selectDay(day: Date | undefined) {
    if (!day) return;
    const next = new Date(appointment);
    next.setFullYear(day.getFullYear(), day.getMonth(), day.getDate());
    setDateTime(next);
  }

  function selectSlot(minute: number) {
    const next = new Date(appointment);
    next.setHours(Math.floor(minute / 60), minute % 60, 0, 0);
    setDateTime(next);
  }

  const groups = [
    { label: "Morning", slots: slots.filter((minute) => minute < 12 * 60) },
    { label: "Afternoon", slots: slots.filter((minute) => minute >= 12 * 60 && minute < 17 * 60) },
    { label: "Evening", slots: slots.filter((minute) => minute >= 17 * 60) },
  ].filter((group) => group.slots.length);

  return (
    <DialogPrimitive.Root open onOpenChange={(open) => { if (!open && !busy) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[150] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out]" />
        <DialogPrimitive.Content
          onEscapeKeyDown={(event) => { if (busy) event.preventDefault(); }}
          onPointerDownOutside={(event) => { if (busy) event.preventDefault(); }}
          className="fixed left-1/2 top-1/2 z-[151] flex max-h-[min(92dvh,46rem)] w-[min(96vw,52rem)] -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[12px] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)]"
        >
          <form
            className="flex min-h-0 flex-1 flex-col"
            onSubmit={(event) => {
              event.preventDefault();
              onSave(values);
            }}
          >
            <header className="flex items-start gap-4 border-b border-line px-6 py-4">
              <div className="min-w-0 flex-1">
                <DialogPrimitive.Title className="m-0 text-[1.05rem] font-semibold text-ink">
                  Reschedule {values.callerName || "booking"}
                </DialogPrimitive.Title>
                <DialogPrimitive.Description className="m-0 mt-0.5 text-[0.82rem] text-fg-muted">
                  {values.serviceType} · {booking.bookingReference} · currently {format(original, "EEE d MMM, HH:mm")}
                </DialogPrimitive.Description>
              </div>
              <DialogPrimitive.Close
                disabled={busy}
                aria-label="Close"
                className="grid size-8 shrink-0 cursor-pointer place-items-center rounded-[7px] border-0 bg-transparent p-0 text-fg-muted transition-colors hover:bg-tint/[0.06] hover:text-ink"
              >
                <X className="size-4" />
              </DialogPrimitive.Close>
            </header>

            <div className="min-h-0 flex-1 overflow-y-auto">
              <div className="grid grid-cols-1 md:grid-cols-[auto_minmax(0,1fr)]">
                <section aria-label="Day" className="border-b border-line px-6 py-5 md:border-b-0 md:border-r">
                  <DayPicker
                    className={calendarClasses}
                    style={calendarStyle}
                    defaultMonth={appointment}
                    disabled={{ before: startOfDay(new Date()) }}
                    mode="single"
                    onSelect={selectDay}
                    selected={appointment}
                    showOutsideDays
                    weekStartsOn={1}
                  />
                </section>

                <section aria-label="Time" className="flex min-w-0 flex-col px-6 py-5">
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="m-0 text-[0.9rem] font-semibold text-ink">{format(appointment, "EEEE d MMMM")}</p>
                    <span className="text-[0.76rem] text-fg-muted">Times in your timezone</span>
                  </div>

                  <div ref={slotListRef} role="radiogroup" aria-label="Start time" className="mt-3 grid max-h-[17.5rem] gap-4 overflow-y-auto pr-1">
                    {groups.map((group) => (
                      <div key={group.label}>
                        <p className="m-0 mb-1.5 text-[0.72rem] font-medium text-fg-muted">{group.label}</p>
                        <div className="grid grid-cols-4 gap-1.5 sm:grid-cols-5">
                          {group.slots.map((minute) => {
                            const slotDate = new Date(appointment);
                            slotDate.setHours(Math.floor(minute / 60), minute % 60, 0, 0);
                            const past = slotDate.getTime() < now;
                            const selected = minute === minutesOfDay;
                            const current = isSameDay(slotDate, original) && slotDate.getHours() === original.getHours() && slotDate.getMinutes() === original.getMinutes();
                            return (
                              <button
                                key={minute}
                                type="button"
                                role="radio"
                                aria-checked={selected}
                                disabled={past}
                                onClick={() => selectSlot(minute)}
                                title={current ? "Current time" : undefined}
                                className={cn(
                                  "relative h-8 cursor-pointer rounded-[6px] border font-sans text-[0.8rem] tabular-nums transition-colors disabled:cursor-not-allowed disabled:opacity-35",
                                  selected
                                    ? "border-signal bg-signal font-semibold text-signal-fg"
                                    : "border-line bg-surface text-fg hover:border-line-strong hover:bg-surface-2",
                                )}
                              >
                                {format(slotDate, "HH:mm")}
                                {current && !selected ? <span className="absolute right-1 top-1 size-1 rounded-full bg-fg-muted" aria-hidden="true" /> : null}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="mt-5 border-t border-line pt-4">
                    <p className="m-0 mb-2 text-[0.8rem] font-medium text-ink">Length</p>
                    <div role="radiogroup" aria-label="Duration" className="inline-flex flex-wrap gap-0.5 rounded-[8px] border border-line bg-surface-2 p-0.5">
                      {durations.map((duration) => (
                        <button
                          key={duration}
                          type="button"
                          role="radio"
                          aria-checked={values.durationMinutes === duration}
                          onClick={() => setValues((current) => ({ ...current, durationMinutes: duration }))}
                          className={cn(
                            "h-7 cursor-pointer rounded-[6px] border-0 px-3 font-sans text-[0.8rem] tabular-nums transition-colors",
                            values.durationMinutes === duration ? "bg-selected text-selected-fg shadow-[var(--shadow-card)]" : "bg-transparent text-fg-soft hover:text-fg",
                          )}
                        >
                          {duration} min
                        </button>
                      ))}
                    </div>
                  </div>
                </section>
              </div>

              <section className="border-t border-line">
                <button
                  type="button"
                  aria-expanded={detailsOpen}
                  onClick={() => setDetailsOpen((open) => !open)}
                  className="flex w-full cursor-pointer items-center gap-2 border-0 bg-transparent px-6 py-3.5 text-left font-sans text-[0.86rem] text-ink transition-colors hover:bg-surface-2"
                >
                  <ChevronDown className={cn("size-4 text-fg-muted transition-transform", detailsOpen && "rotate-180")} />
                  Customer details
                  <span className="ml-auto text-[0.78rem] text-fg-muted">{detailsChanged ? "Edited" : `${values.callerName} · ${values.callerPhone}`}</span>
                </button>
                {detailsOpen ? (
                  <div className="grid grid-cols-1 gap-4 px-6 pb-5 pt-1 sm:grid-cols-2">
                    <DetailField label="Name" value={values.callerName} required onChange={(callerName) => setValues((current) => ({ ...current, callerName }))} />
                    <DetailField label="Phone" value={values.callerPhone} required onChange={(callerPhone) => setValues((current) => ({ ...current, callerPhone }))} />
                    <DetailField label="Email" type="email" placeholder="Optional" value={values.callerEmail} onChange={(callerEmail) => setValues((current) => ({ ...current, callerEmail }))} />
                    <DetailField label="Service" value={values.serviceType} required onChange={(serviceType) => setValues((current) => ({ ...current, serviceType }))} />
                  </div>
                ) : null}
              </section>
            </div>

            {error ? <div className="px-6 pb-1 pt-3"><Alert tone="error">{error}</Alert></div> : null}

            <footer className="flex flex-wrap items-center gap-3 border-t border-line bg-surface-2 px-6 py-3.5">
              <p className="m-0 flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1 text-[0.84rem]">
                <span className={cn("tabular-nums", changed ? "text-fg-muted line-through decoration-fg-muted/60" : "text-fg")}>
                  {format(original, "EEE d MMM, HH:mm")}
                </span>
                {changed ? (
                  <>
                    <ArrowRight className="size-3.5 text-fg-muted" />
                    <span className="font-medium tabular-nums text-ink">
                      {format(appointment, "EEE d MMM, HH:mm")}–{format(addMinutes(appointment, values.durationMinutes), "HH:mm")}
                    </span>
                  </>
                ) : (
                  <span className="text-fg-muted">· pick a new time</span>
                )}
              </p>
              <Button variant="ghost" size="xs" disabled={busy} onClick={onClose}>Keep current</Button>
              <Button size="xs" type="submit" disabled={busy || (!changed && !detailsChanged)}>
                {busy ? <LoaderCircle className="animate-spin" /> : null}
                {busy ? "Saving…" : changed ? "Move booking" : "Save details"}
              </Button>
            </footer>
          </form>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function DetailField({ label, value, onChange, type = "text", placeholder, required = false }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
}) {
  const id = React.useId();
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id} className="text-[0.8rem]">{label}</Label>
      <Input id={id} type={type} required={required} placeholder={placeholder} value={value} onChange={(event) => onChange(event.target.value)} className="h-9 text-[0.86rem]" />
    </div>
  );
}

function toLocalDateTimeInput(value: Date) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
