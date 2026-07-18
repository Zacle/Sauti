"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ArrowUpRight,
  CalendarCheck2,
  CalendarClock,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Filter,
  LayoutGrid,
  List,
  LoaderCircle,
  Pencil,
  Phone,
  Search,
  Trash2,
  UserRound,
  X,
  XCircle,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import { cancelBooking, deleteBooking, listBookings, updateBooking } from "@/lib/api/bookings";
import type { Agent, Booking } from "@/types/api";
import {
  filterBookings,
  formatAppointment,
  formatTime,
  summarizeBookings,
  toBookingViewModels,
  type BookingStatusFilter,
  type BookingViewModel,
} from "../domain/bookings";
import styles from "./BookingsPage.module.css";
import { BookingDateRangePicker } from "./BookingDateRangePicker";

const FILTERS: Array<{ value: BookingStatusFilter; label: string }> = [
  { value: "all", label: "All" },
  { value: "upcoming", label: "Upcoming" },
  { value: "today", label: "Today" },
  { value: "confirmed", label: "Confirmed" },
  { value: "cancelled", label: "Cancelled" },
  { value: "past", label: "Past" },
];

type BookingEditorValues = {
  callerName: string;
  callerPhone: string;
  callerEmail: string;
  serviceType: string;
  appointmentAt: string;
  durationMinutes: number;
};

type BookingView = "list" | "calendar";

