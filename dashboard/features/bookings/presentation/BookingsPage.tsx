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
import polish from "./BookingsPagePolish.module.css";
import { BookingActionDialog, type BookingAction } from "./BookingActionDialog";
import { BookingDateRangePicker } from "./BookingDateRangePicker";
import { BookingEditor, type BookingEditorValues } from "./BookingEditor";

const FILTERS: Array<{ value: BookingStatusFilter; label: string }> = [
  { value: "all", label: "All" },
  { value: "upcoming", label: "Upcoming" },
  { value: "today", label: "Today" },
  { value: "confirmed", label: "Confirmed" },
  { value: "cancelled", label: "Cancelled" },
  { value: "past", label: "Past" },
];

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
  const [editorError, setEditorError] = useState("");
  const [savingId, setSavingId] = useState("");
  const [pendingAction, setPendingAction] = useState<BookingAction | null>(null);
  const [actionError, setActionError] = useState("");

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

  function requestAction(kind: BookingAction["kind"], booking: BookingViewModel) {
    if (booking.status === "cancelled" && kind === "cancel") return;
    if (cancellingId || savingId) return;
    setActionError("");
    setPendingAction({ kind, booking });
  }

  async function confirmAction() {
    if (!pendingAction) return;
    const { kind, booking } = pendingAction;
    if (kind === "cancel") setCancellingId(booking.id);
    else setSavingId(booking.id);
    setError("");
    setActionError("");
    try {
      if (kind === "cancel") {
        const updated = await cancelBooking(booking.id);
        setBookings((current) => current.map((item) => item.id === updated.id ? updated : item));
      } else {
        await deleteBooking(booking.id);
        setBookings((current) => current.filter((item) => item.id !== booking.id));
      }
      setPendingAction(null);
    } catch (caught) {
      setActionError(caught instanceof Error
        ? caught.message
        : kind === "cancel"
          ? "Unable to cancel this booking."
          : "Unable to delete this booking.");
    } finally {
      if (kind === "cancel") setCancellingId("");
      else setSavingId("");
    }
  }

  async function onUpdate(booking: Booking, values: BookingEditorValues) {
    setSavingId(booking.id);
    setEditorError("");
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
      setEditorError(caught instanceof Error ? caught.message : "Unable to update this booking.");
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

      <section className={`${styles.metrics} ${polish.metrics}`} aria-label="Booking summary">
        <Metric detail="Next 7 days" icon={CalendarCheck2} label="Upcoming" value={summary.upcoming} tone="cyan" />
        <Metric detail="Scheduled for today" icon={Clock3} label="Today" value={summary.today} tone="blue" />
        <Metric detail="All confirmed bookings" icon={CheckCircle2} label="Confirmed" value={summary.confirmed} tone="green" />
        <Metric detail="All cancelled bookings" icon={XCircle} label="Cancelled" value={summary.cancelled} tone="orange" />
        <article className={`${styles.nextMetric} ${polish.metricCard}`}>
          <span><CalendarClock size={20} /></span>
          <div><small>Next appointment</small><strong>{summary.nextBooking ? formatAppointment(summary.nextBooking.appointmentDate) : "No upcoming booking"}</strong></div>
        </article>
      </section>

      <section className={styles.controls}>
        <div className={`${styles.controlRow} ${polish.controlRow}`}>
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
        <CalendarView bookings={visibleBookings} onEdit={(booking) => {
          setEditorError("");
          setEditingBooking(booking);
        }} />
      ) : (
        <section className={styles.groupList}>
          {groupedBookings.map(([label, items]) => (
            <section className={styles.dayGroup} key={label}>
              <h2>{label}<span>{items.length} {items.length === 1 ? "booking" : "bookings"}</span></h2>
              <div className={`${styles.dayRows} ${polish.dayRows}`}>
                {items.map((booking) => (
                  <BookingRow
                    booking={booking}
                    cancelling={cancellingId === booking.id}
                    key={booking.id}
                    onCancel={() => requestAction("cancel", booking)}
                    onDelete={() => requestAction("delete", booking)}
                    onEdit={() => {
                      setEditorError("");
                      setEditingBooking(booking);
                    }}
                    saving={savingId === booking.id}
                  />
                ))}
              </div>
            </section>
          ))}
        </section>
      )}
      {editingBooking && (
        <BookingEditor
          booking={editingBooking}
          busy={savingId === editingBooking.id}
          error={editorError}
          onClose={() => {
            if (!savingId) {
              setEditorError("");
              setEditingBooking(null);
            }
          }}
          onSave={(values) => void onUpdate(editingBooking, values)}
        />
      )}
      {pendingAction && (
        <BookingActionDialog
          action={pendingAction}
          busy={pendingAction.kind === "cancel"
            ? cancellingId === pendingAction.booking.id
            : savingId === pendingAction.booking.id}
          error={actionError}
          onClose={() => {
            if (!cancellingId && !savingId) setPendingAction(null);
          }}
          onConfirm={() => void confirmAction()}
        />
      )}
    </main>
  );
}

function Metric({ icon: Icon, label, value, detail, tone }: { icon: typeof CalendarCheck2; label: string; value: number; detail: string; tone: "cyan" | "green" | "blue" | "orange" }) {
  return <article className={`${styles.metric} ${styles[tone]} ${polish.metricCard} ${polish[tone]}`}><span><Icon size={20} /></span><div><div><strong>{value}</strong><small>{label}</small></div><p>{detail}</p></div></article>;
}

function BookingRow({ booking, cancelling, saving, onCancel, onDelete, onEdit }: { booking: BookingViewModel; cancelling: boolean; saving: boolean; onCancel: () => void; onDelete: () => void; onEdit: () => void }) {
  const cancelled = booking.status === "cancelled";
  const synced = booking.calendarSyncStatus === "synced";
  return (
    <article className={`${styles.bookingRow} ${polish.bookingRow} ${cancelled ? styles.cancelled : ""}`}>
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
        <span className={`${styles.status} ${cancelled ? styles.statusCancelled : synced ? `${styles.statusConfirmed} ${polish.statusConfirmed}` : styles.statusPending}`}>{humanize(booking.status)}</span>
        {!cancelled && <button disabled={saving || cancelling} onClick={onEdit} type="button"><Pencil size={14} /> Reschedule</button>}
        <a href={`tel:${booking.callerPhone}`}><Phone size={14} /> Call</a>
        {!cancelled && <button className={styles.cancelAction} disabled={saving || cancelling} onClick={onCancel} type="button"><X size={14} /> {cancelling ? "Cancelling..." : "Cancel"}</button>}
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
function startOfDay(value: Date) { const copy = new Date(value); copy.setHours(0, 0, 0, 0); return copy; }
function endOfDay(value: Date) { const copy = new Date(value); copy.setHours(23, 59, 59, 999); return copy; }
function formatRange(start: string, end: string) { if (!start && !end) return "All dates"; const formatter = new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }); const from = start ? formatter.format(new Date(`${start}T00:00:00`)) : "Any"; const to = end ? formatter.format(new Date(`${end}T00:00:00`)) : "Any"; return `${from} – ${to}`; }
function humanize(value: string) { return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase()); }
