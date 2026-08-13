import { apiRequest } from "@/lib/api/client";
import type { PrivacyRetentionSettings } from "@/types/api";

export type WorkspaceWebhookSettings = {
  webhookUrl: string | null;
  secretConfigured: boolean;
};

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
