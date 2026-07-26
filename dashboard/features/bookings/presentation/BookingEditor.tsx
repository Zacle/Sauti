"use client";

import * as Select from "@radix-ui/react-select";
import { format, startOfDay } from "date-fns";
import {
  CalendarClock,
  Check,
  ChevronDown,
  ChevronUp,
  Clock3,
  LoaderCircle,
  Mail,
  Phone,
  Scissors,
  UserRound,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { DayPicker } from "react-day-picker";
import type { BookingViewModel } from "../domain/bookings";
import styles from "./BookingEditor.module.css";

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

const DURATIONS = [30, 45, 60, 90];
const MINUTES = ["00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"];

export function BookingEditor({ booking, busy, error, onClose, onSave }: Props) {
  const [values, setValues] = useState<BookingEditorValues>({
    callerName: booking.callerName,
    callerPhone: booking.callerPhone,
    callerEmail: booking.callerEmail ?? "",
    serviceType: booking.serviceType,
    appointmentAt: toLocalDateTimeInput(booking.appointmentDate),
    durationMinutes: booking.durationMinutes,
  });
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const appointment = new Date(values.appointmentAt);
  const hour24 = appointment.getHours();
  const period = hour24 >= 12 ? "PM" : "AM";
  const hour12 = hour24 % 12 || 12;
  const minute = String(appointment.getMinutes()).padStart(2, "0");
  const durationOptions = DURATIONS.includes(values.durationMinutes)
    ? DURATIONS
    : [...DURATIONS, values.durationMinutes].sort((left, right) => left - right);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [busy, onClose]);

  function updateAppointment(date: Date) {
    setValues((current) => ({ ...current, appointmentAt: toLocalDateTimeInput(date) }));
  }

  function selectDay(day: Date | undefined) {
    if (!day) return;
    const next = new Date(appointment);
    next.setFullYear(day.getFullYear(), day.getMonth(), day.getDate());
    updateAppointment(next);
  }

  function selectHour(nextHour: number) {
    const next = new Date(appointment);
    next.setHours(to24Hour(nextHour, period));
    updateAppointment(next);
  }

  function selectMinute(nextMinute: string) {
    const next = new Date(appointment);
    next.setMinutes(Number(nextMinute));
    updateAppointment(next);
  }

  function selectPeriod(nextPeriod: "AM" | "PM") {
    const next = new Date(appointment);
    next.setHours(to24Hour(hour12, nextPeriod));
    updateAppointment(next);
  }

  return (
    <div
      className={styles.backdrop}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
      role="presentation"
    >
      <form
        aria-describedby="booking-editor-description"
        aria-labelledby="booking-editor-title"
        aria-modal="true"
        className={styles.dialog}
        onSubmit={(event) => {
          event.preventDefault();
          onSave(values);
        }}
        role="dialog"
      >
        <header className={styles.header}>
          <span className={styles.headerIcon}><CalendarClock size={22} /></span>
          <div>
            <small>Edit appointment · {booking.bookingReference}</small>
            <h2 id="booking-editor-title">Reschedule booking</h2>
            <p id="booking-editor-description">Choose a new appointment time or update the customer details.</p>
          </div>
          <button aria-label="Close editor" className={styles.close} disabled={busy} onClick={onClose} ref={closeButtonRef} type="button"><X size={20} /></button>
        </header>

        <div className={styles.body}>
          <section className={styles.details}>
            <div className={styles.sectionHeading}>
              <span>Booking details</span>
              <small>Customer and service</small>
            </div>
            <Field icon={UserRound} label="Customer name">
              <input required value={values.callerName} onChange={(event) => setValues({ ...values, callerName: event.target.value })} />
            </Field>
            <Field icon={Phone} label="Phone number">
              <input required value={values.callerPhone} onChange={(event) => setValues({ ...values, callerPhone: event.target.value })} />
            </Field>
            <Field icon={Mail} label="Email address">
              <input placeholder="Optional" type="email" value={values.callerEmail} onChange={(event) => setValues({ ...values, callerEmail: event.target.value })} />
            </Field>
            <Field icon={Scissors} label="Service">
              <input required value={values.serviceType} onChange={(event) => setValues({ ...values, serviceType: event.target.value })} />
            </Field>
          </section>

          <section className={styles.schedule}>
            <div className={styles.sectionHeading}>
              <span>New schedule</span>
              <small>{format(appointment, "EEEE, MMMM d")}</small>
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
                weekday: styles.weekday,
                day: styles.day,
                day_button: styles.dayButton,
                selected: styles.selected,
                today: styles.today,
                outside: styles.outside,
                disabled: styles.disabled,
                hidden: styles.hidden,
              }}
              defaultMonth={appointment}
              disabled={{ before: startOfDay(new Date()) }}
              fixedWeeks
              mode="single"
              onSelect={selectDay}
              selected={appointment}
              showOutsideDays
            />

            <div className={styles.timeSection}>
              <label><Clock3 size={15} /> Appointment time</label>
              <div className={styles.timeControls}>
                <TimeSelect
                  label="Hour"
                  onChange={(value) => selectHour(Number(value))}
                  options={Array.from({ length: 12 }, (_, index) => String(index + 1))}
                  value={String(hour12)}
                />
                <span>:</span>
                <TimeSelect
                  label="Minute"
                  onChange={selectMinute}
                  options={MINUTES.includes(minute) ? MINUTES : [...MINUTES, minute].sort()}
                  value={minute}
                />
                <div className={styles.periodToggle}>
                  {(["AM", "PM"] as const).map((item) => <button className={period === item ? styles.periodSelected : ""} key={item} onClick={() => selectPeriod(item)} type="button">{item}</button>)}
                </div>
              </div>
            </div>

            <div className={styles.durationSection}>
              <label>Duration</label>
              <div className={styles.durationOptions}>
                {durationOptions.map((duration) => (
                  <button
                    className={values.durationMinutes === duration ? styles.durationSelected : ""}
                    key={duration}
                    onClick={() => setValues({ ...values, durationMinutes: duration })}
                    type="button"
                  >
                    {values.durationMinutes === duration && <Check size={13} />}
                    {duration} min
                  </button>
                ))}
              </div>
            </div>
          </section>
        </div>

        {error && <div className={styles.error} role="alert">{error}</div>}

        <footer className={styles.footer}>
          <div>
            <span>New appointment</span>
            <strong>{format(appointment, "EEE, MMM d")} at {format(appointment, "h:mm a")} · {values.durationMinutes} min</strong>
          </div>
          <button className={styles.cancel} disabled={busy} onClick={onClose} type="button">Keep current</button>
          <button className={styles.save} disabled={busy} type="submit">
            {busy ? <><LoaderCircle className={styles.spinner} size={16} /> Saving changes...</> : "Save new time"}
          </button>
        </footer>
      </form>
    </div>
  );
}

