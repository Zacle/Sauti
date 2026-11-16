import clsx, { type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Joins class names; conflicting Tailwind utilities resolve to the last one. */
export function cn(...values: ClassValue[]) {
  return twMerge(clsx(values));
}

export function formatDuration(seconds: number) {
  if (!seconds) return "0m";
  const minutes = Math.floor(seconds / 60);
  const remaining = Math.round(seconds % 60);
  return minutes ? `${minutes}m ${remaining}s` : `${remaining}s`;
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
