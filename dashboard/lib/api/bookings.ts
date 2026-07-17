import type { Booking } from "@/types/api";
import { apiRequest } from "./client";

export function listBookings() {
  return apiRequest<Booking[]>("/bookings");
}

export function cancelBooking(bookingId: string) {
  return apiRequest<Booking>(`/bookings/${bookingId}`, { method: "DELETE" });
}

export function rescheduleBooking(bookingId: string, appointmentAt: string, durationMinutes?: number) {
  return apiRequest<Booking>(`/bookings/${bookingId}`, {
    method: "PATCH",
    body: JSON.stringify({ appointmentAt, durationMinutes }),
  });
}
