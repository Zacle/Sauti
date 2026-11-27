"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { createDashboardSocketTicket } from "@/lib/api/notifications";
import { isPreviewMode } from "@/lib/preview/mode";

/**
 * Listens on the console's live connection for one kind of event and calls `onSignal` with its payload.
 * Reconnects with a growing pause after a drop. Returns whether the connection is up, so callers can
 * poll less often while it is.
 */
export function useDashboardSignal(type: string, onSignal: (payload: Record<string, unknown>) => void) {
  const { session } = useAuth();
  const [connected, setConnected] = useState(false);
  const handler = useRef(onSignal);
  useEffect(() => { handler.current = onSignal; }, [onSignal]);

  const tenantId = session?.tenant?.id ?? "";
  const accessToken = session?.accessToken ?? "";

  useEffect(() => {
    if (!tenantId || !accessToken || isPreviewMode()) return;
    let socket: WebSocket | null = null;
    let stopped = false;
    let retry: number | undefined;
    let pause = 2_000;

    const connect = async () => {
      try {
        const { ticket } = await createDashboardSocketTicket();
        if (stopped) return;
        const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        socket = new WebSocket(`${protocol}//${window.location.host}/ws/dashboard/${tenantId}?ticket=${encodeURIComponent(ticket)}`);
        socket.onopen = () => { pause = 2_000; setConnected(true); };
        socket.onmessage = (message) => {
          try {
            const event = JSON.parse(message.data) as { type?: string; payload?: Record<string, unknown> };
            if (event.type === type) handler.current(event.payload ?? {});
          } catch { /* not ours, or malformed */ }
        };
        socket.onclose = () => { setConnected(false); reconnect(); };
      } catch {
        reconnect();
      }
    };
    const reconnect = () => {
      if (stopped) return;
      retry = window.setTimeout(() => void connect(), pause);
      pause = Math.min(pause * 2, 30_000);
    };

    void connect();
    return () => {
      stopped = true;
      window.clearTimeout(retry);
      if (socket) { socket.onclose = null; socket.close(); }
      setConnected(false);
    };
  }, [accessToken, tenantId, type]);

  return connected;
}
