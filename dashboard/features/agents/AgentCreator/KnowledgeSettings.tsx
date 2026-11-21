"use client";


import { Button, buttonVariants } from "@/components/ui/button";
import { Alert, Textarea } from "@/components/ui/field";
import { Badge } from "@/components/ui/primitives";
import {
deleteKnowledgeDocument,
listKnowledgeDocuments,
uploadKnowledgeDocument
} from "@/lib/api/agents";
import { cn } from "@/lib/utils";
import type { KnowledgeDocument } from "@/types/api";
import {
BookOpen,
ChevronDown,
CircleAlert,
FileText,
FileUp,
LoaderCircle,
Trash2
} from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
Meta,
Note,
StudioGroup,
StudioSection
} from "./studio-kit";


export function KnowledgeSettings({ agentId, knowledgeBase, onKnowledgeBase }: { agentId?: string; knowledgeBase: string; onKnowledgeBase: (value: string) => void }) {
  const chunks = knowledgeChunks(knowledgeBase);
  const totalChunkCharacters = chunks.reduce((total, chunk) => total + chunk.length, 0);
  const promptCharacters = Math.min(3000, totalChunkCharacters);
  const omitted = totalChunkCharacters > 3000;
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [documentBusy, setDocumentBusy] = useState(false);
  const [documentError, setDocumentError] = useState("");
  const [openChunk, setOpenChunk] = useState<number | null>(0);

  useEffect(() => {
    if (!agentId) return;
    listKnowledgeDocuments(agentId)
      .then(setDocuments)
      .catch((caught) => setDocumentError(caught instanceof Error ? caught.message : "Unable to load documents."));
  }, [agentId]);

  async function addDocument(file?: File) {
    if (!agentId || !file) return;
    setDocumentBusy(true);
    setDocumentError("");
    try {
      const document = await uploadKnowledgeDocument(agentId, file);
      setDocuments((current) => [document, ...current]);
      toast.success(`${file.name} added`, { description: "The agent can use it on the next call." });
    } catch (caught) {
      setDocumentError(caught instanceof Error ? caught.message : "Unable to index this document.");
    } finally {
      setDocumentBusy(false);
    }
  }

  async function removeDocument(documentId: string) {
    if (!agentId) return;
    setDocumentBusy(true);
    setDocumentError("");
    try {
      await deleteKnowledgeDocument(agentId, documentId);
      setDocuments((current) => current.filter((document) => document.id !== documentId));
    } catch (caught) {
      setDocumentError(caught instanceof Error ? caught.message : "Unable to delete this document.");
    } finally {
      setDocumentBusy(false);
    }
  }

  const usage = Math.min(100, (promptCharacters / 3000) * 100);

  return (
    <StudioSection eyebrow="Knowledge" title="Ground the agent in approved information" description="Add notes directly or index business documents for semantic retrieval.">
      <StudioGroup
        title="Approved knowledge"
        description="Facts the agent may quote: services, prices, policies. Separate topics with a blank line."
        action={<Meta>{knowledgeBase.length.toLocaleString()} / 10,000</Meta>}
      >
        <Textarea
          aria-label="Approved knowledge"
          maxLength={10000}
          className="min-h-[340px] text-[0.88rem] leading-relaxed"
          value={knowledgeBase}
          onChange={(event) => onKnowledgeBase(event.target.value)}
          placeholder={"Services\nConsultations are available Monday to Friday.\n\nCancellation policy\nPlease give at least 24 hours’ notice."}
        />
        <div className="grid gap-2">
          <div className="flex flex-wrap justify-between gap-2 text-[0.76rem] text-fg-muted">
            <span><span className="font-medium tabular-nums text-fg-soft">{promptCharacters.toLocaleString()}</span> of 3,000 characters added to each call</span>
            <span className="tabular-nums">{chunks.length} {chunks.length === 1 ? "section" : "sections"}</span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-tint/[0.08]">
            <div className={cn("h-full rounded-full transition-[width] duration-300", omitted ? "bg-warning" : "bg-signal")} style={{ width: `${usage}%` }} />
          </div>
        </div>
        {omitted && (
          <Note tone="warning" icon={<CircleAlert />} title="Some of this won’t fit in a call">
            Only the first 3,000 characters go into each call. Move longer material into a document below; documents are searched and only the relevant parts are used.
          </Note>
        )}
      </StudioGroup>

      <StudioGroup title="How it’s split" description="What you write above is divided into sections like these." action={<Meta>{chunks.length} {chunks.length === 1 ? "section" : "sections"}</Meta>}>
        {chunks.length === 0 ? (
          <EmptyBox icon={<BookOpen />} title="Nothing to preview yet" text="Add knowledge above to see how it’s divided." />
        ) : (
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">
            {chunks.map((chunk, index) => {
              const offset = chunks.slice(0, index).reduce((total, item) => total + item.length, 0);
              const status = offset >= 3000 ? "Left out" : offset + chunk.length > 3000 ? "Partly included" : "Included";
              const open = openChunk === index;
              return (
                <div key={`${index}-${chunk.slice(0, 20)}`}>
                  <button
                    type="button"
                    aria-expanded={open}
                    onClick={() => setOpenChunk(open ? null : index)}
                    className="flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-tint/[0.03]"
                  >
                    <span className="text-[0.86rem] font-medium text-ink">Section {index + 1}</span>
                    <span className="text-[0.76rem] tabular-nums text-fg-muted">{chunk.length} characters</span>
                    <Badge tone={status === "Included" ? "success" : status === "Left out" ? "warning" : "neutral"} className="ml-auto">{status}</Badge>
                    <ChevronDown className={cn("size-4 text-fg-muted transition-transform", open && "rotate-180")} />
                  </button>
                  {open ? <p className="m-0 whitespace-pre-wrap px-4 pb-4 text-[0.82rem] leading-relaxed text-fg-soft">{chunk}</p> : null}
                </div>
              );
            })}
          </div>
        )}
      </StudioGroup>

      <StudioGroup
        title="Documents"
        description="Upload policies, menus or FAQs. The agent searches them on every call and uses only the parts that are relevant."
        action={agentId ? (
          <label className={cn(buttonVariants({ variant: "secondary", size: "xs" }), documentBusy && "pointer-events-none opacity-45")}>
            {documentBusy ? <LoaderCircle className="spin" /> : <FileUp />}
            {documentBusy ? "Adding…" : "Upload"}
            <input
              className="sr-only"
              accept=".pdf,.doc,.docx,.txt,.md,.csv,.html,.htm"
              disabled={documentBusy}
              type="file"
              onChange={(event) => {
                void addDocument(event.target.files?.[0]);
                event.currentTarget.value = "";
              }}
            />
          </label>
        ) : (
          <Button variant="secondary" size="xs" disabled><FileUp /> Save agent first</Button>
        )}
      >
        {documentError ? <Alert>{documentError}</Alert> : null}
        {documents.length === 0 ? (
          <EmptyBox icon={<FileText />} title="No documents yet" text="PDF, Word, text, Markdown, CSV or HTML. Up to 10 MB each, 20 per agent." />
        ) : (
          <div className="divide-y divide-line overflow-hidden rounded-[var(--radius-card)] border border-line">
            {documents.map((document) => (
              <div key={document.id} className="flex items-center gap-3 px-4 py-3">
                <span className="grid size-9 shrink-0 place-items-center rounded-[var(--radius-control)] bg-tint/[0.05] text-fg-soft"><FileText className="size-4" /></span>
                <div className="min-w-0 flex-1">
                  <p className="m-0 truncate text-[0.86rem] font-medium text-ink">{document.fileName}</p>
                  <p className="m-0 mt-0.5 text-[0.76rem] text-fg-muted">
                    {document.chunkCount} sections · {document.characterCount.toLocaleString()} characters
                    {document.originalStored && document.originalSizeBytes ? ` · ${formatFileSize(document.originalSizeBytes)}` : ""}
                  </p>
                </div>
                <Badge tone={document.status === "ready" ? "success" : "neutral"} className="capitalize">{document.status}</Badge>
                <Button variant="ghost" size="icon-xs" aria-label={`Delete ${document.fileName}`} disabled={documentBusy} className="hover:bg-danger/[0.08] hover:text-danger" onClick={() => void removeDocument(document.id)}>
                  <Trash2 />
                </Button>
              </div>
            ))}
          </div>
        )}
        {documents.length > 0 ? <p className="m-0 text-[0.76rem] text-fg-muted">PDF, Word, text, Markdown, CSV or HTML · up to 10 MB each · {documents.length} of 20 used</p> : null}
      </StudioGroup>
    </StudioSection>
  );
}

