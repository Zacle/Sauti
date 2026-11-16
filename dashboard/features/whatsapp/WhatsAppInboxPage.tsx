"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Bot, CheckCheck, LoaderCircle, MessageCircle, RefreshCw, Search, Send, UserRound } from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import {
  assignWhatsAppConversation,
  downloadWhatsAppMedia,
  listWhatsAppConversations,
  listWhatsAppMessages,
  markWhatsAppConversationRead,
  sendWhatsAppHumanMessage,
  type WhatsAppConversation,
  type WhatsAppMessage,
} from "@/lib/api/whatsapp";
import type { Agent } from "@/types/api";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/console";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import styles from "./WhatsAppInboxPage.module.css";

export function WhatsAppInboxPage() {
  const [conversations, setConversations] = useState<WhatsAppConversation[]>([]);
  const [messages, setMessages] = useState<WhatsAppMessage[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");

  const selected = conversations.find((item) => item.id === selectedId) ?? null;
  const agentNames = useMemo(() => new Map(agents.map((agent) => [agent.id, agent.name])), [agents]);
  const filtered = useMemo(() => {
    const value = query.trim().toLowerCase();
    if (!value) return conversations;
    return conversations.filter((item) => [item.customerName, item.customerNumber,
      item.lastMessagePreview, agentNames.get(item.agentId)].some((field) => field?.toLowerCase().includes(value)));
  }, [agentNames, conversations, query]);

  const refresh = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [conversationItems, agentItems] = await Promise.all([
        listWhatsAppConversations(), listAgents(),
      ]);
      setConversations(conversationItems);
      setAgents(agentItems);
      setSelectedId((current) => current || conversationItems[0]?.id || "");
      setError("");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load WhatsApp conversations.");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const interval = window.setInterval(() => void refresh(true), 5000);
    return () => window.clearInterval(interval);
  }, [refresh]);

  useEffect(() => {
    if (!selectedId) { setMessages([]); return; }
    void Promise.all([listWhatsAppMessages(selectedId), markWhatsAppConversationRead(selectedId)])
      .then(([items, updated]) => {
        setMessages(items);
        setConversations((current) => current.map((item) => item.id === updated.id ? updated : item));
      })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to open the conversation."));
    const interval = window.setInterval(() => {
      void listWhatsAppMessages(selectedId).then(setMessages).catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(interval);
  }, [selectedId]);

  async function changeMode(mode: "ai" | "human") {
    if (!selected) return;
    setWorking(true);
    try {
      const updated = await assignWhatsAppConversation(selected.id, mode);
      setConversations((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to change assignment.");
    } finally { setWorking(false); }
  }

  async function sendMessage() {
    if (!selected || !draft.trim() || selected.mode !== "human") return;
    setWorking(true);
    try {
      const sent = await sendWhatsAppHumanMessage(selected.id, draft.trim());
      setMessages((current) => [...current, sent]);
      setDraft("");
      await refresh(true);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to send the message.");
    } finally { setWorking(false); }
  }

  async function openMedia(message: WhatsAppMessage) {
    try {
      const blob = await downloadWhatsAppMedia(message.id);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to download the attachment.");
    }
  }

  return <section className={styles.page}>
    <PageHeader className={styles.header} title="WhatsApp inbox"
      description="Let agents handle routine messages and step in without losing context."
      actions={<Button variant="secondary" size="xs" onClick={() => void refresh()} disabled={loading}>
        <RefreshCw className={loading ? "spin" : ""} /> Refresh
      </Button>} />
    {error && <div className={styles.error}>{error}</div>}
    <div className={styles.workspace}>
      <aside className={styles.listPane}>
        <label className={styles.search}><Search size={16} /><input value={query}
          onChange={(event) => setQuery(event.target.value)} placeholder="Search customers" /></label>
        <div className={styles.list}>
          {loading ? <div className={styles.empty}><LoaderCircle className="spin" /> Loading conversations…</div>
            : filtered.length === 0 ? <div className={styles.empty}><MessageCircle />
              <strong>No WhatsApp conversations yet</strong><span>New customer messages will appear here.</span></div>
              : filtered.map((item) => <button type="button" key={item.id}
                className={item.id === selectedId ? styles.selected : ""} onClick={() => setSelectedId(item.id)}>
                <span className={styles.avatar}>{(item.customerName || item.customerNumber).slice(0, 1).toUpperCase()}</span>
                <div><strong>{item.customerName || item.customerNumber}</strong>
                  <span>{item.lastMessagePreview || "New WhatsApp conversation"}</span>
                  <small>{agentNames.get(item.agentId) || "Agent"} · {item.mode === "ai" ? "AI handling" : "Human handling"}</small></div>
                {item.unreadCount > 0 && <b>{item.unreadCount}</b>}
              </button>)}
        </div>
      </aside>
      <main className={styles.thread}>
        {!selected ? <div className={styles.threadEmpty}><MessageCircle size={34} /><h2>Select a conversation</h2>
          <p>Messages and assignment controls will appear here.</p></div> : <>
          <header className={styles.threadHeader}>
            <div className={styles.identity}><span className={styles.avatar}>{(selected.customerName || selected.customerNumber).slice(0, 1).toUpperCase()}</span>
              <div><strong>{selected.customerName || selected.customerNumber}</strong><span>{selected.customerNumber}</span></div></div>
            <Tabs value={selected.mode} onValueChange={(mode) => { if (mode !== selected.mode) void changeMode(mode as "ai" | "human"); }}>
              <TabsList aria-label="Who is replying">
                <TabsTrigger value="ai" disabled={working}><Bot /> AI replying</TabsTrigger>
                <TabsTrigger value="human" disabled={working}><UserRound /> Take over</TabsTrigger>
              </TabsList>
            </Tabs>
          </header>
          <div className={styles.messages}>
            {messages.map((message) => <article key={message.id}
              className={message.direction === "outbound" ? styles.outbound : styles.inbound}>
              {message.type !== "text" && <small>{message.type === "audio" ? "Voice note" : message.type}</small>}
              <p>{message.body || `${message.type} message`}</p>
              {message.mediaId && <Button variant="link" size="xs" className="mt-1.5 text-[0.8rem]"
                onClick={() => void openMedia(message)}>Open attachment</Button>}
              <footer><span>{new Date(message.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
                {message.direction === "outbound" && <span title={message.status}><CheckCheck size={13} /> {message.status}</span>}</footer>
              {message.failureReason && <em>{message.failureReason}</em>}
            </article>)}
          </div>
          <footer className={styles.composer}>
            {selected.mode === "ai" ? <div className={styles.aiNotice}><Bot size={17} />
              The AI agent is replying. Choose Take over to write as a human.</div> : <>
              <textarea value={draft} onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); void sendMessage(); } }}
                placeholder="Write a WhatsApp reply…" rows={2} />
              <Button size="icon" aria-label="Send reply" disabled={working || !draft.trim()} onClick={() => void sendMessage()}>
                {working ? <LoaderCircle className="spin" /> : <Send />}
              </Button></>}
          </footer>
        </>}
      </main>
    </div>
  </section>;
}