function Field({ icon: Icon, label, children }: { icon: typeof UserRound; label: string; children: React.ReactNode }) {
  return <label className={styles.field}><span><Icon size={14} /> {label}</span>{children}</label>;
}

function TimeSelect({ label, value, options, onChange }: { label: string; value: string; options: string[]; onChange: (value: string) => void }) {
  return (
    <Select.Root onValueChange={onChange} value={value}>
      <Select.Trigger aria-label={label} className={styles.timeSelectTrigger}>
        <Select.Value>{value.padStart(2, "0")}</Select.Value>
        <Select.Icon className={styles.timeSelectIcon}><ChevronDown size={16} /></Select.Icon>
      </Select.Trigger>
      <Select.Portal>
        <Select.Content className={styles.timeSelectContent} collisionPadding={12} position="popper" sideOffset={6}>
          <Select.ScrollUpButton className={styles.timeSelectScroll}><ChevronUp size={15} /></Select.ScrollUpButton>
          <Select.Viewport className={styles.timeSelectViewport}>
            {options.map((option) => (
              <Select.Item className={styles.timeSelectItem} key={option} value={option}>
                <Select.ItemText>{option.padStart(2, "0")}</Select.ItemText>
                <Select.ItemIndicator className={styles.timeSelectIndicator}><Check size={13} /></Select.ItemIndicator>
              </Select.Item>
            ))}
          </Select.Viewport>
          <Select.ScrollDownButton className={styles.timeSelectScroll}><ChevronDown size={15} /></Select.ScrollDownButton>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  );
}

function to24Hour(hour: number, period: "AM" | "PM") {
  if (period === "AM") return hour === 12 ? 0 : hour;
  return hour === 12 ? 12 : hour + 12;
}

function toLocalDateTimeInput(value: Date) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
