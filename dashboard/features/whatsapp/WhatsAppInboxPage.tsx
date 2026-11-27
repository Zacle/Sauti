"use client";

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { Archive, ArchiveRestore, Bot, CheckCheck, Clock, FileText, LoaderCircle, MessageCircle, RefreshCw, Search, Send, TriangleAlert, UserRound } from "lucide-react";
import { listAgents } from "@/lib/api/agents";
import {
  assignWhatsAppConversation,
  closeWhatsAppConversation,
  downloadWhatsAppMedia,
  listWhatsAppConversationChanges,
  listWhatsAppConversations,
  listWhatsAppMessagePage,
  listWhatsAppMessages,
  markWhatsAppConversationRead,
  reopenWhatsAppConversation,
  sendWhatsAppHumanMessage,
  type WhatsAppAttentionReason,
  type WhatsAppConversation,
  type WhatsAppInboxView,
  type WhatsAppMessage,
} from "@/lib/api/whatsapp";
import type { Agent } from "@/types/api";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/console";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/primitives";
import { WhatsAppTemplateDialog } from "./WhatsAppTemplateDialog";
import { useDashboardSignal } from "./useDashboardSignal";
import styles from "./WhatsAppInboxPage.module.css";

const PAGE_SIZE = 30;
/** Polls reach back this far, so a change still being saved when the last poll ran is not missed. */
const OVERLAP_MS = 30_000;

const views: Array<{ value: WhatsAppInboxView; label: string }> = [
  { value: "open", label: "Open" },
  { value: "attention", label: "Needs a reply" },
  { value: "closed", label: "Closed" },
];

