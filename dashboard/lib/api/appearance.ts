import { apiRequest } from "./client";
import type { ThemePrefs } from "@/lib/console-theme";

/** Per-user console appearance stored on the account (follows the user across browsers). */
export function loadConsoleAppearance() {
  return apiRequest<ThemePrefs>("/auth/appearance");
}

export function saveConsoleAppearance(prefs: ThemePrefs) {
  return apiRequest<ThemePrefs>("/auth/appearance", { method: "PUT", body: JSON.stringify(prefs) });
}
