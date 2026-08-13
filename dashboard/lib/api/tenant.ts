import { apiRequest } from "@/lib/api/client";
import type { PrivacyRetentionSettings } from "@/types/api";

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
