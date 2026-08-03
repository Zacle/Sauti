import type { AdminDemoRequestPage, AdminOverview } from "@/types/api";
import { apiRequest } from "./client";

export function getAdminOverview() {
  return apiRequest<AdminOverview>("/admin/overview");
}

export function getAdminDemoRequests(page = 0, pageSize = 25) {
  return apiRequest<AdminDemoRequestPage>(`/admin/demo-requests?page=${page}&pageSize=${pageSize}`);
}

export function inviteDemoRequest(requestId: string) {
  return apiRequest<{ invitationId: string; email: string; expiresAt: string }>(
    `/admin/demo-requests/${requestId}/invitation`,
    { method: "POST" },
  );
}
