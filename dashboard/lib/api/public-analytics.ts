export type PublicAnalyticsEvent = "page_view" | "voice_demo_started" | "voice_demo_completed" | "voice_demo_failed" | "demo_request_submitted";

export function trackPublicAnalyticsEvent(eventType: PublicAnalyticsEvent, path?: string) {
  if (typeof window === "undefined") return;
  if (navigator.doNotTrack === "1") return;
  const query = new URLSearchParams(window.location.search);
  const body = JSON.stringify({
    eventType,
    path: path ?? window.location.pathname,
    referrer: document.referrer || null,
    utmSource: query.get("utm_source"),
    utmMedium: query.get("utm_medium"),
    utmCampaign: query.get("utm_campaign"),
  });
  if (navigator.sendBeacon) {
    const queued = navigator.sendBeacon("/api/v1/public/analytics/events", new Blob([body], { type: "application/json" }));
    if (queued) return;
  }
  void fetch("/api/v1/public/analytics/events", {
    method: "POST", headers: { "Content-Type": "application/json" }, body, keepalive: true,
  }).catch(() => undefined);
}
