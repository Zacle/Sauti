"use client";

import { Link2 } from "lucide-react";
import { toast } from "@/components/ui/sonner";

/** Copies a deep link to a document section and confirms with a toast. */
export function CopySectionLink({ id, title }: { id: string; title: string }) {
  function copy() {
    const url = `${window.location.origin}${window.location.pathname}#${id}`;
    navigator.clipboard
      .writeText(url)
      .then(() => toast.success("Link copied", { description: title }))
      .catch(() => toast.error("Couldn't copy the link"));
  }

  return (
    <button
      type="button"
      onClick={copy}
      aria-label={`Copy link to ${title}`}
      className="grid size-8 shrink-0 place-items-center rounded-lg text-fg-muted opacity-0 transition hover:bg-white/[0.06] hover:text-white focus-visible:opacity-100 group-hover/section:opacity-100"
    >
      <Link2 className="size-4" aria-hidden="true" />
    </button>
  );
}
