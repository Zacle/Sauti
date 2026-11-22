"use client";

import { Select } from "@/components/ui/select";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  CalendarDays,
  Filter,
  LayoutGrid,
  List,
  Phone,
  Search,
  Trash2,
  X,
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
import { Button } from "@/components/ui/button";
import { Alert, Input } from "@/components/ui/field";
import { Card, EmptyState, Skeleton, Tabs, TabsList, TabsTrigger, Tooltip } from "@/components/ui/primitives";
import { PageHeader, StatStrip } from "@/components/ui/console";
import { toast } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
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
        // A link from a call shows its booking, whatever its date or status: /bookings?booking=<id>.
        const linked = bookingItems.find((item) => item.id === new URLSearchParams(window.location.search).get("booking"));
        if (linked) {
          setFilter("all");
          setRangeStart("");
          setRangeEnd("");
          setQuery(linked.bookingReference);
        }
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
        toast.success("Appointment cancelled", { description: `${booking.serviceType} for ${booking.callerName}` });
      } else {
        await deleteBooking(booking.id);
        setBookings((current) => current.filter((item) => item.id !== booking.id));
        toast.success("Booking deleted", { description: booking.bookingReference });
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
      toast.success("Booking updated", { description: `${values.serviceType} · ${new Date(values.appointmentAt).toLocaleString()}` });
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
    <div className="mk-v2 grid min-w-0 grid-cols-1 gap-6 [&>*]:min-w-0">
      <PageHeader
        eyebrow="Operations"
        title="Bookings"
        description="Appointments captured by your agents, where they came from, and what needs follow-up."
      />

      <StatStrip
        items={[
          { label: "Upcoming", value: summary.upcoming, hint: summary.nextBooking ? `Next: ${formatAppointment(summary.nextBooking.appointmentDate)}` : "Next 7 days" },
          { label: "Today", value: summary.today, hint: "Scheduled for today" },
          { label: "Confirmed", value: summary.confirmed, hint: "All time" },
          { label: "Cancelled", value: summary.cancelled, hint: "All time" },
        ]}
      />

      <section className="grid min-w-0 grid-cols-1 gap-3 [&>*]:min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <label className="relative min-w-[14rem] flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-fg-muted" />
            <Input aria-label="Search bookings" className="h-9 pl-9 pr-8 text-[0.85rem]" onChange={(event) => setQuery(event.target.value)} placeholder="Search bookings…" value={query} />
            {query ? (
              <button aria-label="Clear search" onClick={() => setQuery("")} type="button" className="absolute right-2 top-1/2 grid size-6 -translate-y-1/2 cursor-pointer place-items-center rounded-md border-0 bg-transparent text-fg-muted hover:text-ink">
                <X className="size-3.5" />
              </button>
            ) : null}
          </label>
          <BookingDateRangePicker end={rangeEnd} onApply={(range) => { setRangeStart(range.start); setRangeEnd(range.end); }} start={rangeStart} />
          <div className="w-44 max-sm:w-full">
            <Select size="sm" icon={<Filter />} ariaLabel="Filter by agent" value={agentId} onValueChange={setAgentId} options={[{ value: "all", label: "All agents" }, ...agents.map((agent) => ({ value: agent.id, label: agent.name }))]} />
          </div>
          <Tabs value={view} onValueChange={(value) => setView(value as BookingView)} className="ml-auto">
            <TabsList aria-label="Booking view">
              <TabsTrigger value="list"><List /> List</TabsTrigger>
              <TabsTrigger value="calendar"><LayoutGrid /> Calendar</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Tabs value={filter} onValueChange={(value) => setFilter(value as BookingStatusFilter)} className="max-w-full overflow-x-auto [scrollbar-width:none]">
            <TabsList aria-label="Booking status">
              {FILTERS.map((item) => <TabsTrigger key={item.value} value={item.value}>{item.label}</TabsTrigger>)}
            </TabsList>
          </Tabs>
          {hasActiveFilters ? (
            <div className="flex flex-wrap items-center gap-1.5">
              {(rangeStart || rangeEnd) ? <FilterChip onClear={() => { setRangeStart(""); setRangeEnd(""); }}>{formatRange(rangeStart, rangeEnd)}</FilterChip> : null}
              {activeAgent ? <FilterChip onClear={() => setAgentId("all")}>Agent: {activeAgent.name}</FilterChip> : null}
              <Button variant="ghost" size="sm" onClick={clearFilters}>Clear all</Button>
            </div>
          ) : null}
        </div>
      </section>

      {error ? <Alert tone="error">{error}</Alert> : null}
      {loading ? (
        <Card className="grid gap-3 p-5">{[1, 2, 3, 4].map((row) => <Skeleton key={row} className="h-20" />)}</Card>
      ) : viewModels.length === 0 ? (
        <EmptyState icon={<CalendarDays />} title="No bookings yet" description="Bookings created by agents or owners appear here, even when an external calendar needs follow-up." />
      ) : visibleBookings.length === 0 ? (
        <EmptyState icon={<Search />} title="No matching bookings" description="Adjust the date, agent, search or status filters to see more appointments." action={<Button variant="secondary" size="sm" onClick={clearFilters}>Clear filters</Button>} />
      ) : view === "calendar" ? (
        <CalendarView bookings={visibleBookings} onEdit={(booking) => { setEditorError(""); setEditingBooking(booking); }} />
      ) : (
        <div className="grid gap-6">
          {groupedBookings.map(([label, items]) => (
            <section key={label}>
              <h2 className="m-0 mb-3 flex items-baseline gap-3 text-sm font-medium text-ink">
                {label}
                <span className="text-xs font-medium text-fg-muted">{items.length} {items.length === 1 ? "booking" : "bookings"}</span>
              </h2>
              <Card className="divide-y divide-line overflow-hidden">
                {items.map((booking) => (
                  <BookingRow
                    booking={booking}
                    cancelling={cancellingId === booking.id}
                    key={booking.id}
                    onCancel={() => requestAction("cancel", booking)}
                    onDelete={() => requestAction("delete", booking)}
                    onEdit={() => { setEditorError(""); setEditingBooking(booking); }}
                    saving={savingId === booking.id}
                  />
                ))}
              </Card>
            </section>
          ))}
        </div>
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
          busy={pendingAction.kind === "cancel" ? cancellingId === pendingAction.booking.id : savingId === pendingAction.booking.id}
          error={actionError}
          onClose={() => { if (!cancellingId && !savingId) setPendingAction(null); }}
          onConfirm={() => void confirmAction()}
        />
      )}
    </div>
  );
}

