import type { Agent, AgentDraft, AgentReadiness, AgentStats, AgentTemplate, AgentVariable, AvailablePhoneNumber, CreateAgentVariable, GeneratedAgentDraft, KnowledgeDocument } from "@/types/api";
import { apiRequest } from "./client";

export function createAgent(draft: AgentDraft) {
  return apiRequest<Agent>("/agents", { method: "POST", body: JSON.stringify(draft) });
}

export function listAgents() {
  return apiRequest<Agent[]>("/agents");
}

export function getAgent(agentId: string) {
  return apiRequest<Agent>(`/agents/${agentId}`);
}

export function deleteAgent(agentId: string) {
  return apiRequest<void>(`/agents/${agentId}`, { method: "DELETE" });
}

export function activateAgent(agentId: string) {
  return apiRequest<Agent>(`/agents/${agentId}/activate`, { method: "POST" });
}

export function provisionAgentNumber(agentId: string, phoneNumber?: string, replaceExisting = false) {
  return apiRequest<Agent>(`/agents/${agentId}/provision-number`, {
    method: "POST",
    ...((phoneNumber || replaceExisting) ? { body: JSON.stringify({ phoneNumber, replaceExisting }) } : {}),
  });
}

export function refreshAgentPhoneNumber(agentId: string) {
  return apiRequest<Agent>(`/agents/${agentId}/phone-number/refresh`, { method: "POST" });
}

export function listAvailableAgentNumbers(agentId: string, countryCode: string, limit = 10) {
  const query = new URLSearchParams({ countryCode, limit: String(limit) });
  return apiRequest<AvailablePhoneNumber[]>(`/agents/${agentId}/available-numbers?${query}`);
}

export function listAgentTemplates() {
  return apiRequest<AgentTemplate[]>("/agent-templates");
}

export function updateAgent(agentId: string, draft: AgentDraft) {
  return apiRequest<Agent>(`/agents/${agentId}`, { method: "PUT", body: JSON.stringify(draft) });
}

export function updateAgentTimezone(agentId: string, timezone: string) {
  return apiRequest<Agent>(`/agents/${agentId}/timezone`, {
    method: "PATCH",
    body: JSON.stringify({ timezone }),
  });
}

export function createAgentFromTemplate(
  templateId: string,
  payload: { name: string; timezone: string; humanTransferNumber: string | null },
) {
  return apiRequest<Agent>(`/agents/from-template/${templateId}`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function generateAgentFromBrief(brief: string) {
  return apiRequest<GeneratedAgentDraft>("/agents/generate-from-brief", {
    method: "POST",
    body: JSON.stringify({ brief }),
  });
}

export function listAgentStats() {
  return apiRequest<AgentStats[]>("/agents/stats");
}

export function listAgentReadiness() {
  return apiRequest<AgentReadiness[]>("/agents/readiness");
}

export function getAgentReadiness(agentId: string) {
  return apiRequest<AgentReadiness>(`/agents/${agentId}/readiness`);
}

export function listAgentVariables(agentId: string) {
  return apiRequest<AgentVariable[]>(`/agents/${agentId}/variables`);
}

export function updateAgentVariables(agentId: string, values: Record<string, string>) {
  return apiRequest<AgentVariable[]>(`/agents/${agentId}/variables`, {
    method: "PUT",
    body: JSON.stringify({
      variables: Object.entries(values).map(([key, value]) => ({ key, value })),
    }),
  });
}

export function updateAgentVariable(agentId: string, key: string, value: string) {
  return apiRequest<AgentVariable>(`/agents/${agentId}/variables/${encodeURIComponent(key)}`, {
    method: "PATCH",
    body: JSON.stringify({ value }),
  });
}

export function createAgentVariable(agentId: string, variable: CreateAgentVariable) {
  return apiRequest<AgentVariable>(`/agents/${agentId}/variables`, {
    method: "POST",
    body: JSON.stringify(variable),
  });
}

export function listKnowledgeDocuments(agentId: string) {
  return apiRequest<KnowledgeDocument[]>(`/agents/${agentId}/knowledge-documents`);
}

export function uploadKnowledgeDocument(agentId: string, file: File) {
  const body = new FormData();
  body.append("file", file);
  return apiRequest<KnowledgeDocument>(`/agents/${agentId}/knowledge-documents`, {
    method: "POST",
    body,
  });
}

export function deleteKnowledgeDocument(agentId: string, documentId: string) {
  return apiRequest<void>(`/agents/${agentId}/knowledge-documents/${documentId}`, {
    method: "DELETE",
  });
}
