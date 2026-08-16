import { apiRequest } from "@/lib/api/client";
import type { PrivacyRetentionSettings } from "@/types/api";

export type WorkspaceWebhookSettings = {
  webhookUrl: string | null;
  secretConfigured: boolean;
};

export type WorkspaceProfileSettings = {
  businessName: string;
  ownerEmail: string;
  countryCode: string;
  timezone: string;
  defaultBookingDurationMinutes: number;
};

export function loadWorkspaceProfile() {
  return apiRequest<WorkspaceProfileSettings>("/tenant/workspace-profile");
}

export function saveWorkspaceProfile(request: Pick<WorkspaceProfileSettings, "businessName" | "timezone" | "defaultBookingDurationMinutes">) {
  return apiRequest<WorkspaceProfileSettings>("/tenant/workspace-profile", {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function loadPrivacyRetention() {
  return apiRequest<PrivacyRetentionSettings>("/tenant/privacy-retention");
}

export function savePrivacyRetention(request: {
  conversationRetentionDays: number;
  recordingRetentionDays: number;
  recordingComplianceAcknowledged: boolean;
}) {
  return apiRequest<PrivacyRetentionSettings>("/tenant/privacy-retention", {
    method: "PUT",
    body: JSON.stringify(request),
  });
}

export function loadWorkspaceWebhook() {
  return apiRequest<WorkspaceWebhookSettings>("/tenant/webhook");
}

export function saveWorkspaceWebhook(request: { webhookUrl: string; webhookSecret: string }) {
  return apiRequest<WorkspaceWebhookSettings>("/tenant/webhook", {
    method: "PUT",
    body: JSON.stringify(request),
  });
}
