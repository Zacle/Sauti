import { apiRequest } from "./client";

export type GoogleCalendarStatus = {
  configured: boolean;
  connected: boolean;
  agentId: string;
  credentialId: string | null;
  calendarId: string | null;
};

export function getGoogleCalendarStatus(agentId: string) {
  return apiRequest<GoogleCalendarStatus>(
    `/integrations/google-calendar/status?agentId=${encodeURIComponent(agentId)}`,
  );
}

export function authorizeGoogleCalendar(agentId: string) {
  return apiRequest<{ authorizationUrl: string }>(
    `/integrations/google-calendar/authorize?agentId=${encodeURIComponent(agentId)}`,
  );
}

export function selectGoogleCalendar(agentId: string, calendarId: string) {
  return apiRequest<GoogleCalendarStatus>(
    `/integrations/google-calendar/selection?agentId=${encodeURIComponent(agentId)}`,
    { method: "PUT", body: JSON.stringify({ calendarId }) },
  );
}

export function testGoogleCalendar(agentId: string) {
  return apiRequest<{ connected: boolean; tested: boolean }>(
    `/integrations/google-calendar/test?agentId=${encodeURIComponent(agentId)}`,
    { method: "POST" },
  );
}

export type IntegrationCatalogEntry = {
  provider: string;
  name: string;
  category: string;
  description: string;
  duringCall: boolean;
  postCall: boolean;
  requiresConnection: boolean;
  configurationFields: string[];
  credentialFields: string[];
  authorizationConfigured: boolean;
};

export type IntegrationConnection = {
  id: string;
  provider: string;
  displayName: string;
  status: string;
  credentialConfigured: boolean;
  configuration: Record<string, unknown>;
  externalAccountId: string | null;
  lastTestedAt: string | null;
  lastError: string | null;
  createdAt: string;
};

export type IntegrationDelivery = {
  id: string;
  callId: string;
  provider: string;
  status: string;
  attempts: number;
  responseCode: number | null;
  lastError: string | null;
  deliveredAt: string | null;
  createdAt: string;
};

export type AgentIntegration = {
  provider: string;
  name: string;
  enabled: boolean;
  connectionId: string | null;
  connectionStatus: string;
  configuration: Record<string, unknown>;
  lastDelivery: IntegrationDelivery | null;
};

export function getIntegrationCatalog() {
  return apiRequest<IntegrationCatalogEntry[]>("/integrations/catalog");
}

export function getIntegrationConnections() {
  return apiRequest<IntegrationConnection[]>("/integrations/connections");
}

export function createIntegrationConnection(body: {
  provider: string;
  displayName?: string;
  configuration: Record<string, unknown>;
  credentials: Record<string, unknown>;
}) {
  return apiRequest<IntegrationConnection>("/integrations/connections", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateIntegrationConnection(id: string, body: {
  displayName?: string;
  configuration: Record<string, unknown>;
  credentials?: Record<string, unknown>;
}) {
  return apiRequest<IntegrationConnection>(`/integrations/connections/${id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export function testIntegrationConnection(id: string, agentId?: string) {
  const query = agentId ? `?agentId=${encodeURIComponent(agentId)}` : "";
  return apiRequest<IntegrationConnection>(`/integrations/connections/${id}/test${query}`, { method: "POST" });
}

export function deleteIntegrationConnection(id: string) {
  return apiRequest<void>(`/integrations/connections/${id}`, { method: "DELETE" });
}

export function getAgentIntegrations(agentId: string) {
  return apiRequest<AgentIntegration[]>(`/agents/${agentId}/integrations`);
}

export function putAgentIntegration(agentId: string, body: {
  provider: string;
  enabled: boolean;
  connectionId: string | null;
  configuration?: Record<string, unknown>;
}) {
  return apiRequest<AgentIntegration>(`/agents/${agentId}/integrations`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function authorizeProvider(provider: string, agentId: string) {
  return apiRequest<{ authorizationUrl: string }>(
    `/integrations/${encodeURIComponent(provider)}/authorize?agentId=${encodeURIComponent(agentId)}`,
  );
}

export type WhatsAppSignupConfiguration = {
  configured: boolean;
  appId: string;
  configurationId: string;
  graphVersion: string;
};

export type WhatsAppTemplate = {
  id: string;
  name: string;
  language: string;
  category: string;
};

export type WhatsAppSignupResult = {
  connectionId: string;
  wabaId: string;
  phoneNumberId: string;
  displayPhoneNumber: string;
  verifiedName: string;
  templates: WhatsAppTemplate[];
};

export function getWhatsAppSignupConfiguration() {
  return apiRequest<WhatsAppSignupConfiguration>("/integrations/whatsapp/embedded-signup/config");
}

export function completeWhatsAppSignup(body: {
  agentId: string;
  code: string;
  wabaId: string;
  phoneNumberId: string;
}) {
  return apiRequest<WhatsAppSignupResult>("/integrations/whatsapp/embedded-signup/complete", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function getWhatsAppTemplates(connectionId: string) {
  return apiRequest<WhatsAppTemplate[]>(
    `/integrations/connections/${encodeURIComponent(connectionId)}/whatsapp/templates`,
  );
}
