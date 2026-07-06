import type { DashboardData } from "@/types/api";
import { apiRequest } from "./client";

export async function loadDashboard(): Promise<DashboardData> {
  const [onboarding, agents, readiness, calls, bookings, analytics, daily, usage] = await Promise.all([
    apiRequest<DashboardData["onboarding"]>("/tenant/onboarding-status"),
    apiRequest<DashboardData["agents"]>("/agents"),
    apiRequest<DashboardData["readiness"]>("/agents/readiness"),
    apiRequest<DashboardData["calls"]>("/calls"),
    apiRequest<DashboardData["bookings"]>("/bookings"),
    apiRequest<DashboardData["analytics"]>("/analytics/summary"),
    apiRequest<DashboardData["daily"]>("/analytics/daily?days=14"),
    apiRequest<DashboardData["usage"]>("/billing/usage"),
  ]);
  return { onboarding, agents, readiness, calls, bookings, analytics, daily, usage };
}
