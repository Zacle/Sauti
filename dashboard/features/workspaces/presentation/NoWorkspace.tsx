"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/console";
import { CreateWorkspaceDialog } from "./CreateWorkspaceDialog";

/** Shown in place of console pages when the signed-in person is not in any workspace. */
export function NoWorkspace() {
  const [creating, setCreating] = useState(false);
  return (
    <div className="grid max-w-xl gap-5">
      <PageHeader
        title="You're not in a workspace yet"
        description="A workspace holds one business's agents, phone numbers, calls and billing. Create one, or ask a colleague to invite you to theirs."
      />
      <div className="flex flex-wrap items-center gap-3 rounded-[var(--radius-card)] border border-line bg-surface px-5 py-4">
        <p className="m-0 min-w-0 flex-1 text-[0.86rem] leading-relaxed text-fg-soft">
          Invited by a colleague? Open the link in their email and you&apos;ll join their workspace.
        </p>
        <Button size="sm" onClick={() => setCreating(true)}>Create workspace</Button>
      </div>
      <CreateWorkspaceDialog open={creating} onOpenChange={setCreating} />
    </div>
  );
}
