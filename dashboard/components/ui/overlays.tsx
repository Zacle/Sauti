"use client";

/**
 * Sauti design system: overlays.
 * - Dialog: centred modal (Radix Dialog) for confirmations and short forms.
 * - Drawer: Vaul side panel for details (call, booking) that keeps the list visible.
 *   On small screens it becomes a bottom sheet you can swipe away.
 */

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Drawer as Vaul } from "vaul";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

const overlay =
  "fixed inset-0 z-[150] bg-scrim data-[state=open]:animate-[fade-in_180ms_ease-out] data-[state=closed]:animate-[fade-out_140ms_ease-in]";

/* ---------- Dialog ---------- */

export const Dialog = DialogPrimitive.Root;
export const DialogTrigger = DialogPrimitive.Trigger;
export const DialogClose = DialogPrimitive.Close;

export function DialogContent({
  className,
  children,
  title,
  description,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & { title: React.ReactNode; description?: React.ReactNode }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className={overlay} />
      <DialogPrimitive.Content
        className={cn(
          "fixed left-1/2 top-1/2 z-[151] w-[min(92vw,30rem)] -translate-x-1/2 -translate-y-1/2 rounded-[var(--radius-card)] border border-line-strong bg-popover p-6 font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[dialog-in_220ms_cubic-bezier(0.16,1,0.3,1)]",
          className,
        )}
        {...props}
      >
        <DialogPrimitive.Title className="m-0 pr-8 text-lg font-medium tracking-tight text-ink">{title}</DialogPrimitive.Title>
        {description ? (
          <DialogPrimitive.Description className="m-0 mt-1.5 text-sm leading-relaxed text-fg-soft">{description}</DialogPrimitive.Description>
        ) : (
          <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
        )}
        <div className="mt-5">{children}</div>
        <DialogPrimitive.Close
          aria-label="Close"
          className="absolute right-4 top-4 grid size-8 cursor-pointer place-items-center rounded-lg border-0 bg-transparent text-fg-muted transition-colors hover:bg-tint/[0.06] hover:text-ink"
        >
          <X className="size-4" />
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

/* ---------- Drawer (Vaul) ---------- */

function useIsWide() {
  const [wide, setWide] = React.useState(true);
  React.useEffect(() => {
    const query = window.matchMedia("(min-width: 768px)");
    const update = () => setWide(query.matches);
    update();
    query.addEventListener("change", update);
    return () => query.removeEventListener("change", update);
  }, []);
  return wide;
}

export function Drawer({
  open,
  onOpenChange,
  title,
  description,
  actions,
  children,
  width = "34rem",
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: React.ReactNode;
  description?: React.ReactNode;
  /** Buttons shown in the header next to the close button. */
  actions?: React.ReactNode;
  children: React.ReactNode;
  width?: string;
}) {
  const wide = useIsWide();
  return (
    <Vaul.Root open={open} onOpenChange={onOpenChange} direction={wide ? "right" : "bottom"}>
      <Vaul.Portal>
        <Vaul.Overlay className="fixed inset-0 z-[150] bg-scrim" />
        <Vaul.Content
          style={wide ? ({ width: `min(100vw, ${width})` } as React.CSSProperties) : undefined}
          className={cn(
            "fixed z-[151] flex flex-col border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none",
            wide ? "inset-y-0 right-0 border-l" : "inset-x-0 bottom-0 max-h-[88dvh] rounded-t-[1.5rem] border-t",
          )}
        >
          {!wide ? <div className="mx-auto mt-3 h-1.5 w-12 shrink-0 rounded-full bg-tint/15" aria-hidden="true" /> : null}
          <header className="flex items-start gap-3 border-b border-line px-6 py-5">
            <div className="min-w-0 flex-1">
              <Vaul.Title className="m-0 truncate text-lg font-medium tracking-tight text-ink">{title}</Vaul.Title>
              {description ? (
                <Vaul.Description className="m-0 mt-1 text-sm text-fg-soft">{description}</Vaul.Description>
              ) : (
                <Vaul.Description className="sr-only">{title}</Vaul.Description>
              )}
            </div>
            {actions}
            <Vaul.Close
              aria-label="Close"
              className="grid size-9 shrink-0 cursor-pointer place-items-center rounded-lg border-0 bg-transparent text-fg-muted transition-colors hover:bg-tint/[0.06] hover:text-ink"
            >
              <X className="size-[18px]" />
            </Vaul.Close>
          </header>
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">{children}</div>
        </Vaul.Content>
      </Vaul.Portal>
    </Vaul.Root>
  );
}
