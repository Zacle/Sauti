import type { Booking } from "@/types/api";
import { apiRequest } from "./client";

export function listBookings() {
  return apiRequest<Booking[]>("/bookings");
}
