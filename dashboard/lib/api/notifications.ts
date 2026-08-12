import { apiRequest } from "@/lib/api/client";
import type { WorkspaceNotification, WorkspaceNotificationList } from "@/types/api";

export function listNotifications() {
  return apiRequest<WorkspaceNotificationList>("/notifications");
}

export function markNotificationRead(id: string) {
  return apiRequest<WorkspaceNotification>(`/notifications/${id}/read`, { method: "PATCH" });
}

export function markAllNotificationsRead() {
  return apiRequest<void>("/notifications/read-all", { method: "POST" });
}

export function createDashboardSocketTicket() {
  return apiRequest<{ ticket: string; expiresAt: string }>("/dashboard/socket-ticket", { method: "POST" });
}
