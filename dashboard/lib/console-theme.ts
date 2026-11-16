"use client";

import { useEffect, useSyncExternalStore } from "react";

/**
 * Console appearance: light / dark / system mode plus an accent colour.
 * Stored per browser in localStorage and applied as classes on <html>
 * (`console-light` | `console-dark`, `data-accent`) so tokens in
 * styles/tailwind.css restyle everything, portaled overlays included.
 */

export type ThemeMode = "light" | "dark" | "system";
export type ThemeAccent = "green" | "blue" | "violet" | "amber" | "rose" | "graphite";
export type ThemePrefs = { mode: ThemeMode; accent: ThemeAccent };

export const THEME_ACCENTS: Array<{ value: ThemeAccent; label: string; light: string; dark: string }> = [
  { value: "green", label: "Forest", light: "#2d6a55", dark: "#6cbf9f" },
  { value: "blue", label: "Ocean", light: "#2f62a3", dark: "#7ea9e0" },
  { value: "violet", label: "Iris", light: "#5b4bb0", dark: "#a798e2" },
  { value: "amber", label: "Amber", light: "#9a5b0c", dark: "#e0a94f" },
  { value: "rose", label: "Rose", light: "#ad3858", dark: "#e8849d" },
  { value: "graphite", label: "Graphite", light: "#44423d", dark: "#cfccc4" },
];

const STORAGE_KEY = "sauti.console.theme";
const DEFAULT_PREFS: ThemePrefs = { mode: "light", accent: "green" };
const MODES: ThemeMode[] = ["light", "dark", "system"];

function sanitize(value: unknown): ThemePrefs {
  const input = (value ?? {}) as Partial<ThemePrefs>;
  return {
    mode: MODES.includes(input.mode as ThemeMode) ? (input.mode as ThemeMode) : DEFAULT_PREFS.mode,
    accent: THEME_ACCENTS.some((item) => item.value === input.accent) ? (input.accent as ThemeAccent) : DEFAULT_PREFS.accent,
  };
}

let current: ThemePrefs = DEFAULT_PREFS;
let loaded = false;
const listeners = new Set<() => void>();

function load() {
  if (loaded || typeof window === "undefined") return;
  loaded = true;
  try {
    current = sanitize(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "null"));
  } catch {
    current = DEFAULT_PREFS;
  }
}

function systemPrefersDark() {
  return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function resolveMode(mode: ThemeMode): "light" | "dark" {
  return mode === "system" ? (systemPrefersDark() ? "dark" : "light") : mode;
}

/** Put the theme classes on <html>. Transitions are paused for one frame so the swap is instant. */
export function applyTheme(prefs: ThemePrefs) {
  const root = document.documentElement;
  const dark = resolveMode(prefs.mode) === "dark";
  root.classList.add("console-switching");
  root.classList.toggle("console-dark", dark);
  root.classList.toggle("console-light", !dark);
  root.dataset.accent = prefs.accent;
  window.setTimeout(() => root.classList.remove("console-switching"), 60);
}

export function clearTheme() {
  const root = document.documentElement;
  root.classList.remove("console-dark", "console-light", "console-switching");
  delete root.dataset.accent;
}

let remoteSave: ((prefs: ThemePrefs) => void) | null = null;
let saveTimer: number | undefined;

export function setThemePrefs(next: Partial<ThemePrefs>, options: { persistRemote?: boolean } = {}) {
  load();
  current = sanitize({ ...current, ...next });
  if (options.persistRemote !== false && remoteSave) {
    const save = remoteSave;
    const snapshotPrefs = current;
    window.clearTimeout(saveTimer);
    // Debounced so clicking through accents sends one request.
    saveTimer = window.setTimeout(() => save(snapshotPrefs), 500);
  }
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch {
    // Private windows may block storage; the choice still applies for this visit.
  }
  applyTheme(current);
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key !== STORAGE_KEY) return;
    loaded = false;
    load();
    applyTheme(current);
    listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

function snapshot() {
  load();
  return current;
}

/** Read and change the console appearance. */
export function useConsoleTheme() {
  const prefs = useSyncExternalStore(subscribe, snapshot, () => DEFAULT_PREFS);
  return {
    ...prefs,
    resolved: typeof window === "undefined" ? "light" as const : resolveMode(prefs.mode),
    setMode: (mode: ThemeMode) => setThemePrefs({ mode }),
    setAccent: (accent: ThemeAccent) => setThemePrefs({ accent }),
  };
}

/** Apply the theme while the console is mounted and follow OS changes in system mode. */
export function useApplyConsoleTheme() {
  const prefs = useSyncExternalStore(subscribe, snapshot, () => DEFAULT_PREFS);
  useEffect(() => {
    applyTheme(prefs);
    if (prefs.mode !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => applyTheme(prefs);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [prefs]);
  useEffect(() => clearTheme, []);
}


/**
 * Keep the theme in sync with the signed-in account: load it once on mount
 * (the account wins over this browser) and save every later change.
 */
export function useSyncConsoleThemeWithAccount(
  enabled: boolean,
  api: { load: () => Promise<ThemePrefs>; save: (prefs: ThemePrefs) => Promise<unknown> },
) {
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    api.load()
      .then((remote) => {
        if (!active) return;
        const prefs = sanitize(remote);
        if (prefs.mode !== snapshot().mode || prefs.accent !== snapshot().accent) {
          setThemePrefs(prefs, { persistRemote: false });
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) remoteSave = (prefs) => void api.save(prefs).catch(() => undefined);
      });
    return () => {
      active = false;
      remoteSave = null;
    };
    // api is a stable module-level object.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);
}
