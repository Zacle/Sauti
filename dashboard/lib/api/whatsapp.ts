import { apiBlobRequest, apiRequest } from "./client";

export type WhatsAppConversation = {
  id: string;
  agentId: string;
  customerNumber: string;
  customerName: string | null;
  mode: "ai" | "human";
  status: "open" | "closed";
  unreadCount: number;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  /** When the customer last wrote; WhatsApp allows free replies for 24 hours after it. */
  lastCustomerMessageAt: string | null;
  replyWindowEndsAt: string | null;
  /** The last customer message is waiting on the team. */
  needsAttention: boolean;
  attentionReason: WhatsAppAttentionReason | null;
  updatedAt: string;
  /** Why it was closed: by someone on the team, or on its own after a week without messages. */
  closedReason: "team" | "inactive" | null;
};

export type WhatsAppAttentionReason = "agent_failed" | "agent_paused" | "waiting_for_team";

export type WhatsAppMessage = {
  id: string;
  providerMessageId: string | null;
  direction: "inbound" | "outbound";
  type: string;
  body: string | null;
  mediaId: string | null;
  mediaMimeType: string | null;
  status: string;
  failureReason: string | null;
  createdAt: string;
  /** Who wrote it; null for replies sent before authors were kept. */
  author: "customer" | "ai" | "staff" | null;
  /** For staff replies: the team member's name, or their email (or API key name) when they haven't set one. */
  sentBy: string | null;
  /** The approved template a staff message was sent from. */
  templateName: string | null;
  updatedAt: string;
};

/** open: everything not closed; attention: open and waiting on the team; closed: closed by the team. */
export type WhatsAppInboxView = "open" | "attention" | "closed";

export type WhatsAppConversationPage = { items: WhatsAppConversation[]; nextCursor: string | null };

/** Conversations changed since a time, in any view; ask again with since set to next. */
export type WhatsAppConversationChanges = { items: WhatsAppConversation[]; next: string };

export type WhatsAppTemplate = {
  name: string;
  language: string;
  category: string;
  headerText: string;
  bodyText: string;
  fields: Array<{ key: string; component: "header" | "body"; name: string; placeholder: string }>;
};

const conversationPath = (conversationId: string, action: string) =>
  `/whatsapp/conversations/${encodeURIComponent(conversationId)}/${action}`;

export function listWhatsAppConversations(options: { view?: WhatsAppInboxView; query?: string; cursor?: string | null; limit?: number } = {}) {
  const params = new URLSearchParams({ view: options.view ?? "open", limit: String(options.limit ?? 30) });
  if (options.query?.trim()) params.set("q", options.query.trim());
  if (options.cursor) params.set("cursor", options.cursor);
  return apiRequest<WhatsAppConversationPage>(`/whatsapp/conversations?${params}`);
}

export function listWhatsAppConversationChanges(since: string | null) {
  const query = since ? `?since=${encodeURIComponent(since)}` : "";
  return apiRequest<WhatsAppConversationChanges>(`/whatsapp/conversations/changes${query}`);
}

/** The whole thread, or with since, only messages added or changed after it. */
export function listWhatsAppMessages(conversationId: string, since?: string | null) {
  const query = since ? `?since=${encodeURIComponent(since)}` : "";
  return apiRequest<WhatsAppMessage[]>(`${conversationPath(conversationId, "messages")}${query}`);
}

export function closeWhatsAppConversation(conversationId: string) {
  return apiRequest<WhatsAppConversation>(conversationPath(conversationId, "close"), { method: "POST" });
}

export function reopenWhatsAppConversation(conversationId: string) {
  return apiRequest<WhatsAppConversation>(conversationPath(conversationId, "reopen"), { method: "POST" });
}

export function listWhatsAppTemplates(conversationId: string) {
  return apiRequest<WhatsAppTemplate[]>(conversationPath(conversationId, "templates"));
}

export function sendWhatsAppTemplate(conversationId: string, template: Pick<WhatsAppTemplate, "name" | "language">,
                                     values: Record<string, string>) {
  return apiRequest<WhatsAppMessage>(conversationPath(conversationId, "templates"), {
    method: "POST", body: JSON.stringify({ name: template.name, language: template.language, values }),
  });
}

export function assignWhatsAppConversation(conversationId: string, mode: "ai" | "human") {
  return apiRequest<WhatsAppConversation>(
    `/whatsapp/conversations/${encodeURIComponent(conversationId)}/assignment`,
    { method: "PUT", body: JSON.stringify({ mode }) },
  );
}

export function markWhatsAppConversationRead(conversationId: string) {
  return apiRequest<WhatsAppConversation>(
    `/whatsapp/conversations/${encodeURIComponent(conversationId)}/read`,
    { method: "POST" },
  );
}

export function sendWhatsAppHumanMessage(conversationId: string, text: string) {
  return apiRequest<WhatsAppMessage>(
    `/whatsapp/conversations/${encodeURIComponent(conversationId)}/messages`,
    { method: "POST", body: JSON.stringify({ text }) },
  );
}

export function downloadWhatsAppMedia(messageId: string) {
  return apiBlobRequest(`/whatsapp/conversations/messages/${encodeURIComponent(messageId)}/media`);
}
