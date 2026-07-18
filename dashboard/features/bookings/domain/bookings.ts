import type { Agent, Booking } from "@/types/api";

export type BookingStatusFilter = "all" | "upcoming" | "today" | "past" | "confirmed" | "cancelled";

export type BookingViewModel = Booking & {
  agentName: string;
  appointmentDate: Date;
  bookedDate: Date;
  isUpcoming: boolean;
  isToday: boolean;
  sourceLabel: string;
};

export function toBookingViewModels(bookings: Booking[], agents: Agent[], now = new Date()): BookingViewModel[] {
  const agentNames = new Map(agents.map((agent) => [agent.id, agent.name]));
  return bookings
    .map((booking) => {
      const appointmentDate = new Date(booking.appointmentAt);
      const bookedDate = new Date(booking.bookedAt);
      return {
        ...booking,
        agentName: agentNames.get(booking.agentId) ?? "Unknown agent",
        appointmentDate,
        bookedDate,
        isUpcoming: appointmentDate.getTime() >= now.getTime() && booking.status !== "cancelled",
        isToday: isSameDay(appointmentDate, now),
        sourceLabel: booking.callId ? "Voice call" : booking.externalEventId ? "Calendar sync" : "Manual",
      };
    })
    .sort((left, right) => left.appointmentDate.getTime() - right.appointmentDate.getTime());
}

export function filterBookings(bookings: BookingViewModel[], filter: BookingStatusFilter, query: string) {
  const normalized = query.trim().toLowerCase();
  return bookings.filter((booking) => {
    const matchesFilter =
      filter === "all" ||
      (filter === "upcoming" && booking.isUpcoming) ||
      (filter === "today" && booking.isToday && booking.status !== "cancelled") ||
      (filter === "past" && booking.appointmentDate.getTime() < Date.now()) ||
      (filter === "confirmed" && booking.status === "confirmed") ||
      (filter === "cancelled" && booking.status === "cancelled");
    if (!matchesFilter) return false;
    if (!normalized) return true;
    return [
      booking.callerName,
      booking.callerPhone,
      booking.callerEmail ?? "",
      booking.bookingReference,
      booking.serviceType,
      booking.agentName,
      booking.status,
      booking.sourceLabel,
    ].some((value) => value.toLowerCase().includes(normalized));
  });
}

export function summarizeBookings(bookings: BookingViewModel[], now = new Date()) {
  const upcoming = bookings.filter((booking) => booking.isUpcoming).length;
  const today = bookings.filter((booking) => booking.isToday && booking.status !== "cancelled").length;
  const confirmed = bookings.filter((booking) => booking.status === "confirmed").length;
  const cancelled = bookings.filter((booking) => booking.status === "cancelled").length;
  const nextBooking = bookings.find((booking) => booking.appointmentDate.getTime() >= now.getTime() && booking.status !== "cancelled") ?? null;
  return { upcoming, today, confirmed, cancelled, nextBooking };
}

export function formatAppointment(value: Date) {
  return new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(value);
}

export function formatShortDate(value: Date) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(value);
}

export function formatTime(value: Date) {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(value);
}

function isSameDay(left: Date, right: Date) {
  return left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate();
}
