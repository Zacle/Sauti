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
};

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
};

export function listWhatsAppConversations() {
  return apiRequest<WhatsAppConversation[]>("/whatsapp/conversations");
}

export function listWhatsAppMessages(conversationId: string) {
  return apiRequest<WhatsAppMessage[]>(
    `/whatsapp/conversations/${encodeURIComponent(conversationId)}/messages`,
  );
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
