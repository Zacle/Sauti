"use client";

import styles from "./NotificationMenu.module.css";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { formatDistanceToNow } from "date-fns";
import { Bell, CalendarCheck2, CalendarClock, Check, CheckCheck, LoaderCircle, X } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/lib/api/notifications";
import type { WorkspaceNotification } from "@/types/api";

export function NotificationMenu() {
  const { session } = useAuth();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<WorkspaceNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const rootRef = useRef<HTMLDivElement>(null);

  async function refresh(silent = false) {
    if (!session) return;
    if (!silent) setLoading(true);
    try {
      const result = await listNotifications();
      setNotifications(result.notifications);
      setUnreadCount(result.unreadCount);
      setError("");
    } catch (caught) {
      if (!silent) setError(caught instanceof Error ? caught.message : "Unable to load notifications.");
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => {
    if (!session) return;
    void refresh();
    const poll = window.setInterval(() => void refresh(true), 30_000);
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/dashboard/${session.tenant.id}?token=${encodeURIComponent(session.accessToken)}`);
    socket.onmessage = (message) => {
      try {
        const event = JSON.parse(message.data) as { type?: string };
        if (event.type === "booking.created") window.setTimeout(() => void refresh(true), 150);
      } catch {
        // Ignore unrelated or malformed dashboard events; polling remains the fallback.
      }
    };
    return () => {
      window.clearInterval(poll);
      socket.close();
    };
  // refresh intentionally uses the current authenticated session.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken, session?.tenant.id]);

  useEffect(() => {
    if (!open) return;
    function closeOnOutsideClick(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [open]);

  async function read(notification: WorkspaceNotification) {
    if (notification.readAt) return;
    setNotifications((current) => current.map((item) => item.id === notification.id
      ? { ...item, readAt: new Date().toISOString() }
      : item));
    setUnreadCount((current) => Math.max(0, current - 1));
    try {
      const updated = await markNotificationRead(notification.id);
      setNotifications((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch {
      void refresh(true);
    }
  }

  async function readAll() {
    if (!unreadCount) return;
    const readAt = new Date().toISOString();
    setNotifications((current) => current.map((item) => ({ ...item, readAt: item.readAt ?? readAt })));
    setUnreadCount(0);
    try {
      await markAllNotificationsRead();
    } catch {
      void refresh(true);
    }
  }

  return (
    <div className={styles.root} ref={rootRef}>
      <button
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : "Notifications"}
        className={`top-icon-button ${styles.trigger}`}
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <Bell size={18} />
        {unreadCount > 0 && <span className={styles.badge}>{unreadCount > 99 ? "99+" : unreadCount}</span>}
      </button>
      {open && (
        <section aria-label="Notifications" className={styles.panel} role="dialog">
          <header>
            <div><span>Inbox</span><h2>Notifications</h2></div>
            <div>
              {unreadCount > 0 && <button className={styles.readAll} onClick={() => void readAll()} type="button"><CheckCheck size={15} /> Mark all read</button>}
              <button aria-label="Close notifications" className={styles.close} onClick={() => setOpen(false)} type="button"><X size={17} /></button>
            </div>
          </header>
          <div className={styles.summary}>
            <span>{unreadCount ? `${unreadCount} unread` : "You're all caught up"}</span>
            <Link href="/bookings" onClick={() => setOpen(false)}>View bookings</Link>
          </div>
          <div className={styles.list}>
            {loading ? <div className={styles.state}><LoaderCircle className={styles.spin} size={22} /><span>Loading notifications…</span></div>
              : error ? <div className={styles.state}><span>{error}</span><button onClick={() => void refresh()} type="button">Try again</button></div>
                : notifications.length === 0 ? <div className={styles.empty}><span><Bell size={22} /></span><strong>No notifications yet</strong><p>New booking confirmations and follow-up requests will appear here.</p></div>
                  : notifications.map((notification) => <NotificationRow key={notification.id} notification={notification} onRead={read} onClose={() => setOpen(false)} />)}
          </div>
        </section>
      )}
    </div>
  );
}

function NotificationRow({ notification, onRead, onClose }: { notification: WorkspaceNotification; onRead: (notification: WorkspaceNotification) => Promise<void>; onClose: () => void }) {
  const unread = !notification.readAt;
  const actionRequired = notification.type === "booking.follow_up_required"
    || notification.type === "booking.calendar_sync_failed";
  const appointmentAt = typeof notification.payload.appointmentAt === "string" ? notification.payload.appointmentAt : "";
  const reference = typeof notification.payload.bookingReference === "string" ? notification.payload.bookingReference : "";
  return (
    <Link
      className={`${styles.item} ${unread ? styles.unread : ""}`}
      href={notification.href}
      onClick={() => { void onRead(notification); onClose(); }}
    >
      <span className={`${styles.icon} ${actionRequired ? styles.warning : ""}`}>{actionRequired ? <CalendarClock size={18} /> : <CalendarCheck2 size={18} />}</span>
      <span className={styles.copy}>
        <span><strong>{notification.title}</strong>{unread && <i aria-label="Unread" />}</span>
        <small>{notification.message}</small>
        {appointmentAt && <time dateTime={appointmentAt}>{formatBookingDate(appointmentAt)}</time>}
        <em>{reference}</em>
      </span>
      <span className={styles.age}>{formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })}</span>
      {!unread && <Check className={styles.readIcon} size={14} />}
    </Link>
  );
}

function formatBookingDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(undefined, {
    weekday: "short", month: "short", day: "numeric", hour: "numeric", minute: "2-digit",
  }).format(date);
}
