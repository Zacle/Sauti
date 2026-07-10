"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ArrowUpRight,
  Calendar,
  CalendarCheck2,
  CalendarClock,
  CalendarDays,
  CheckCircle2,
  Clock3,
  LoaderCircle,
  Phone,
  Search,
  SlidersHorizontal,
  UserRound,
  XCircle,
} from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import { cancelBooking, listBookings } from "@/lib/api/bookings";
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

const FILTERS: Array<{ value: BookingStatusFilter; label: string }> = [
  { value: "all", label: "All" },
  { value: "upcoming", label: "Upcoming" },
  { value: "today", label: "Today" },
  { value: "confirmed", label: "Confirmed" },
  { value: "cancelled", label: "Cancelled" },
  { value: "past", label: "Past" },
];

export function BookingsPage() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<BookingStatusFilter>("upcoming");
  const [cancellingId, setCancellingId] = useState("");

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
  const visibleBookings = useMemo(() => filterBookings(viewModels, filter, query), [viewModels, filter, query]);

  async function onCancel(booking: BookingViewModel) {
    if (booking.status === "cancelled" || cancellingId) return;
    const confirmed = window.confirm(`Cancel ${booking.serviceType} for ${booking.callerName}?`);
    if (!confirmed) return;
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

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>Bookings</h1>
          <p>Track appointments captured by agents, where they came from, and what needs follow-up.</p>
        </div>
        <div className={styles.nextSlot}>
          <span className={styles.nextIcon}><CalendarClock size={22} /></span>
          <span>
            <small>Next appointment</small>
            <strong>{summary.nextBooking ? formatAppointment(summary.nextBooking.appointmentDate) : "No upcoming booking"}</strong>
          </span>
        </div>
      </header>

      <section className={styles.metrics} aria-label="Booking summary">
        <Metric detail="Next 7 days" icon={CalendarCheck2} label="Upcoming" value={summary.upcoming} tone="cyan" />
        <Metric detail="Scheduled for today" icon={Clock3} label="Today" value={summary.today} tone="blue" />
        <Metric detail="All confirmed bookings" icon={CheckCircle2} label="Confirmed" value={summary.confirmed} tone="green" />
        <Metric detail="All cancelled bookings" icon={XCircle} label="Cancelled" value={summary.cancelled} tone="orange" />
      </section>

      <section className={styles.toolbar}>
        <label className={styles.search}>
          <Search size={18} />
          <input
            aria-label="Search bookings"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search customer, phone, service, source..."
            value={query}
          />
        </label>
        <div className={styles.filters} aria-label="Booking filters">
          <SlidersHorizontal size={16} />
          {FILTERS.map((item) => (
            <button
              className={filter === item.value ? styles.activeFilter : ""}
              key={item.value}
              onClick={() => setFilter(item.value)}
              type="button"
            >
              {item.label}
            </button>
          ))}
        </div>
      </section>

      {error && <div className={styles.error}>{error}</div>}

      {loading ? (
        <div className={styles.loading}><LoaderCircle className="spin" size={22} /> Loading bookings...</div>
      ) : viewModels.length === 0 ? (
        <section className={styles.empty}>
          <span><CalendarDays size={25} /></span>
          <h2>No bookings yet</h2>
          <p>When an agent creates or syncs an appointment, it will appear here with customer, source, and status details.</p>
        </section>
      ) : visibleBookings.length === 0 ? (
        <section className={styles.empty}>
          <span><Search size={25} /></span>
          <h2>No matching bookings</h2>
          <p>Adjust the search or filter to see more appointments.</p>
        </section>
      ) : (
        <section className={styles.content}>
          {visibleBookings.map((booking) => (
            <div className={styles.bookingRow} key={booking.id}>
              <div className={styles.timelineItem} aria-hidden="true">
                <span>{booking.appointmentDate.toLocaleDateString(undefined, { month: "short", day: "numeric" })}</span>
                <strong>{formatTime(booking.appointmentDate)}</strong>
              </div>
              <BookingCard
                booking={booking}
                cancelling={cancellingId === booking.id}
                onCancel={() => void onCancel(booking)}
              />
            </div>
          ))}
        </section>
      )}
    </main>
  );
}

function Metric({ icon: Icon, label, value, detail, tone }: { icon: typeof CalendarCheck2; label: string; value: number; detail: string; tone: "cyan" | "green" | "blue" | "orange" }) {
  return (
    <article className={`${styles.metric} ${styles[tone]}`}>
      <span><Icon size={19} /></span>
      <div>
        <div className={styles.metricValue}><strong>{value}</strong><small>{label}</small></div>
        <p>{detail}</p>
      </div>
    </article>
  );
}

function BookingCard({ booking, cancelling, onCancel }: { booking: BookingViewModel; cancelling: boolean; onCancel: () => void }) {
  const cancelled = booking.status === "cancelled";
  return (
    <article className={`${styles.card} ${cancelled ? styles.cancelled : ""}`}>
      <div className={styles.dateBlock}>
        <span>{booking.appointmentDate.toLocaleDateString(undefined, { month: "short", year: "numeric" })}</span>
        <small>{booking.appointmentDate.toLocaleDateString(undefined, { weekday: "short" })}</small>
        <b>{booking.appointmentDate.getDate()}</b>
      </div>
      <div className={styles.timeBlock}>
        <strong>{formatTime(booking.appointmentDate)}</strong>
        <small>Appointment</small>
      </div>
      <div className={styles.cardMain}>
        <div className={styles.cardTop}>
          <div>
            <h2>{booking.serviceType}</h2>
            <p><UserRound size={14} /> {booking.callerName}</p>
          </div>
          <span className={`${styles.status} ${cancelled ? styles.statusCancelled : styles.statusConfirmed}`}>
            {cancelled ? <XCircle size={14} /> : <CheckCircle2 size={14} />}
            {humanize(booking.status)}
          </span>
        </div>
        <div className={styles.details}>
          <span><Phone size={14} /> {booking.callerPhone}</span>
          <span><CalendarDays size={14} /> {booking.agentName}</span>
          <span><ArrowUpRight size={14} /> {booking.sourceLabel}</span>
        </div>
        <div className={styles.footer}>
          <div className={styles.bookingMeta}>
            <span><Calendar size={16} /><small>Booked<strong>{formatAppointment(booking.bookedDate)}</strong></small></span>
            <span><ArrowUpRight size={16} /><small>Source<strong>{booking.sourceLabel}</strong></small></span>
            <span><CalendarDays size={16} /><small>Agent<strong>{booking.agentName}</strong></small></span>
          </div>
          <div>
            {booking.confirmationSent && <span className={styles.confirmation}>Confirmation sent</span>}
            {booking.externalEventId && <span className={styles.external}>Synced calendar</span>}
            {!cancelled && (
              <button disabled={cancelling} onClick={onCancel} type="button">
                {cancelling ? "Cancelling..." : "Cancel"}
              </button>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}

function humanize(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}
