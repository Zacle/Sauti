import type { AdminAuditPage, AdminBillingReadiness, AdminCustomerDetail, AdminCustomerPage, AdminDemoRequest, AdminDemoRequestPage, AdminLaunchReadiness, AdminOverview, AdminPilotReadiness, AdminPlatformAnalytics, AdminQueueHealth, AdminReliabilityDrill, AdminReliabilityIncident, AdminSlo, AdminWorkspace, AdminWorkspacePage } from "@/types/api";
import { apiRequest } from "./client";

export function getAdminOverview() {
  return apiRequest<AdminOverview>("/admin/overview");
}

export function getAdminBillingReadiness() {
  return apiRequest<AdminBillingReadiness>("/admin/billing/readiness");
}

export function getAdminLaunchReadiness() {
  return apiRequest<AdminLaunchReadiness>("/admin/launch-readiness");
}

export function updateAdminLaunchReadiness(review: {
  securityReviewCompleted: boolean;
  privacyLegalReviewCompleted: boolean;
  googleVerificationCompleted: boolean;
  googleVerificationReference: string;
  liveAcceptanceCompleted: boolean;
  liveAcceptanceEvidence: string;
  generalAvailabilityApproved: boolean;
  confirmation: string;
  notes: string;
}) {
  return apiRequest<AdminLaunchReadiness>("/admin/launch-readiness", {
    method: "PATCH",
    body: JSON.stringify(review),
  });
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

export function resendDemoInvitation(requestId: string) {
  return apiRequest<{ invitationId: string; email: string; expiresAt: string }>(
    `/admin/demo-requests/${requestId}/invitation/resend`, { method: "POST" },
  );
}

export function revokeDemoInvitation(requestId: string) {
  return apiRequest<void>(`/admin/demo-requests/${requestId}/invitation/revoke`, { method: "POST" });
}

export function rejectDemoRequest(requestId: string, reason: string) {
  return apiRequest<AdminDemoRequest>(`/admin/demo-requests/${requestId}/reject`, {
    method: "POST", body: JSON.stringify({ reason }),
  });
}

export function updateDemoRequestOperations(requestId: string, assignedTo: string, internalNotes: string) {
  return apiRequest<AdminDemoRequest>(`/admin/demo-requests/${requestId}`, {
    method: "PATCH", body: JSON.stringify({ assignedTo, internalNotes }),
  });
}

export function getAdminAudit(page = 0, pageSize = 50) {
  return apiRequest<AdminAuditPage>(`/admin/audit?page=${page}&pageSize=${pageSize}`);
}

export function getAdminWorkspaces(query = "", page = 0, pageSize = 25) {
  const params = new URLSearchParams({ query, page: String(page), pageSize: String(pageSize) });
  return apiRequest<AdminWorkspacePage>(`/admin/workspaces?${params.toString()}`);
}

export function getAdminWorkspace(tenantId: string) {
  return apiRequest<AdminWorkspace>(`/admin/workspaces/${tenantId}`);
}

export function configureAdminPilotPolicy(tenantId: string, policy: {
  status: string; currency: string; monthlyBudget: number;
  phoneNumbersApproved: boolean; liveCallingApproved: boolean;
  smsApproved: boolean; whatsappApproved: boolean; notes: string;
}) {
  return apiRequest<AdminWorkspace>(`/admin/workspaces/${tenantId}/pilot-policy`, {
    method: "PATCH", body: JSON.stringify(policy),
  });
}

export function getAdminPilotReadiness(tenantId: string) {
  return apiRequest<AdminPilotReadiness>(`/admin/workspaces/${tenantId}/readiness`);
}

export function updateAdminPilotReadiness(tenantId: string, review: {
  supportContactName: string; supportContactEmail: string; supportContactPhone: string;
  launchNotes: string; launchApproved: boolean;
}) {
  return apiRequest<AdminPilotReadiness>(`/admin/workspaces/${tenantId}/readiness`, {
    method: "PATCH", body: JSON.stringify(review),
  });
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

export function getAdminReliabilityIncidents() {
  return apiRequest<AdminReliabilityIncident[]>("/admin/reliability/incidents");
}

export function getAdminQueueHealth() {
  return apiRequest<AdminQueueHealth[]>("/admin/reliability/queues");
}

export function getAdminSlos() {
  return apiRequest<AdminSlo[]>("/admin/reliability/slos");
}

export function getAdminReliabilityDrills() {
  return apiRequest<AdminReliabilityDrill[]>("/admin/reliability/drills");
}

export function startAdminReliabilityDrill() {
  return apiRequest<AdminReliabilityDrill>("/admin/reliability/drills", {
    method: "POST",
    body: JSON.stringify({ confirmation: "START RELIABILITY DRILL" }),
  });
}

export function acknowledgeAdminReliabilityDrill(drillId: string) {
  return apiRequest<AdminReliabilityDrill>(`/admin/reliability/drills/${drillId}/acknowledge`, {
    method: "POST",
  });
}

export function resolveAdminReliabilityDrill(drillId: string) {
  return apiRequest<AdminReliabilityDrill>(`/admin/reliability/drills/${drillId}/resolve`, {
    method: "POST",
  });
}
