import type { AdminCustomerDetail, AdminCustomerPage, AdminDemoRequestPage, AdminOverview, AdminPlatformAnalytics, AdminWorkspace, AdminWorkspacePage } from "@/types/api";
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

export function getAdminWorkspaces(query = "", page = 0, pageSize = 25) {
  const params = new URLSearchParams({ query, page: String(page), pageSize: String(pageSize) });
  return apiRequest<AdminWorkspacePage>(`/admin/workspaces?${params.toString()}`);
}

export function getAdminWorkspace(tenantId: string) {
  return apiRequest<AdminWorkspace>(`/admin/workspaces/${tenantId}`);
}

export function getAdminCustomers(query = "", page = 0, pageSize = 25) {
  const params = new URLSearchParams({ query, page: String(page), pageSize: String(pageSize) });
  return apiRequest<AdminCustomerPage>(`/admin/customers?${params.toString()}`);
}

export function getAdminCustomer(tenantId: string, phone: string) {
  return apiRequest<AdminCustomerDetail>(`/admin/customers/${tenantId}?${new URLSearchParams({ phone }).toString()}`);
}

export function getAdminPlatformAnalytics(days: 7 | 30 | 90 = 30) {
  return apiRequest<AdminPlatformAnalytics>(`/admin/analytics?days=${days}`);
}
