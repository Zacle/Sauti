export type TimezoneOption = {
  value: string;
  label: string;
};

export type TimezoneGroup = {
  label: string;
  zones: TimezoneOption[];
};

const REGION_LABELS: Record<string, string> = {
  Africa: "Africa",
  America: "Americas",
  Antarctica: "Antarctica",
  Arctic: "Arctic",
  Asia: "Asia",
  Atlantic: "Atlantic",
  Australia: "Australia",
  Europe: "Europe",
  Indian: "Indian Ocean",
  Pacific: "Pacific",
};

function utcOffset(timeZone: string, date = new Date()): string {
  const offset = new Intl.DateTimeFormat("en", {
    timeZone,
    timeZoneName: "longOffset",
  })
    .formatToParts(date)
    .find((part) => part.type === "timeZoneName")
    ?.value;

  if (!offset || offset === "GMT") return "UTC+00:00";
  return offset.replace("GMT", "UTC");
}

function locationName(timeZone: string): string {
  return timeZone
    .split("/")
    .slice(1)
    .join(" / ")
    .replaceAll("_", " ");
}

export function formatTimezone(timeZone: string): string {
  return `${utcOffset(timeZone)} — ${locationName(timeZone)}`;
}

export const TIMEZONE_GROUPS: TimezoneGroup[] = Object.entries(
  Intl.supportedValuesOf("timeZone").reduce<Record<string, TimezoneOption[]>>((groups, value) => {
    const region = value.split("/")[0];
    const group = REGION_LABELS[region] ?? region;
    (groups[group] ??= []).push({
      value,
      label: formatTimezone(value),
    });
    return groups;
  }, {}),
)
  .map(([label, zones]) => ({
    label,
    zones: zones.sort((left, right) => left.label.localeCompare(right.label, "en")),
  }))
  .sort((left, right) => left.label.localeCompare(right.label, "en"));
