"use client";

import { useEffect, useState } from "react";
import { Toaster as Sonner } from "sonner";

/** Light inside the light console theme, dark everywhere else (marketing, dark console). */
function useToastTheme() {
  const [theme, setTheme] = useState<"light" | "dark">("dark");
  useEffect(() => {
    const root = document.documentElement;
    const sync = () => setTheme(root.classList.contains("console-light") ? "light" : "dark");
    sync();
    const observer = new MutationObserver(sync);
    observer.observe(root, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);
  return theme;
}

/**
 * App-wide toaster (Sonner), styled from the active theme tokens.
 * Mounted once in the root layout; call `toast()` from anywhere.
 */
export function Toaster() {
  const theme = useToastTheme();
  return (
    <Sonner
      theme={theme}
      position="bottom-right"
      gap={10}
      offset={20}
      toastOptions={{
        style: {
          background: "var(--color-popover)",
          border: "1px solid var(--color-line-strong)",
          color: "var(--color-fg)",
          borderRadius: "var(--radius-card)",
          fontFamily: "var(--font-geist-sans), ui-sans-serif, system-ui, sans-serif",
          boxShadow: "var(--shadow-pop)",
        },
        classNames: {
          description: "!text-fg-soft",
          actionButton: "!bg-primary !text-primary-fg !rounded-[var(--radius-button)]",
          success: "[&_[data-icon]]:!text-signal",
          error: "[&_[data-icon]]:!text-danger",
        },
      }}
    />
  );
}

export { toast } from "sonner";
