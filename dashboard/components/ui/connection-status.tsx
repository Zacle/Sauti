"use client";

import { useEffect } from "react";
import { toast } from "sonner";

const TOAST_ID = "sauti-connection";

/**
 * Tells people the moment their connection drops (and when it returns), so a
 * failed save never comes as a surprise. Renders nothing itself.
 */
export function ConnectionStatus() {
  useEffect(() => {
    const goOffline = () =>
      toast.error("You’re offline", {
        id: TOAST_ID,
        description: "Changes won’t be saved until your connection is back.",
        duration: Infinity,
      });
    const goOnline = () =>
      toast.success("Back online", { id: TOAST_ID, description: "You can carry on where you left off.", duration: 3000 });

    if (!navigator.onLine) goOffline();
    window.addEventListener("offline", goOffline);
    window.addEventListener("online", goOnline);
    return () => {
      window.removeEventListener("offline", goOffline);
      window.removeEventListener("online", goOnline);
    };
  }, []);
  return null;
}
