import type {
  AnalyticsAfterHours,
  AnalyticsAgentSummary,
  AnalyticsChannelBreakdown,
  AnalyticsConnectRateByDay,
  AnalyticsData,
  AnalyticsFunnel,
  AnalyticsIntegrationEvents,
  AnalyticsLanguageBreakdown,
  AnalyticsOutcomeByDay,
  AnalyticsSentimentByDay,
  AnalyticsSummary,
  AnalyticsTopIntent,
} from "@/types/api";
import { apiRequest } from "./client";

export type AnalyticsFilters = {
  from: string;
  to: string;
  agentId?: string;
};

function query(filters: AnalyticsFilters, extra?: Record<string, string>) {
  const params = new URLSearchParams({
    from: filters.from,
    to: filters.to,
    ...(filters.agentId ? { agentId: filters.agentId } : {}),
    ...extra,
  });
  return params.toString();
}

export async function loadAnalytics(filters: AnalyticsFilters): Promise<AnalyticsData> {
  const scoped = query(filters);
  const unscoped = query({ from: filters.from, to: filters.to });
  const [
    summary,
    outcomesByDay,
    connectRateByDay,
    funnel,
    languages,
    channels,
    topIntents,
    sentimentByDay,
    agents,
    afterHours,
    integrationEvents,
  ] = await Promise.all([
    apiRequest<AnalyticsSummary>(`/analytics/summary?${scoped}`),
    apiRequest<AnalyticsOutcomeByDay[]>(`/analytics/outcomes-by-day?${scoped}`),
    apiRequest<AnalyticsConnectRateByDay[]>(`/analytics/connect-rate-by-day?${scoped}`),
    apiRequest<AnalyticsFunnel>(`/analytics/funnel?${scoped}`),
    apiRequest<AnalyticsLanguageBreakdown[]>(`/analytics/by-language?${scoped}`),
    apiRequest<AnalyticsChannelBreakdown[]>(`/analytics/by-channel?${scoped}`),
    apiRequest<AnalyticsTopIntent[]>(`/analytics/top-intents?${query(filters, { limit: "10" })}`),
    apiRequest<AnalyticsSentimentByDay[]>(`/analytics/sentiment-by-day?${scoped}`),
    apiRequest<AnalyticsAgentSummary[]>(`/analytics/by-agent?${unscoped}`),
    apiRequest<AnalyticsAfterHours>(`/analytics/after-hours?${scoped}`),
    apiRequest<AnalyticsIntegrationEvents[]>(`/analytics/integration-events?${scoped}`),
  ]);

  return {
    summary,
    outcomesByDay,
    connectRateByDay,
    funnel,
    languages,
    channels,
    topIntents,
    sentimentByDay,
    agents,
    afterHours,
    integrationEvents,
  };
}