export function WhatsAppInboxPage() {
  const [view, setView] = useState<WhatsAppInboxView>("open");
  const [query, setQuery] = useState("");
  const search = useDebounced(query, 300);
  const [conversations, setConversations] = useState<WhatsAppConversation[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [selected, setSelected] = useState<WhatsAppConversation | null>(null);
  const [messages, setMessages] = useState<WhatsAppMessage[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [draft, setDraft] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [working, setWorking] = useState(false);
  const [templateOpen, setTemplateOpen] = useState(false);
  const [error, setError] = useState("");
  const changesSince = useRef<string | null>(null);
  const latestMessage = useRef<string | null>(null);
  const signalTimer = useRef<number | undefined>(undefined);
  const thread = useRef<HTMLDivElement>(null);
  /** How the thread should scroll after the next render: to the newest message, or keep the reader's place. */
  const nextScroll = useRef<{ mode: "bottom" } | { mode: "keep"; fromBottom: number } | null>(null);
  const [earlierCursor, setEarlierCursor] = useState<string | null>(null);
  const [loadingEarlier, setLoadingEarlier] = useState(false);

  const selectedId = selected?.id ?? "";
  const now = useNow(30_000);
  const replyWindowOpen = Boolean(selected?.replyWindowEndsAt && new Date(selected.replyWindowEndsAt).getTime() > now);
  const agentNames = useMemo(() => new Map(agents.map((agent) => [agent.id, agent.name])), [agents]);

  /** Puts a changed conversation where it belongs: in, out of, or moved within the current list. */
  const apply = useCallback((items: WhatsAppConversation[]) => {
    if (items.length === 0) return;
    setConversations((current) => {
      const byId = new Map(current.map((item) => [item.id, item]));
      for (const item of items) {
        if (belongsIn(item, view, search)) byId.set(item.id, item);
        else byId.delete(item.id);
      }
      return [...byId.values()].sort(newestFirst);
    });
    setSelected((current) => current && (items.find((item) => item.id === current.id) ?? current));
  }, [search, view]);

  const loadFirstPage = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const page = await listWhatsAppConversations({ view, query: search, limit: PAGE_SIZE });
      setConversations(page.items);
      setNextCursor(page.nextCursor);
      setSelected((current) => current ?? page.items[0] ?? null);
      setError("");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load WhatsApp conversations.");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, [search, view]);

  useEffect(() => { void loadFirstPage(); }, [loadFirstPage]);
  useEffect(() => { void listAgents().then(setAgents).catch(() => undefined); }, []);

  const pollChanges = useCallback(async () => {
    try {
      const changes = await listWhatsAppConversationChanges(changesSince.current);
      changesSince.current = changes.next;
      apply(changes.items);
    } catch { /* the next poll tries again */ }
  }, [apply]);

  /** Adds new or changed messages of the open thread to what is shown. */
  const mergeMessages = useCallback((items: WhatsAppMessage[], replace = false) => {
    for (const item of items) if (!latestMessage.current || item.updatedAt > latestMessage.current) latestMessage.current = item.updatedAt;
    const box = thread.current;
    // Follow new messages only when the reader is already at the bottom; never pull them away from older ones.
    if (replace || !box || box.scrollHeight - box.scrollTop - box.clientHeight < 80) nextScroll.current = { mode: "bottom" };
    setMessages((current) => {
      const byId = new Map((replace ? [] : current).map((item) => [item.id, item]));
      for (const item of items) byId.set(item.id, item);
      return [...byId.values()].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    });
  }, []);

  const pollMessages = useCallback(async (conversationId: string) => {
    const latest = latestMessage.current;
    const since = latest ? new Date(new Date(latest).getTime() - OVERLAP_MS).toISOString() : null;
    try { mergeMessages(await listWhatsAppMessages(conversationId, since)); } catch { /* the next poll tries again */ }
  }, [mergeMessages]);

  // The server says the moment something changes; a short pause gathers a burst of saves into one fetch.
  const live = useDashboardSignal("whatsapp.changed", (payload) => {
    window.clearTimeout(signalTimer.current);
    const conversationId = typeof payload.conversationId === "string" ? payload.conversationId : "";
    signalTimer.current = window.setTimeout(() => {
      void pollChanges();
      if (conversationId && conversationId === selectedId) void pollMessages(conversationId);
    }, 250);
  });
  useEffect(() => () => window.clearTimeout(signalTimer.current), []);

  // Polling stays as the backup, every few seconds without the live connection and every 30 with it.
  useEffect(() => {
    void pollChanges();
    const interval = window.setInterval(() => void pollChanges(), live ? 30_000 : 5_000);
    return () => window.clearInterval(interval);
  }, [live, pollChanges]);

  useEffect(() => {
    if (!selectedId) { setMessages([]); return; }
    latestMessage.current = null;
    setEarlierCursor(null);
    void Promise.all([listWhatsAppMessagePage(selectedId), markWhatsAppConversationRead(selectedId)])
      .then(([page, updated]) => { mergeMessages(page.items, true); setEarlierCursor(page.earlierCursor); apply([updated]); })
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to open the conversation."));
    // apply changes with the view; reopening the thread for that would reload it needlessly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId) return;
    const interval = window.setInterval(() => void pollMessages(selectedId), live ? 30_000 : 3_000);
    return () => window.clearInterval(interval);
  }, [live, pollMessages, selectedId]);

  /** Adds the page above the oldest message shown, keeping the reader where they were. */
  async function loadEarlier() {
    if (!selectedId || !earlierCursor) return;
    setLoadingEarlier(true);
    try {
      const page = await listWhatsAppMessagePage(selectedId, earlierCursor);
      const box = thread.current;
      if (box) nextScroll.current = { mode: "keep", fromBottom: box.scrollHeight - box.scrollTop };
      setMessages((current) => {
        const seen = new Set(current.map((item) => item.id));
        return [...page.items.filter((item) => !seen.has(item.id)), ...current];
      });
      setEarlierCursor(page.earlierCursor);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load earlier messages.");
    } finally { setLoadingEarlier(false); }
  }

  useLayoutEffect(() => {
    const box = thread.current, scroll = nextScroll.current;
    if (!box || !scroll) return;
    nextScroll.current = null;
    box.scrollTop = scroll.mode === "bottom" ? box.scrollHeight : box.scrollHeight - scroll.fromBottom;
  }, [messages]);

  async function loadMore() {
    if (!nextCursor) return;
    setLoadingMore(true);
    try {
      const page = await listWhatsAppConversations({ view, query: search, cursor: nextCursor, limit: PAGE_SIZE });
      setConversations((current) => {
        const seen = new Set(current.map((item) => item.id));
        return [...current, ...page.items.filter((item) => !seen.has(item.id))];
      });
      setNextCursor(page.nextCursor);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to load more conversations.");
    } finally { setLoadingMore(false); }
  }

  async function update(action: () => Promise<WhatsAppConversation>, failure: string) {
    setWorking(true);
    try {
      apply([await action()]);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : failure);
    } finally { setWorking(false); }
  }

  /** Shows a reply the team just sent, and updates its conversation the way the server has. */
  function sent(message: WhatsAppMessage) {
    setMessages((current) => [...current.filter((item) => item.id !== message.id), message]);
    if (selected) {
      apply([{ ...selected, lastMessagePreview: message.body, lastMessageAt: message.createdAt,
        needsAttention: false, attentionReason: null, updatedAt: message.updatedAt }]);
    }
  }

  async function sendMessage() {
    if (!selected || !draft.trim() || selected.mode !== "human" || !replyWindowOpen) return;
    setWorking(true);
    try {
      sent(await sendWhatsAppHumanMessage(selected.id, draft.trim()));
      setDraft("");
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

  const closed = selected?.status === "closed";

  return <section className={styles.page}>
    <PageHeader className={styles.header} title="WhatsApp inbox"
      description="Let agents handle routine messages and step in without losing context."
      actions={<Button variant="secondary" size="xs" onClick={() => void loadFirstPage()} disabled={loading}>
        <RefreshCw className={loading ? "spin" : ""} /> Refresh
      </Button>} />
    {error && <div className={styles.error}>{error}</div>}
    <div className={styles.workspace}>
      <aside className={styles.listPane}>
        <label className={styles.search}><Search size={16} /><input value={query}
          onChange={(event) => setQuery(event.target.value)} placeholder="Search name, number or message" /></label>
        <Tabs value={view} onValueChange={(value) => setView(value as WhatsAppInboxView)} className={styles.views}>
          <TabsList aria-label="Which conversations">
            {views.map((item) => <TabsTrigger key={item.value} value={item.value}>{item.label}</TabsTrigger>)}
          </TabsList>
        </Tabs>
        <div className={styles.list}>
          {loading ? <div className={styles.empty}><LoaderCircle className="spin" /> Loading conversations…</div>
            : conversations.length === 0 ? <div className={styles.empty}><MessageCircle />
              <strong>{emptyTitle(view, search)}</strong><span>{emptyHint(view, search)}</span></div>
              : <>{conversations.map((item) => <button type="button" key={item.id}
                className={item.id === selectedId ? styles.selected : ""} onClick={() => setSelected(item)}>
                <span className={styles.avatar}>{(item.customerName || item.customerNumber).slice(0, 1).toUpperCase()}</span>
                <div><strong>{item.customerName || item.customerNumber}</strong>
                  <span>{item.lastMessagePreview || "New WhatsApp conversation"}</span>
                  <small>{agentNames.get(item.agentId) || "Agent"} · {item.status === "closed" ? "Closed"
                    : item.mode === "ai" ? "Agent replying" : "Team replying"}</small>
                  {item.needsAttention && <i className={styles.needsReply}>Needs a reply</i>}</div>
                {item.unreadCount > 0 && <b>{item.unreadCount}</b>}
              </button>)}
              {nextCursor && <div className={styles.loadMore}>
                <Button variant="secondary" size="xs" disabled={loadingMore} onClick={() => void loadMore()}>
                  {loadingMore && <LoaderCircle className="spin" />} Load more</Button></div>}</>}
        </div>
      </aside>
      <main className={styles.thread}>
        {!selected ? <div className={styles.threadEmpty}><MessageCircle size={34} /><h2>Select a conversation</h2>
          <p>Messages and assignment controls will appear here.</p></div> : <>
          <header className={styles.threadHeader}>
            <div className={styles.identity}><span className={styles.avatar}>{(selected.customerName || selected.customerNumber).slice(0, 1).toUpperCase()}</span>
              <div><strong>{selected.customerName || selected.customerNumber}</strong><span>{selected.customerNumber}</span></div></div>
            <div className={styles.threadActions}>
              <Tabs value={selected.mode} onValueChange={(mode) => {
                if (mode !== selected.mode) void update(() => assignWhatsAppConversation(selected.id, mode as "ai" | "human"), "Unable to change assignment.");
              }}>
                <TabsList aria-label="Who is replying">
                  <TabsTrigger value="ai" disabled={working}><Bot /> AI replying</TabsTrigger>
                  <TabsTrigger value="human" disabled={working}><UserRound /> Take over</TabsTrigger>
                </TabsList>
              </Tabs>
              {closed
                ? <Button variant="secondary" size="xs" disabled={working}
                    onClick={() => void update(() => reopenWhatsAppConversation(selected.id), "Unable to reopen the conversation.")}>
                    <ArchiveRestore /> Reopen</Button>
                : <Button variant="secondary" size="xs" disabled={working} title="Done with this chat. Its summary and follow-ups run now."
                    onClick={() => void update(() => closeWhatsAppConversation(selected.id), "Unable to close the conversation.")}>
                    <Archive /> Close</Button>}
            </div>
          </header>
          {closed ? <div className={styles.closedNote} role="status">
            {selected.closedReason === "inactive" ? "Closed after a week without messages." : "Closed."} If the customer writes again, it reopens on its own.</div>
            : selected.needsAttention && selected.attentionReason && <div className={styles.attention} role="status">
              <TriangleAlert size={16} /><span>{attentionCopy(selected.attentionReason, selected.mode)}</span></div>}
          <div className={styles.messages} ref={thread}>
            {earlierCursor && <div className={styles.loadEarlier}>
              <Button variant="secondary" size="xs" disabled={loadingEarlier} onClick={() => void loadEarlier()}>
                {loadingEarlier && <LoaderCircle className="spin" />} Load earlier messages</Button></div>}
            {messages.map((message) => <article key={message.id}
              className={message.direction === "outbound" ? styles.outbound : styles.inbound}>
              {messageLabel(message) && <small>{messageLabel(message)}</small>}
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
              The agent is replying. Choose Take over to write yourself.</div>
              : !replyWindowOpen ? <>
                <div className={styles.aiNotice}><Clock size={17} />
                  It&apos;s been more than 24 hours since the customer wrote, so WhatsApp only accepts an approved template. You can write freely again once they reply.</div>
                <Button size="sm" variant="secondary" onClick={() => setTemplateOpen(true)}><FileText /> Send a template</Button>
                <WhatsAppTemplateDialog conversationId={selected.id} open={templateOpen} onOpenChange={setTemplateOpen} onSent={sent} />
              </> : <>
                <textarea value={draft} onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); void sendMessage(); } }}
                  placeholder="Write a WhatsApp reply…" rows={2} />
                <div className={styles.sendColumn}>
                  <Button size="icon" aria-label="Send reply" disabled={working || !draft.trim()} onClick={() => void sendMessage()}>
                    {working ? <LoaderCircle className="spin" /> : <Send />}
                  </Button>
                  {selected.replyWindowEndsAt && <small title="WhatsApp allows free replies for 24 hours after the customer's last message.">
                    Open {timeLeft(selected.replyWindowEndsAt, now)}</small>}
                </div></>}
          </footer>
        </>}
      </main>
    </div>
  </section>;
}

