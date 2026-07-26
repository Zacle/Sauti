"use client";

import { useEffect, useRef } from "react";
import { CalendarX2, LoaderCircle, Trash2, X } from "lucide-react";
import type { BookingViewModel } from "../domain/bookings";
import { formatAppointment } from "../domain/bookings";
import styles from "./BookingActionDialog.module.css";

export type BookingAction = {
  kind: "cancel" | "delete";
  booking: BookingViewModel;
};

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
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const deleting = action.kind === "delete";
  const titleId = `booking-${action.kind}-title`;
  const descriptionId = `booking-${action.kind}-description`;

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    cancelButtonRef.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onClose();
    };
    window.addEventListener("keydown", onKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [busy, onClose]);

  return (
    <div
      className={styles.backdrop}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
      role="presentation"
    >
      <section
        aria-describedby={descriptionId}
        aria-labelledby={titleId}
        aria-modal="true"
        className={styles.dialog}
        role="dialog"
      >
        <button aria-label="Close dialog" className={styles.close} disabled={busy} onClick={onClose} type="button">
          <X size={18} />
        </button>
        <span className={`${styles.icon} ${deleting ? styles.dangerIcon : styles.warningIcon}`}>
          {deleting ? <Trash2 size={23} /> : <CalendarX2 size={23} />}
        </span>
        <span className={styles.eyebrow}>{deleting ? "Permanent action" : "Booking status"}</span>
        <h2 id={titleId}>{deleting ? "Delete this booking?" : "Cancel this appointment?"}</h2>
        <p id={descriptionId}>
          {deleting ? (
            <>
              Booking <strong>{action.booking.bookingReference}</strong> for{" "}
              <strong>{action.booking.callerName}</strong> will be permanently removed. This cannot be undone.
            </>
          ) : (
            <>
              <strong>{action.booking.serviceType}</strong> for <strong>{action.booking.callerName}</strong> will
              be marked as cancelled and kept in your booking history.
            </>
          )}
        </p>
        <div className={styles.summary}>
          <span>{formatAppointment(action.booking.appointmentDate)}</span>
          <strong>{action.booking.bookingReference}</strong>
        </div>
        {error && <div className={styles.error} role="alert">{error}</div>}
        <footer>
          <button disabled={busy} onClick={onClose} ref={cancelButtonRef} type="button">Keep booking</button>
          <button className={styles.danger} disabled={busy} onClick={onConfirm} type="button">
            {busy ? (
              <><LoaderCircle className="spin" size={16} /> {deleting ? "Deleting..." : "Cancelling..."}</>
            ) : (
              <>{deleting ? <Trash2 size={16} /> : <CalendarX2 size={16} />} {deleting ? "Delete permanently" : "Cancel appointment"}</>
            )}
          </button>
        </footer>
      </section>
    </div>
  );
}
