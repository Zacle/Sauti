"use client";

import { useState } from "react";
import * as PopoverPrimitive from "@radix-ui/react-popover";
import { Check, ChevronsUpDown, Plus, Settings2 } from "lucide-react";
import Link from "next/link";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";
import { roleLabel } from "../domain/members";
import { CreateWorkspaceDialog } from "./CreateWorkspaceDialog";

/**
 * The workspace name at the top of the sidebar. Opens a list of the person's workspaces; picking
 * one reloads the console in that workspace (only this tab switches).
 */
export function WorkspaceSwitcher({ compact }: { compact: boolean }) {
  const { session, selectWorkspace } = useAuth();
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const tenant = session?.tenant;
  const workspaces = session?.account.workspaces ?? [];
  const initial = tenant?.businessName?.trim().slice(0, 1).toUpperCase() ?? "S";

  function choose(workspaceId: string) {
    setOpen(false);
    if (workspaceId === tenant?.id) return;
    if (selectWorkspace(workspaceId)) window.location.assign("/dashboard");
  }

  return (
    <>
      <PopoverPrimitive.Root open={open} onOpenChange={setOpen}>
        <PopoverPrimitive.Trigger asChild>
          <button
            type="button"
            className="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5 rounded-[7px] border-0 bg-transparent px-1.5 py-1.5 text-left font-sans transition-colors hover:bg-tint/[0.05] data-[state=open]:bg-tint/[0.05]"
            aria-label={`Workspace: ${tenant?.businessName ?? "none"}. Switch workspace`}
            title={tenant?.businessName}
          >
            <span className="grid size-7 shrink-0 place-items-center rounded-[6px] bg-primary text-[0.8rem] font-semibold text-primary-fg">{initial}</span>
            {!compact ? (
              <>
                <span className="min-w-0 flex-1 leading-tight">
                  <span className="block truncate text-[0.84rem] font-semibold text-ink">{tenant?.businessName ?? "No workspace"}</span>
                  <span className="block truncate text-[0.72rem] capitalize text-fg-muted">{tenant ? `${tenant.plan ?? "starter"} plan` : "Create one to start"}</span>
                </span>
                <ChevronsUpDown className="size-3.5 shrink-0 text-fg-muted" aria-hidden="true" />
              </>
            ) : null}
          </button>
        </PopoverPrimitive.Trigger>
        <PopoverPrimitive.Portal>
          <PopoverPrimitive.Content
            align="start"
            sideOffset={6}
            className="z-[200] w-[16.5rem] rounded-[var(--radius-popover)] border border-line-strong bg-popover p-1.5 font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[select-in_140ms_cubic-bezier(0.16,1,0.3,1)]"
          >
            <p className="m-0 truncate px-2 pb-1.5 pt-1 text-[0.75rem] text-fg-muted">{session?.account.email}</p>
            <ul className="m-0 grid max-h-[18rem] list-none gap-px overflow-y-auto p-0" role="listbox" aria-label="Workspaces">
              {workspaces.map(({ workspace, role }) => {
                const active = workspace.id === tenant?.id;
                return (
                  <li key={workspace.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={active}
                      onClick={() => choose(workspace.id)}
                      className={cn(
                        "flex w-full cursor-pointer items-center gap-2.5 rounded-[6px] border-0 bg-transparent px-2 py-1.5 text-left font-sans transition-colors hover:bg-tint/[0.06]",
                        active && "bg-tint/[0.04]",
                      )}
                    >
                      <span className="grid size-6 shrink-0 place-items-center rounded-[5px] bg-surface-3 text-[0.72rem] font-semibold text-fg">
                        {workspace.businessName.trim().slice(0, 1).toUpperCase()}
                      </span>
                      <span className="min-w-0 flex-1 leading-tight">
                        <span className="block truncate text-[0.83rem] text-ink">{workspace.businessName}</span>
                        <span className="block text-[0.72rem] text-fg-muted">{roleLabel(role)}</span>
                      </span>
                      {active ? <Check className="size-3.5 shrink-0 text-ink" aria-hidden="true" /> : null}
                    </button>
                  </li>
                );
              })}
            </ul>
            <div className="mt-1.5 grid gap-px border-t border-line pt-1.5">
              {tenant ? (
                <Link
                  href="/settings?tab=members"
                  onClick={() => setOpen(false)}
                  className="flex items-center gap-2.5 rounded-[6px] px-2 py-1.5 text-[0.83rem] text-fg-soft no-underline transition-colors hover:bg-tint/[0.06] hover:text-ink"
                >
                  <Settings2 className="size-3.5" aria-hidden="true" />
                  Members and roles
                </Link>
              ) : null}
              <button
                type="button"
                onClick={() => {
                  setOpen(false);
                  setCreating(true);
                }}
                className="flex w-full cursor-pointer items-center gap-2.5 rounded-[6px] border-0 bg-transparent px-2 py-1.5 text-left font-sans text-[0.83rem] text-fg-soft transition-colors hover:bg-tint/[0.06] hover:text-ink"
              >
                <Plus className="size-3.5" aria-hidden="true" />
                Create workspace
              </button>
            </div>
          </PopoverPrimitive.Content>
        </PopoverPrimitive.Portal>
      </PopoverPrimitive.Root>
      <CreateWorkspaceDialog open={creating} onOpenChange={setCreating} />
    </>
  );
}
