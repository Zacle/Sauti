import type { Metadata } from "next";
import { WhatsAppInboxPage } from "@/features/whatsapp/WhatsAppInboxPage";


export const metadata: Metadata = { title: "Inbox | Sauti" };

export default function InboxPage() {
  return <WhatsAppInboxPage />;
}
