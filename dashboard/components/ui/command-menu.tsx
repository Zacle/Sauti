"use client";

/**
 * ⌘K / Ctrl+K command menu for the console (cmdk inside a Radix Dialog).
 * Jump to any page, run common actions, or open an agent by name.
 */

import * as React from "react";
import { useRouter } from "next/navigation";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { Command } from "cmdk";
import type { LucideIcon } from "lucide-react";
import {
  BarChart3,
  Bot,
  CalendarDays,
  CircleHelp,
  CreditCard,
  LayoutDashboard,
  MessageCircle,
  Monitor,
  Moon,
  Palette,
  PhoneCall,
  Plug,
  Plus,
  Search,
  Settings,
  Sparkles,
  Sun,
} from "lucide-react";
import { setThemePrefs, type ThemeMode } from "@/lib/console-theme";
import { listAgents } from "@/lib/api/agents";
import { Kbd } from "./primitives";

type Entry = { label: string; href: string; icon: LucideIcon; keywords?: string[]; hint?: string };

const pages: Entry[] = [
  { label: "Home", href: "/dashboard", icon: LayoutDashboard, keywords: ["home", "dashboard"] },
  { label: "Agents", href: "/agents", icon: Bot },
  { label: "Calls", href: "/calls", icon: PhoneCall, keywords: ["transcripts", "history"] },
  { label: "Inbox", href: "/inbox", icon: MessageCircle, keywords: ["whatsapp", "messages"] },
  { label: "Bookings", href: "/bookings", icon: CalendarDays, keywords: ["appointments", "calendar"] },
  { label: "Analytics", href: "/analytics", icon: BarChart3, keywords: ["reports", "metrics"] },
  { label: "Integrations", href: "/dashboard/integrations", icon: Plug, keywords: ["calendar", "crm", "webhook"] },
  { label: "Usage & billing", href: "/billing", icon: CreditCard, keywords: ["plan", "invoice"] },
  { label: "Settings", href: "/settings", icon: Settings, keywords: ["workspace", "profile"] },
  { label: "Help center", href: "/help", icon: CircleHelp, keywords: ["support", "docs"] },
];

const actions: Entry[] = [
  { label: "Create a new agent", href: "/agents/new", icon: Plus, keywords: ["add", "new"] },
  { label: "Test an agent", href: "/agents/new", icon: Sparkles, keywords: ["try", "call"] },
];

const themeEntries: Array<{ label: string; mode: ThemeMode; icon: LucideIcon }> = [
  { label: "Use light theme", mode: "light", icon: Sun },
  { label: "Use dark theme", mode: "dark", icon: Moon },
  { label: "Match system theme", mode: "system", icon: Monitor },
];

const group =
  "[&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:pb-1.5 [&_[cmdk-group-heading]]:pt-3 [&_[cmdk-group-heading]]:text-[0.72rem] [&_[cmdk-group-heading]]:font-medium [&_[cmdk-group-heading]]:text-fg-muted";
const item =
  "flex cursor-pointer items-center gap-3 rounded-[6px] px-3 py-2 text-[0.86rem] text-fg-soft outline-none data-[selected=true]:bg-tint/[0.07] data-[selected=true]:text-ink [&_svg]:size-4";

/** Opens on ⌘K / Ctrl+K anywhere, or via the returned `setOpen`. */
export function useCommandMenu() {
  const [open, setOpen] = React.useState(false);
  React.useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key.toLowerCase() === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setOpen((value) => !value);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  return { open, setOpen };
}

export function CommandMenu({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const router = useRouter();
  const [agents, setAgents] = React.useState<Array<{ id: string; name: string }>>([]);

  React.useEffect(() => {
    if (!open || agents.length) return;
    listAgents()
      .then((list) => setAgents((list as Array<{ id: string; name: string }>).map(({ id, name }) => ({ id, name }))))
      .catch(() => undefined);
  }, [agents.length, open]);

  function go(href: string) {
    onOpenChange(false);
    router.push(href);
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-[160] bg-scrim data-[state=open]:animate-[fade-in_160ms_ease-out]" />
        <DialogPrimitive.Content className="fixed left-1/2 top-[14vh] z-[161] w-[min(92vw,36rem)] -translate-x-1/2 overflow-hidden rounded-[var(--radius-popover)] border border-line-strong bg-popover font-sans text-fg shadow-[var(--shadow-pop)] outline-none data-[state=open]:animate-[select-in_200ms_cubic-bezier(0.16,1,0.3,1)]">
          <DialogPrimitive.Title className="sr-only">Command menu</DialogPrimitive.Title>
          <DialogPrimitive.Description className="sr-only">Search pages, agents and actions</DialogPrimitive.Description>
          <Command loop>
            <div className="flex items-center gap-3 border-b border-line px-4">
              <Search className="size-4 shrink-0 text-fg-muted" aria-hidden="true" />
              <Command.Input
                autoFocus
                placeholder="Search pages, agents and actions…"
                className="h-14 w-full appearance-none border-0 bg-transparent p-0 font-sans text-[0.95rem] text-fg shadow-none outline-none placeholder:text-fg-muted focus:outline-none"
              />
              <Kbd>Esc</Kbd>
            </div>
            <Command.List className="max-h-[min(26rem,60vh)] overflow-y-auto overscroll-contain p-2">
              <Command.Empty className="px-3 py-10 text-center text-sm text-fg-muted">No results</Command.Empty>
              <Command.Group heading="Actions" className={group}>
                {actions.map((entry) => (
                  <Command.Item key={entry.label} value={entry.label} keywords={entry.keywords} onSelect={() => go(entry.href)} className={item}>
                    <entry.icon aria-hidden="true" className="text-signal" />
                    {entry.label}
                  </Command.Item>
                ))}
              </Command.Group>
              <Command.Group heading="Go to" className={group}>
                {pages.map((entry) => (
                  <Command.Item key={entry.href} value={entry.label} keywords={entry.keywords} onSelect={() => go(entry.href)} className={item}>
                    <entry.icon aria-hidden="true" />
                    {entry.label}
                  </Command.Item>
                ))}
              </Command.Group>
              <Command.Group heading="Appearance" className={group}>
                {themeEntries.map((entry) => (
                  <Command.Item key={entry.label} value={entry.label} keywords={["theme", "appearance", "colour", "color", "mode"]} onSelect={() => { setThemePrefs({ mode: entry.mode }); onOpenChange(false); }} className={item}>
                    <entry.icon aria-hidden="true" />
                    {entry.label}
                  </Command.Item>
                ))}
                <Command.Item value="Appearance settings" keywords={["theme", "accent", "colour", "color"]} onSelect={() => go("/settings?tab=appearance")} className={item}>
                  <Palette aria-hidden="true" />
                  Appearance settings…
                </Command.Item>
              </Command.Group>
              {agents.length ? (
                <Command.Group heading="Agents" className={group}>
                  {agents.map((agent) => (
                    <Command.Item key={agent.id} value={`agent ${agent.name}`} onSelect={() => go(`/agents/${agent.id}`)} className={item}>
                      <Bot aria-hidden="true" />
                      {agent.name}
                    </Command.Item>
                  ))}
                </Command.Group>
              ) : null}
            </Command.List>
            <div className="flex items-center gap-4 border-t border-line px-4 py-2.5 text-[0.7rem] text-fg-muted">
              <span className="flex items-center gap-1.5"><Kbd>↑</Kbd><Kbd>↓</Kbd> navigate</span>
              <span className="flex items-center gap-1.5"><Kbd>↵</Kbd> open</span>
            </div>
          </Command>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
