"use client";

import { useEffect, useState } from "react";
import { CircleAlert, History, LoaderCircle, RotateCcw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { Dialog, DialogClose, DialogContent } from "@/components/ui/overlays";
import { listAgentVersions, restoreAgentVersion } from "@/lib/api/agents";
import type { AgentVersion } from "@/types/api";
import { Note, StudioGroup, StudioSection } from "./studio-kit";

const when = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** What restoring a version puts back, in words. Documents are files, so they are never put back. */
export function restoreScope(version: AgentVersion): string {
  const tools = version.parts.includes("tools");
  const integrations = version.parts.includes("integrations");
  const extra = tools && integrations ? ", tools and integrations" : tools ? " and tools" : integrations ? " and integrations" : "";
  const kept = tools || integrations
    ? "Documents stay as they are now."
    : "Tools, integrations and documents stay as they are now; this version was saved before they were kept.";
  return `Its settings, business details${extra} come back. ${kept}`;
}

/** Every version of an agent, newest first, with a way to make an older one live again. */
export function VersionHistory({ agentId, refreshKey, hasUnsavedChanges, onRestored }: {
  agentId: string;
  /** Changes after each publish or direct edit, so the list reloads. */
  refreshKey: number;
  hasUnsavedChanges: boolean;
  onRestored: () => void;
}) {
  const [versions, setVersions] = useState<AgentVersion[] | null>(null);
  const [failure, setFailure] = useState("");
  const [confirming, setConfirming] = useState<AgentVersion | null>(null);
  const [restoring, setRestoring] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setFailure("");
    listAgentVersions(agentId)
      .then((list) => { if (!cancelled) setVersions(list); })
      .catch((caught) => { if (!cancelled) setFailure(caught instanceof Error ? caught.message : "Couldn’t load the versions."); });
    return () => { cancelled = true; };
  }, [agentId, refreshKey]);

  async function restore(version: AgentVersion) {
    setRestoring(true);
    try {
      await restoreAgentVersion(agentId, version.version);
      toast.success(`Version ${version.version} is live again`, { description: "It was published as the newest version." });
      setConfirming(null);
      onRestored();
    } catch (caught) {
      setFailure(caught instanceof Error ? caught.message : "Couldn’t restore that version.");
      setConfirming(null);
    } finally {
      setRestoring(false);
    }
  }

  return (
    <StudioSection eyebrow="Versions" title="History" description="Every publish is kept here, and so is every change you make directly: a business detail, a tool, a document or an integration. Changes made a minute or two apart share one version. Restoring makes an older version live again; nothing is deleted.">
      <StudioGroup title="Versions">
        {failure && <Alert>{failure}</Alert>}
        {versions === null && !failure ? (
          <div className="h-40 animate-pulse rounded-[var(--radius-control)] bg-tint/[0.04]" aria-label="Loading versions" />
        ) : versions && versions.length === 0 ? (
          <Note tone="neutral" icon={<History />} title="No versions yet">
            The next time you publish or change this agent, its settings are kept here.
          </Note>
        ) : (
          <ol className="m-0 grid list-none gap-0 divide-y divide-line rounded-[var(--radius-card)] border border-line p-0">
            {versions?.map((version) => (
              <li key={version.version} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="m-0 flex items-center gap-2 text-[0.88rem] font-medium text-ink">
                    Version {version.version}
                    {version.current && <span className="rounded-[4px] bg-success/12 px-1.5 py-[3px] text-[0.68rem] font-medium leading-none text-success">Live</span>}
                  </p>
                  <p className="m-0 mt-0.5 text-[0.78rem] text-fg-muted">
                    {version.kind === "edit" ? "Changed" : "Published"} {when.format(new Date(version.publishedAt))}
                    {version.publishedBy ? ` · ${version.publishedBy}` : ""}
                    {version.note ? ` · ${version.note}` : ""}
                  </p>
                </div>
                {!version.current && (
                  <Button type="button" size="xs" variant="secondary" onClick={() => setConfirming(version)}>
                    <RotateCcw size={15} /> Restore
                  </Button>
                )}
              </li>
            ))}
          </ol>
        )}
      </StudioGroup>

      <Dialog open={confirming !== null} onOpenChange={(open) => { if (!open && !restoring) setConfirming(null); }}>
        {confirming && (
          <DialogContent
            title={`Restore version ${confirming.version}?`}
            description={`Callers get this version from their next call. ${restoreScope(confirming)} Your current settings stay in the history, so you can switch back.`}
          >
            {hasUnsavedChanges && (
              <div className="mb-4">
                <Note tone="warning" icon={<CircleAlert />} title="Unsaved edits will be replaced">
                  Save them as a draft first if you want to keep them.
                </Note>
              </div>
            )}
            <div className="flex justify-end gap-2">
              <DialogClose asChild>
                <Button type="button" variant="secondary" size="sm" disabled={restoring}>Cancel</Button>
              </DialogClose>
              <Button type="button" size="sm" disabled={restoring} onClick={() => void restore(confirming)}>
                {restoring ? <LoaderCircle className="spin" /> : <RotateCcw />} Restore and publish
              </Button>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </StudioSection>
  );
}