/** Whether a conversation shows in the list for this view and search, as the server would decide. */
function belongsIn(item: WhatsAppConversation, view: WhatsAppInboxView, search: string) {
  if (view === "closed" ? item.status !== "closed" : item.status === "closed") return false;
  if (view === "attention" && !item.needsAttention) return false;
  const value = search.trim().toLowerCase();
  if (!value) return true;
  const digits = value.replace(/[\s+()-]/g, "");
  const term = /^\d+$/.test(digits) ? digits : value;
  return [item.customerName, item.customerNumber, item.lastMessagePreview]
    .some((field) => field?.toLowerCase().includes(term));
}

function newestFirst(a: WhatsAppConversation, b: WhatsAppConversation) {
  return (b.lastMessageAt ?? "").localeCompare(a.lastMessageAt ?? "") || b.id.localeCompare(a.id);
}

function emptyTitle(view: WhatsAppInboxView, search: string) {
  if (search.trim()) return "No matching conversations";
  return view === "attention" ? "Nothing needs a reply" : view === "closed" ? "No closed conversations" : "No WhatsApp conversations yet";
}

function emptyHint(view: WhatsAppInboxView, search: string) {
  if (search.trim()) return "Try a different name, number or word.";
  return view === "attention" ? "Customers waiting on your team will appear here."
    : view === "closed" ? "Conversations you close move here." : "New customer messages will appear here.";
}