const AGENT_TONES = ["45 106 85", "106 88 173", "47 98 163", "168 106 20", "47 122 75", "184 67 47"];

function agentAccent(tone: number) {
  return AGENT_TONES[Math.abs(tone) % AGENT_TONES.length];
}

function FilterChip({ children, onClear }: { children: React.ReactNode; onClear: () => void }) {
  return (
    <button type="button" onClick={onClear} className="inline-flex h-7 cursor-pointer items-center gap-1.5 rounded-[6px] border border-line-strong bg-surface px-2.5 font-sans text-xs text-fg transition-colors hover:bg-surface-2">
      {children} <X className="size-3" />
    </button>
  );
}

function BookingRow({ booking, cancelling, saving, onCancel, onDelete, onEdit }: { booking: BookingViewModel; cancelling: boolean; saving: boolean; onCancel: () => void; onDelete: () => void; onEdit: () => void }) {
  const cancelled = booking.status === "cancelled";
  const synced = booking.calendarSyncStatus === "synced";
  const status = cancelled
    ? { label: "Cancelled", dot: "bg-fg-muted" }
    : synced
      ? { label: humanize(booking.status), dot: "bg-success" }
      : { label: "Calendar follow-up", dot: "bg-warning" };
  return (
    <article
      style={{ ["--agent" as string]: agentAccent(booking.agentTone) }}
      className={cn(
        "group grid grid-cols-[4.5rem_minmax(0,1fr)] items-center gap-x-4 gap-y-1 px-4 py-3 transition-colors hover:bg-surface-2/60 md:grid-cols-[4.5rem_minmax(0,1.4fr)_minmax(0,1fr)_10rem_auto] sm:px-5",
        cancelled && "opacity-55",
      )}
    >
      <div className="leading-tight">
        <p className="m-0 text-[0.9rem] font-medium tabular-nums text-ink">{formatTime(booking.appointmentDate)}</p>
        <p className="m-0 text-[0.75rem] text-fg-muted">{booking.durationMinutes} min</p>
      </div>
      <div className="min-w-0 leading-tight">
        <p className={cn("m-0 truncate text-[0.9rem] text-ink", cancelled && "line-through decoration-fg-muted")}>{booking.callerName}</p>
        <p className="m-0 mt-0.5 truncate text-[0.8rem] text-fg-soft">{booking.serviceType}</p>
      </div>
      <div className="hidden min-w-0 leading-tight md:block">
        <p className="m-0 flex items-center gap-1.5 truncate text-[0.82rem] text-fg">
          <span className="size-2 shrink-0 rounded-[2px] bg-[rgb(var(--agent))]" aria-hidden="true" />
          {booking.agentName}
        </p>
        <p className="m-0 mt-0.5 truncate text-[0.75rem] text-fg-muted">
          <a href={`tel:${booking.callerPhone}`} className="text-fg-muted no-underline tabular-nums hover:text-ink">{booking.callerPhone}</a>
          <span className="mx-1.5">·</span>{booking.bookingReference}
        </p>
      </div>
      <p className="m-0 hidden items-center gap-2 text-[0.82rem] text-fg md:flex">
        <span className={cn("size-1.5 shrink-0 rounded-full", status.dot)} aria-hidden="true" />
        {status.label}
      </p>
      <div className="flex items-center justify-end gap-0.5 max-md:-ml-3 max-md:col-start-2 max-md:justify-start">
        {booking.callId ? (
          <Link
            href={`/calls?call=${booking.callId}`}
            className="inline-flex h-7 items-center rounded-[6px] px-2 text-xs font-medium text-fg-soft no-underline transition-colors hover:bg-tint/[0.05] hover:text-ink"
          >
            Call
          </Link>
        ) : null}
        {!cancelled ? (
          <Button variant="ghost" size="xs" disabled={saving || cancelling} onClick={onEdit} className="text-fg-soft hover:bg-tint/[0.05]">
            Reschedule
          </Button>
        ) : null}
        <Tooltip content="Call">
          <Button asChild variant="ghost" size="xs" className="size-8 px-0 text-fg-muted hover:bg-tint/[0.05] max-sm:hidden">
            <a href={`tel:${booking.callerPhone}`} aria-label={`Call ${booking.callerName}`}><Phone /></a>
          </Button>
        </Tooltip>
        {!cancelled ? (
          <Tooltip content={cancelling ? "Cancelling…" : "Cancel booking"}>
            <Button variant="ghost" size="xs" aria-label={`Cancel ${booking.bookingReference}`} disabled={saving || cancelling} onClick={onCancel} className="size-8 px-0 text-fg-muted hover:bg-tint/[0.05]"><X /></Button>
          </Tooltip>
        ) : null}
        <Tooltip content="Delete permanently">
          <Button variant="ghost" size="xs" aria-label={`Delete ${booking.bookingReference}`} disabled={saving || cancelling} onClick={onDelete} className="size-8 px-0 text-fg-muted hover:bg-danger/[0.08] hover:text-danger"><Trash2 /></Button>
        </Tooltip>
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
  return (
    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
      {days.map(([date, items]) => (
        <Card key={date} className="flex min-h-48 flex-col overflow-hidden">
          <header className="flex items-baseline justify-between border-b border-line px-4 py-3">
            <span className="text-sm font-medium text-ink">{new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { weekday: "short" })}</span>
            <span className="font-mono text-[0.65rem] text-fg-muted">{new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</span>
          </header>
          <div className="grid gap-2 p-2">
            {items.map((booking) => (
              <button
                key={booking.id}
                type="button"
                onClick={() => onEdit(booking)}
                style={{ ["--agent" as string]: agentAccent(booking.agentTone) }}
                className="grid cursor-pointer gap-0.5 rounded-[var(--radius-control)] border border-[rgb(var(--agent)/0.25)] bg-[rgb(var(--agent)/0.07)] p-2.5 text-left font-sans transition-colors hover:border-[rgb(var(--agent)/0.5)]"
              >
                <time className="font-mono text-[0.65rem] text-[rgb(var(--agent))]">{formatTime(booking.appointmentDate)}</time>
                <span className="truncate text-sm font-medium text-ink">{booking.serviceType}</span>
                <span className="truncate text-xs text-fg-soft">{booking.callerName}</span>
              </button>
            ))}
          </div>
        </Card>
      ))}
    </section>
  );
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
