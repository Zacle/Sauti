"use client";

import { CalendarX2, LoaderCircle, Trash2 } from "lucide-react";
import type { BookingViewModel } from "../domain/bookings";
import { formatAppointment } from "../domain/bookings";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { Dialog, DialogContent } from "@/components/ui/overlays";

export type BookingAction = {
  kind: "cancel" | "delete";
  booking: BookingViewModel;
};

/** Confirmation for cancelling (kept in history) or permanently deleting a booking. */
export function BookingActionDialog({
  action,
  busy,
  error,
  onClose,
  onConfirm,
}: {
  action: BookingAction;
  busy: boolean;
  error: string;
  onClose: () => void;
  onConfirm: () => void;
}) {
  const deleting = action.kind === "delete";
  const { booking } = action;

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose(); }}>
      <DialogContent
        title={deleting ? "Delete this booking?" : "Cancel this appointment?"}
        description={deleting
          ? `Booking ${booking.bookingReference} for ${booking.callerName} will be permanently removed. This cannot be undone.`
          : `${booking.serviceType} for ${booking.callerName} will be marked as cancelled and kept in your booking history.`}
        onEscapeKeyDown={(event) => { if (busy) event.preventDefault(); }}
        onPointerDownOutside={(event) => { if (busy) event.preventDefault(); }}
      >
        <div className="flex items-center justify-between gap-3 rounded-[var(--radius-control)] border border-line bg-tint/[0.03] px-4 py-3 text-sm">
          <span className="text-fg-soft">{formatAppointment(booking.appointmentDate)}</span>
          <span className="text-xs tabular-nums text-fg">{booking.bookingReference}</span>
        </div>
        {error ? <Alert tone="error" className="mt-4">{error}</Alert> : null}
        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <Button variant="secondary" size="sm" disabled={busy} onClick={onClose} autoFocus>
            Keep booking
          </Button>
          <Button size="sm" variant="danger" disabled={busy} onClick={onConfirm}>
            {busy ? <LoaderCircle className="animate-spin" /> : deleting ? <Trash2 /> : <CalendarX2 />}
            {busy ? (deleting ? "Deleting…" : "Cancelling…") : deleting ? "Delete permanently" : "Cancel appointment"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
