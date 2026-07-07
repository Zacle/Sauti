export type AnalyticsRangeKey = "7d" | "30d" | "90d";

export function analyticsRange(key: AnalyticsRangeKey, now = new Date()) {
  const days = key === "7d" ? 7 : key === "90d" ? 90 : 30;
  const to = now;
  const from = new Date(to);
  from.setDate(to.getDate() - days);
  return {
    key,
    label: key === "7d" ? "Last 7 days" : key === "90d" ? "Last 90 days" : "Last 30 days",
    days,
    from: from.toISOString(),
    to: to.toISOString(),
  };
}

export const analyticsRangeOptions: Array<{ key: AnalyticsRangeKey; label: string }> = [
  { key: "7d", label: "7 days" },
  { key: "30d", label: "30 days" },
  { key: "90d", label: "90 days" },
];
