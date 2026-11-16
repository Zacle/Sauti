import type { Metadata } from "next";
import { BookingsPage as BookingsPageFeature } from "@/features/bookings/presentation/BookingsPage";


export const metadata: Metadata = { title: "Bookings | Sauti" };

export default function BookingsPage() {
  return <BookingsPageFeature />;
}