export function EmptyBox({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="grid place-items-center gap-1.5 rounded-[var(--radius-card)] border border-dashed border-line-strong px-6 py-8 text-center">
      <span className="mb-1 grid size-9 place-items-center rounded-full bg-tint/[0.05] text-fg-muted [&_svg]:size-4">{icon}</span>
      <p className="m-0 text-[0.86rem] font-medium text-ink">{title}</p>
      <p className="m-0 max-w-sm text-[0.78rem] leading-relaxed text-fg-muted">{text}</p>
    </div>
  );
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function knowledgeChunks(value: string) {
  const normalized = value.replace(/\r\n?/g, "\n").split("\n").map((line) => line.trim()).join("\n").replace(/\n{3,}/g, "\n\n").trim();
  if (!normalized) return [];
  const chunks: string[] = [];
  let current = "";
  for (const paragraph of normalized.split(/\n\s*\n/)) {
    for (const word of paragraph.trim().split(/\s+/)) {
      if (current && current.length + word.length + 1 > 700) {
        chunks.push(current.trim());
        current = "";
      }
      current += `${current && !/\s$/.test(current) ? " " : ""}${word}`;
    }
    if (current.length >= 490) {
      chunks.push(current.trim());
      current = "";
    } else if (current) {
      current += "\n\n";
    }
  }
  if (current.trim()) chunks.push(current.trim());
  return chunks;
}
