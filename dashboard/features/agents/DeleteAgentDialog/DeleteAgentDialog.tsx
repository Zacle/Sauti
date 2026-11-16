"use client";

import { LoaderCircle, Trash2 } from "lucide-react";
import type { Agent } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { Dialog, DialogContent } from "@/components/ui/overlays";

export function DeleteAgentDialog({
  agent,
  busy,
  error,
  onCancel,
  onConfirm,
}: {
  agent: Agent;
  busy: boolean;
  error?: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onCancel(); }}>
      <DialogContent
        title={`Delete ${agent.name}?`}
        description="This permanently removes the agent's configuration, tools and business variables. Agents with call or booking history can't be deleted."
        onEscapeKeyDown={(event) => { if (busy) event.preventDefault(); }}
        onPointerDownOutside={(event) => { if (busy) event.preventDefault(); }}
      >
        {agent.active ? (
          <Alert tone="error">This agent is live. Deleting it takes it offline immediately.</Alert>
        ) : null}
        {error ? <Alert tone="error" className="mt-3">{error}</Alert> : null}
        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <Button variant="secondary" size="sm" disabled={busy} onClick={onCancel} autoFocus>
            Keep agent
          </Button>
          <Button variant="danger" size="sm" disabled={busy} onClick={onConfirm}>
            {busy ? <LoaderCircle className="animate-spin" /> : <Trash2 />}
            {busy ? "Deleting…" : "Delete agent"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
