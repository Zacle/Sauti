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

export function updateBooking(bookingId: string, booking: {
  callerName: string;
  callerPhone: string;
  callerEmail: string | null;
  serviceType: string;
  appointmentAt: string;
  durationMinutes: number;
  capturedData: Record<string, unknown>;
}) {
  return apiRequest<Booking>(`/bookings/${bookingId}`, {
    method: "PUT",
    body: JSON.stringify(booking),
  });
}

export function deleteBooking(bookingId: string) {
  return apiRequest<void>(`/bookings/${bookingId}/permanent`, { method: "DELETE" });
}