export function BookingsPage() {
  const initialRange = useMemo(() => defaultRange(), []);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<BookingStatusFilter>("upcoming");
  const [agentId, setAgentId] = useState("all");
  const [rangeStart, setRangeStart] = useState(initialRange.start);
  const [rangeEnd, setRangeEnd] = useState(initialRange.end);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [view, setView] = useState<BookingView>("list");
  const [cancellingId, setCancellingId] = useState("");
  const [editingBooking, setEditingBooking] = useState<BookingViewModel | null>(null);
  const [savingId, setSavingId] = useState("");

  useEffect(() => {
    Promise.all([listBookings(), listAgents()])
      .then(([bookingItems, agentItems]) => {
        setBookings(bookingItems);
        setAgents(agentItems);
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load bookings."))
      .finally(() => setLoading(false));
  }, []);

  const viewModels = useMemo(() => toBookingViewModels(bookings, agents), [bookings, agents]);
  const summary = useMemo(() => summarizeBookings(viewModels), [viewModels]);
  const visibleBookings = useMemo(() => {
    const start = rangeStart ? startOfDay(new Date(`${rangeStart}T00:00:00`)).getTime() : Number.NEGATIVE_INFINITY;
    const end = rangeEnd ? endOfDay(new Date(`${rangeEnd}T00:00:00`)).getTime() : Number.POSITIVE_INFINITY;
    return filterBookings(viewModels, filter, query).filter((booking) => (
      (agentId === "all" || booking.agentId === agentId)
      && booking.appointmentDate.getTime() >= start
      && booking.appointmentDate.getTime() <= end
    ));
  }, [agentId, filter, query, rangeEnd, rangeStart, viewModels]);
  const groupedBookings = useMemo(() => groupBookings(visibleBookings), [visibleBookings]);

  async function onCancel(booking: BookingViewModel) {
    if (booking.status === "cancelled" || cancellingId) return;
    if (!window.confirm(`Cancel ${booking.serviceType} for ${booking.callerName}?`)) return;
    setCancellingId(booking.id);
    setError("");
    try {
      const updated = await cancelBooking(booking.id);
      setBookings((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to cancel this booking.");
    } finally {
      setCancellingId("");
    }
  }

  async function onUpdate(booking: Booking, values: BookingEditorValues) {
    setSavingId(booking.id);
    setError("");
    try {
      const updated = await updateBooking(booking.id, {
        ...values,
        appointmentAt: new Date(values.appointmentAt).toISOString(),
        callerEmail: values.callerEmail.trim() || null,
        capturedData: booking.capturedData,
      });
      setBookings((current) => current.map((item) => item.id === updated.id ? updated : item));
      setEditingBooking(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to update this booking.");
    } finally {
      setSavingId("");
    }
  }

  async function onDelete(booking: BookingViewModel) {
    if (!window.confirm(`Permanently delete booking ${booking.bookingReference}? This cannot be undone.`)) return;
    setSavingId(booking.id);
    setError("");
    try {
      await deleteBooking(booking.id);
      setBookings((current) => current.filter((item) => item.id !== booking.id));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to delete this booking.");
    } finally {
      setSavingId("");
    }
  }

  function clearFilters() {
    setQuery("");
    setFilter("all");
    setAgentId("all");
    setRangeStart("");
    setRangeEnd("");
  }

  const activeAgent = agents.find((agent) => agent.id === agentId);
  const hasActiveFilters = Boolean(query || filter !== "all" || agentId !== "all" || rangeStart || rangeEnd);

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>Bookings</h1>
          <p>Track appointments captured by agents, where they came from, and what needs follow-up.</p>
        </div>
      </header>

      <section className={styles.metrics} aria-label="Booking summary">
        <Metric detail="Next 7 days" icon={CalendarCheck2} label="Upcoming" value={summary.upcoming} tone="cyan" />
        <Metric detail="Scheduled for today" icon={Clock3} label="Today" value={summary.today} tone="blue" />
        <Metric detail="All confirmed bookings" icon={CheckCircle2} label="Confirmed" value={summary.confirmed} tone="green" />
        <Metric detail="All cancelled bookings" icon={XCircle} label="Cancelled" value={summary.cancelled} tone="orange" />
        <article className={styles.nextMetric}>
          <span><CalendarClock size={20} /></span>
          <div><small>Next appointment</small><strong>{summary.nextBooking ? formatAppointment(summary.nextBooking.appointmentDate) : "No upcoming booking"}</strong></div>
        </article>
      </section>

      <section className={styles.controls}>
        <div className={styles.controlRow}>
          <label className={styles.search}>
            <Search size={18} />
            <input aria-label="Search bookings" onChange={(event) => setQuery(event.target.value)} placeholder="Search bookings..." value={query} />
            {query && <button aria-label="Clear search" onClick={() => setQuery("")} type="button"><X size={15} /></button>}
          </label>
          <BookingDateRangePicker
            end={rangeEnd}
            onApply={(range) => { setRangeStart(range.start); setRangeEnd(range.end); }}
            start={rangeStart}
          />
          <button className={`${styles.filterButton} ${filtersOpen ? styles.controlActive : ""}`} onClick={() => setFiltersOpen((open) => !open)} type="button">
            <Filter size={16} /> Filters {agentId !== "all" && <i>1</i>}
          </button>
          <div className={styles.viewToggle} aria-label="Booking view">
            <button aria-label="List view" className={view === "list" ? styles.selected : ""} onClick={() => setView("list")} type="button"><List size={16} /> List</button>
            <button aria-label="Calendar view" className={view === "calendar" ? styles.selected : ""} onClick={() => setView("calendar")} type="button"><LayoutGrid size={16} /> Calendar</button>
          </div>
          <div className={styles.statusTabs}>
            {FILTERS.map((item) => <button className={filter === item.value ? styles.selected : ""} key={item.value} onClick={() => setFilter(item.value)} type="button">{item.label}</button>)}
          </div>
        </div>
        {filtersOpen && (
          <div className={styles.filterPanel}>
            <label>Agent<select value={agentId} onChange={(event) => setAgentId(event.target.value)}><option value="all">All agents</option>{agents.map((agent) => <option key={agent.id} value={agent.id}>{agent.name}</option>)}</select></label>
            <p>Filter the current result set by the agent responsible for the booking.</p>
          </div>
        )}
        {hasActiveFilters && (
          <div className={styles.chips}>
            {(rangeStart || rangeEnd) && <button onClick={() => { setRangeStart(""); setRangeEnd(""); }} type="button">{formatRange(rangeStart, rangeEnd)} <X size={13} /></button>}
            {filter !== "all" && <button onClick={() => setFilter("all")} type="button">{FILTERS.find((item) => item.value === filter)?.label} <X size={13} /></button>}
            {activeAgent && <button onClick={() => setAgentId("all")} type="button">Agent: {activeAgent.name} <X size={13} /></button>}
            <button className={styles.clearAll} onClick={clearFilters} type="button">Clear all</button>
          </div>
        )}
      </section>

      {error && <div className={styles.error}>{error}</div>}
      {loading ? (
        <div className={styles.loading}><LoaderCircle className="spin" size={22} /> Loading bookings...</div>
      ) : viewModels.length === 0 ? (
        <Empty icon={<CalendarDays size={25} />} title="No bookings yet">Bookings created by agents or owners will appear here, even when an external calendar needs follow-up.</Empty>
      ) : visibleBookings.length === 0 ? (
        <Empty icon={<Search size={25} />} title="No matching bookings">Adjust the date, agent, search, or status filters to see more appointments.</Empty>
      ) : view === "calendar" ? (
        <CalendarView bookings={visibleBookings} onEdit={setEditingBooking} />
      ) : (
        <section className={styles.groupList}>
          {groupedBookings.map(([label, items]) => (
            <section className={styles.dayGroup} key={label}>
              <h2>{label}<span>{items.length} {items.length === 1 ? "booking" : "bookings"}</span></h2>
              <div className={styles.dayRows}>
                {items.map((booking) => (
                  <BookingRow
                    booking={booking}
                    cancelling={cancellingId === booking.id}
                    key={booking.id}
                    onCancel={() => void onCancel(booking)}
                    onDelete={() => void onDelete(booking)}
                    onEdit={() => setEditingBooking(booking)}
                    saving={savingId === booking.id}
                  />
                ))}
              </div>
            </section>
          ))}
        </section>
      )}
      {editingBooking && <BookingEditor booking={editingBooking} busy={savingId === editingBooking.id} onClose={() => setEditingBooking(null)} onSave={(values) => void onUpdate(editingBooking, values)} />}
    </main>
  );
}

function Metric({ icon: Icon, label, value, detail, tone }: { icon: typeof CalendarCheck2; label: string; value: number; detail: string; tone: "cyan" | "green" | "blue" | "orange" }) {
  return <article className={`${styles.metric} ${styles[tone]}`}><span><Icon size={20} /></span><div><div><strong>{value}</strong><small>{label}</small></div><p>{detail}</p></div></article>;
}

function BookingRow({ booking, cancelling, saving, onCancel, onDelete, onEdit }: { booking: BookingViewModel; cancelling: boolean; saving: boolean; onCancel: () => void; onDelete: () => void; onEdit: () => void }) {
  const cancelled = booking.status === "cancelled";
  const synced = booking.calendarSyncStatus === "synced";
  return (
    <article className={`${styles.bookingRow} ${cancelled ? styles.cancelled : ""}`}>
      <div className={styles.timeColumn}><strong>{formatTime(booking.appointmentDate)}</strong><span><Clock3 size={13} /> {booking.durationMinutes} min</span></div>
      <div className={styles.bookingBody}>
        <div className={styles.bookingTitle}><i className={cancelled ? styles.cancelledDot : synced ? styles.syncedDot : styles.pendingDot} /><h3>{booking.serviceType}</h3></div>
        <p><UserRound size={14} /> {booking.callerName}</p>
        <div className={styles.tags}>
          <a href={`tel:${booking.callerPhone}`}><Phone size={13} /> {booking.callerPhone}</a>
          <span><CalendarDays size={13} /> {booking.agentName}</span>
          <span><ArrowUpRight size={13} /> {booking.sourceLabel}</span>
          <span>{booking.bookingReference}</span>
        </div>
        <div className={styles.rowMeta}><span>Booked <strong>{formatAppointment(booking.bookedDate)}</strong></span><span>Calendar <strong>{synced ? "Synced" : "Owner follow-up"}</strong></span></div>
      </div>
      <div className={styles.rowActions}>
        <span className={`${styles.status} ${cancelled ? styles.statusCancelled : synced ? styles.statusConfirmed : styles.statusPending}`}>{humanize(booking.status)}</span>
        {!cancelled && <button disabled={saving || cancelling} onClick={onEdit} type="button"><Pencil size={14} /> Reschedule</button>}
        <a href={`tel:${booking.callerPhone}`}><Phone size={14} /> Call</a>
        {!cancelled && <button className={styles.cancelAction} disabled={saving || cancelling} onClick={onCancel} type="button">{cancelling ? "Cancelling..." : "Cancel"}</button>}
        <button aria-label={`Delete ${booking.bookingReference}`} className={styles.deleteAction} disabled={saving || cancelling} onClick={onDelete} type="button"><Trash2 size={15} /></button>
      </div>
    </article>
  );
}

function CalendarView({ bookings, onEdit }: { bookings: BookingViewModel[]; onEdit: (booking: BookingViewModel) => void }) {
  const days = useMemo(() => {
    const map = new Map<string, BookingViewModel[]>();
    bookings.forEach((booking) => {
      const key = toDateInput(booking.appointmentDate);
      map.set(key, [...(map.get(key) ?? []), booking]);
    });
    return [...map.entries()].sort(([left], [right]) => left.localeCompare(right));
  }, [bookings]);
  return <section className={styles.calendarView}>{days.map(([date, items]) => <article key={date}><header><strong>{new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { weekday: "short" })}</strong><span>{new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</span></header><div>{items.map((booking) => <button key={booking.id} onClick={() => onEdit(booking)} type="button"><time>{formatTime(booking.appointmentDate)}</time><strong>{booking.serviceType}</strong><span>{booking.callerName}</span></button>)}</div></article>)}</section>;
}

function Empty({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return <section className={styles.empty}><span>{icon}</span><h2>{title}</h2><p>{children}</p></section>;
}

function BookingEditor({ booking, busy, onClose, onSave }: { booking: BookingViewModel; busy: boolean; onClose: () => void; onSave: (values: BookingEditorValues) => void }) {
  const [values, setValues] = useState<BookingEditorValues>({ callerName: booking.callerName, callerPhone: booking.callerPhone, callerEmail: booking.callerEmail ?? "", serviceType: booking.serviceType, appointmentAt: toLocalDateTimeInput(booking.appointmentDate), durationMinutes: booking.durationMinutes });
  return <div className={styles.editorBackdrop} role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><form className={styles.editor} onSubmit={(event) => { event.preventDefault(); onSave(values); }}><header><div><small>{booking.bookingReference}</small><h2>Reschedule or edit booking</h2></div><button aria-label="Close editor" onClick={onClose} type="button"><X size={20} /></button></header><div className={styles.editorGrid}><label>Customer name<input required value={values.callerName} onChange={(event) => setValues({ ...values, callerName: event.target.value })} /></label><label>Phone number<input required value={values.callerPhone} onChange={(event) => setValues({ ...values, callerPhone: event.target.value })} /></label><label>Email<input type="email" value={values.callerEmail} onChange={(event) => setValues({ ...values, callerEmail: event.target.value })} /></label><label>Service<input required value={values.serviceType} onChange={(event) => setValues({ ...values, serviceType: event.target.value })} /></label><label>Date and time<input required type="datetime-local" value={values.appointmentAt} onChange={(event) => setValues({ ...values, appointmentAt: event.target.value })} /></label><label>Duration (minutes)<input min={5} max={480} required type="number" value={values.durationMinutes} onChange={(event) => setValues({ ...values, durationMinutes: Number(event.target.value) })} /></label></div><footer><button onClick={onClose} type="button">Close</button><button disabled={busy} type="submit">{busy ? "Saving..." : "Save changes"}</button></footer></form></div>;
}

function groupBookings(bookings: BookingViewModel[]): Array<[string, BookingViewModel[]]> {
  const groups = new Map<string, BookingViewModel[]>();
  bookings.forEach((booking) => {
    const label = dayLabel(booking.appointmentDate);
    groups.set(label, [...(groups.get(label) ?? []), booking]);
  });
  return [...groups.entries()];
}

function dayLabel(value: Date) {
  const today = startOfDay(new Date());
  const date = startOfDay(value);
  const difference = Math.round((date.getTime() - today.getTime()) / 86_400_000);
  const formatted = value.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
  if (difference === 0) return `Today · ${formatted}`;
  if (difference === 1) return `Tomorrow · ${formatted}`;
  return value.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric", year: "numeric" });
}

function defaultRange() { const start = new Date(); const end = new Date(); end.setDate(end.getDate() + 7); return { start: toDateInput(start), end: toDateInput(end) }; }
function toDateInput(value: Date) { const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000); return local.toISOString().slice(0, 10); }
function toLocalDateTimeInput(value: Date) { const local = new Date(value.getTime() - value.getTimezoneOffset() * 60_000); return local.toISOString().slice(0, 16); }
function startOfDay(value: Date) { const copy = new Date(value); copy.setHours(0, 0, 0, 0); return copy; }
function endOfDay(value: Date) { const copy = new Date(value); copy.setHours(23, 59, 59, 999); return copy; }
function formatRange(start: string, end: string) { if (!start && !end) return "All dates"; const formatter = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }); const from = start ? formatter.format(new Date(`${start}T00:00:00`)) : "Any"; const to = end ? formatter.format(new Date(`${end}T00:00:00`)) : "Any"; return `${from} – ${to}`; }
function humanize(value: string) { return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase()); }