const attentionMessages: Record<WhatsAppAttentionReason, string> = {
  agent_failed: "The agent couldn't answer the last message after several tries.",
  agent_paused: "Automatic replies are paused for this workspace, so the agent didn't answer. Check Billing to turn them back on.",
  waiting_for_team: "The customer is waiting for a reply from your team.",
};

function attentionCopy(reason: WhatsAppAttentionReason, mode: WhatsAppConversation["mode"]) {
  if (reason === "waiting_for_team") {
    return mode === "ai"
      ? "The customer wrote while your team was replying. The agent answers new messages again."
      : attentionMessages.waiting_for_team;
  }
  const message = attentionMessages[reason] ?? "This conversation needs your attention.";
  return mode === "ai" ? `${message} Take over to reply yourself.` : message;
}

/** The small line above a message: who sent it, and what kind of message it is when not plain text. */
function messageLabel(message: WhatsAppMessage) {
  const author = message.direction !== "outbound" ? ""
    : message.author === "ai" ? "Agent"
      : message.author === "staff" ? (message.sentBy ? `Team · ${message.sentBy}` : "Team") : "";
  const kind = message.type === "text" ? ""
    : message.type === "audio" ? "Voice note"
      : message.type === "template" ? `Template${message.templateName ? ` ${message.templateName.replace(/_/g, " ")}` : ""}`
        : message.type;
  return [author, kind].filter(Boolean).join(" · ");
}

/** Time left in the reply window, for example "for 3 h" or "for 12 min". */
function timeLeft(endsAt: string, now: number) {
  const minutes = Math.max(0, Math.round((new Date(endsAt).getTime() - now) / 60_000));
  return minutes >= 60 ? `for ${Math.floor(minutes / 60)} h` : `for ${minutes} min`;
}

function useNow(intervalMs: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}

function useDebounced<T>(value: T, delayMs: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [delayMs, value]);
  return debounced;
}
