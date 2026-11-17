import { TIMEZONE_GROUPS } from "@/lib/timezones";

/** Languages offered as one-click choices across Agent Studio. */
export const LANGUAGE_OPTIONS = [
  ["en", "English"], ["fr", "French"], ["ar", "Arabic"], ["sw", "Swahili"],
  ["es", "Spanish"], ["de", "German"], ["pt", "Portuguese"], ["it", "Italian"],
] as const;

/** A wider list for pickers where any common language may be chosen. */
export const MORE_LANGUAGE_CODES = [
  "am", "zh", "nl", "fa", "ha", "hi", "id", "ig", "ja", "rw", "ko", "lg", "ms", "pl", "ru",
  "so", "ta", "th", "ti", "tr", "uk", "ur", "vi", "wo", "xh", "yo", "zu", "af", "bn", "el", "he", "ro", "sv",
] as const;

export function languageLabel(code: string) {
  const known = LANGUAGE_OPTIONS.find(([value]) => value === code)?.[1];
  if (known) return known;
  try {
    return new Intl.DisplayNames(["en"], { type: "language" }).of(code) ?? code.toUpperCase();
  } catch {
    return code.toUpperCase();
  }
}

export const TIMEZONE_OPTIONS = TIMEZONE_GROUPS.flatMap((group) => group.zones.map((zone) => ({
  value: zone.value,
  label: zone.label,
})));

const BOOKING_DURATION_OPTIONS = [15, 20, 30, 45, 60, 90, 120];

export function bookingDurationOptions(value: number) {
  return BOOKING_DURATION_OPTIONS.includes(value)
    ? BOOKING_DURATION_OPTIONS
    : [...BOOKING_DURATION_OPTIONS, value].sort((left, right) => left - right);
}

export function bookingDurationLabel(minutes: number) {
  if (minutes === 60) return "1 hour";
  if (minutes > 60 && minutes % 60 === 0) return `${minutes / 60} hours`;
  return `${minutes} minutes`;
}
