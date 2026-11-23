"use client";

import "@/styles/console.css";
import "@/styles/console-compat.css";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ComponentType, ReactNode } from "react";
import { useEffect, useState } from "react";
import {
  BarChart3,
  Bot,
  CalendarDays,
  CircleHelp,
  CreditCard,
  Home,
  LogOut,
  Menu,
  MessageSquare,
  PanelLeft,
  Phone,
  Plug,
  Search,
  Settings,
  X,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { NotificationMenu } from "@/features/notifications/presentation/NotificationMenu";
import { loadBillingUsage } from "@/lib/api/billing";
import type { BillingUsage } from "@/types/api";
import { CommandMenu, useCommandMenu } from "@/components/ui/command-menu";
import { isPreviewMode } from "@/lib/preview/mode";
import { cn } from "@/lib/utils";
import { useApplyConsoleTheme, useSyncConsoleThemeWithAccount } from "@/lib/console-theme";
import { loadConsoleAppearance, saveConsoleAppearance } from "@/lib/api/appearance";

const appearanceApi = { load: loadConsoleAppearance, save: saveConsoleAppearance };
import { AppearanceMenu } from "@/components/ui/theme-controls";
import { WorkspaceSwitcher } from "@/features/workspaces/presentation/WorkspaceSwitcher";
import { NoWorkspace } from "@/features/workspaces/presentation/NoWorkspace";

type NavItem = { label: string; href: string; icon: ComponentType<{ className?: string }> };

const workspaceNav: NavItem[] = [
  { label: "Home", href: "/dashboard", icon: Home },
  { label: "Calls", href: "/calls", icon: Phone },
  { label: "Bookings", href: "/bookings", icon: CalendarDays },
  { label: "Inbox", href: "/inbox", icon: MessageSquare },
];

const buildNav: NavItem[] = [
  { label: "Agents", href: "/agents", icon: Bot },
  { label: "Analytics", href: "/analytics", icon: BarChart3 },
  { label: "Integrations", href: "/dashboard/integrations", icon: Plug },
];

const accountNav: NavItem[] = [
  { label: "Usage & billing", href: "/billing", icon: CreditCard },
  { label: "Settings", href: "/settings", icon: Settings },
  { label: "Help", href: "/help", icon: CircleHelp },
];

const allNav = [...workspaceNav, ...buildNav, ...accountNav];

function isActive(pathname: string, href: string) {
  if (href === "/dashboard") return pathname === "/dashboard";
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { session, logout } = useAuth();
  const tenant = session?.tenant;
  const commandMenu = useCommandMenu();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [billingUsage, setBillingUsage] = useState<BillingUsage | null>(null);
  const [shortcut, setShortcut] = useState("Ctrl K");
  const isAgentStudio = pathname === "/agents/new" || /^\/agents\/[^/]+$/.test(pathname);
  const current = allNav.find((item) => isActive(pathname, item.href));

  // Theme the whole document so portaled menus, dialogs and toasts match the console.
  useApplyConsoleTheme();
  useSyncConsoleThemeWithAccount(Boolean(session) && !isPreviewMode(), appearanceApi);

  useEffect(() => {
    if (/Mac|iPhone|iPad/.test(navigator.platform)) setShortcut("⌘K");
    try {
      setCollapsed(window.localStorage.getItem("sauti-sidebar-collapsed") === "true");
    } catch {
      // Storage may be unavailable; keep the default.
    }
  }, []);

  useEffect(() => setMobileOpen(false), [pathname]);

  // Minutes and whether AI calls are paused; refreshed on each page so they follow recent calls.
  useEffect(() => {
    if (!session?.tenant) return;
    let active = true;
    loadBillingUsage()
      .then((usage) => { if (active) setBillingUsage(usage); })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [session?.tenant, pathname]);

  function toggleCollapsed() {
    setCollapsed((value) => {
      try {
        window.localStorage.setItem("sauti-sidebar-collapsed", String(!value));
      } catch {
        // Ignore storage failures.
      }
      return !value;
    });
  }

  function handleLogout() {
    logout();
    router.replace("/login");
  }

  const minutesUsed = billingUsage?.minutesUsedThisCycle ?? 0;
  const minutesLimit = billingUsage?.monthlyMinutesLimit ?? 0;
  const usage = minutesLimit ? Math.min(100, Math.round((minutesUsed / minutesLimit) * 100)) : 0;
  const paidCallingBlocked = Boolean(billingUsage?.accessBlocked);
  const outOfMinutes = billingUsage?.blockedReason === "over_limit";
  const messagesUsed = billingUsage?.messagesUsed ?? 0;
  const messagesLimit = billingUsage?.messagesIncluded ?? 0;
  // Unpaid already pauses everything and has its own banner.
  const outOfMessages = billingUsage?.messagesBlockedReason === "messages_over_limit";

  const sidebar = (compact: boolean) => (
    <div className="flex h-full flex-col gap-5 px-3 pb-3 pt-3">
      <div className={cn("flex items-center gap-2", compact && "flex-col")}>
        <WorkspaceSwitcher compact={compact} />
        <button
          type="button"
          onClick={toggleCollapsed}
          className="hidden size-7 shrink-0 cursor-pointer place-items-center rounded-[6px] border-0 bg-transparent p-0 text-fg-muted transition-colors hover:bg-tint/[0.06] hover:text-fg lg:grid"
          aria-label={compact ? "Expand sidebar" : "Collapse sidebar"}
          title={compact ? "Expand sidebar" : "Collapse sidebar"}
        >
          <PanelLeft className="size-4" />
        </button>
      </div>

      <button
        type="button"
        onClick={() => commandMenu.setOpen(true)}
        className={cn(
          "flex h-8 cursor-pointer items-center gap-2 rounded-[7px] border border-line bg-surface px-2.5 text-left font-sans text-[0.82rem] text-fg-muted shadow-[var(--shadow-card)] transition-colors hover:border-line-strong hover:text-fg-soft",
          compact && "justify-center px-0",
        )}
        aria-label="Search pages, agents and actions"
      >
        <Search className="size-3.5 shrink-0" />
        {!compact ? (
          <>
            <span className="flex-1">Search</span>
            <kbd className="font-sans text-[0.7rem] text-fg-muted">{shortcut}</kbd>
          </>
        ) : null}
      </button>

      <nav aria-label="Primary" className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto">
        <NavGroup items={workspaceNav} pathname={pathname} compact={compact} />
        <NavGroup label="Build" items={buildNav} pathname={pathname} compact={compact} />
        <div className="mt-auto flex flex-col gap-4">
          {!compact && minutesLimit ? (
            <Link href="/billing" className="block rounded-[8px] border border-line bg-surface px-3 py-2.5 no-underline transition-colors hover:border-line-strong">
              <span className="flex items-baseline justify-between text-[0.75rem]">
                <span className="text-fg-soft">{billingUsage?.trial ? "Trial minutes" : "Minutes this cycle"}</span>
                <span className="tabular-nums text-fg">{usage}%</span>
              </span>
              <span className="mt-2 block h-1 overflow-hidden rounded-full bg-surface-3">
                <span className={cn("block h-full rounded-full", usage >= 90 ? "bg-danger" : "bg-signal")} style={{ width: `${usage}%` }} />
              </span>
              <span className="mt-1.5 block text-[0.72rem] tabular-nums text-fg-muted">
                {minutesUsed.toLocaleString("en-US")} of {minutesLimit.toLocaleString("en-US")} min
              </span>
              {messagesLimit ? (
                <span className={cn("block text-[0.72rem] tabular-nums", outOfMessages ? "text-danger" : "text-fg-muted")}>
                  {messagesUsed.toLocaleString("en-US")} of {messagesLimit.toLocaleString("en-US")} messages
                </span>
              ) : null}
            </Link>
          ) : null}
          <NavGroup items={accountNav} pathname={pathname} compact={compact} />
          <button
            type="button"
            onClick={handleLogout}
            className={cn(
              "flex h-8 cursor-pointer items-center gap-2.5 rounded-[7px] border-0 bg-transparent px-2 font-sans text-[0.84rem] text-fg-soft transition-colors hover:bg-tint/[0.05] hover:text-fg",
              compact && "justify-center px-0",
            )}
            title="Log out"
          >
            <LogOut className="size-4 shrink-0" />
            {!compact ? "Log out" : null}
          </button>
        </div>
      </nav>
    </div>
  );

  return (
    <div className="flex min-h-dvh bg-canvas font-sans text-fg antialiased">
      {/* Desktop sidebar */}
      <aside
        className={cn(
          "sticky top-0 hidden h-dvh shrink-0 transition-[width] duration-200 ease-out lg:block",
          collapsed ? "w-[60px]" : "w-[236px]",
        )}
      >
        {sidebar(collapsed)}
      </aside>

      {/* Mobile sidebar */}
      {mobileOpen ? (
        <div className="fixed inset-0 z-[120] lg:hidden">
          <button type="button" aria-label="Close navigation" className="absolute inset-0 cursor-default border-0 bg-scrim p-0" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-[272px] border-r border-line bg-canvas shadow-[var(--shadow-pop)]">
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              className="absolute right-3 top-3.5 grid size-7 cursor-pointer place-items-center rounded-[6px] border-0 bg-transparent p-0 text-fg-muted hover:bg-tint/[0.06]"
              aria-label="Close navigation"
            >
              <X className="size-4" />
            </button>
            {sidebar(false)}
          </aside>
        </div>
      ) : null}

      <div className="console-main flex min-w-0 flex-1 flex-col lg:py-2 lg:pr-2">
        <div className="flex min-h-0 flex-1 flex-col border-line bg-surface lg:rounded-[10px] lg:border lg:shadow-[var(--shadow-card)]">
          <header className="sticky top-0 z-30 flex h-12 shrink-0 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur lg:rounded-t-[10px] lg:px-6">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="grid size-8 cursor-pointer place-items-center rounded-[7px] border-0 bg-transparent p-0 text-fg-soft hover:bg-tint/[0.05] lg:hidden"
              aria-label="Open navigation"
            >
              <Menu className="size-[18px]" />
            </button>
            <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1.5 text-[0.84rem]">
              <span className="hidden truncate text-fg-muted sm:inline">{tenant?.businessName ?? "Workspace"}</span>
              <span className="hidden text-line-strong sm:inline">/</span>
              <span className="truncate font-medium text-ink">{current?.label ?? (isAgentStudio ? "Agent" : "Console")}</span>
            </nav>
            <div className="ml-auto flex items-center gap-1.5">
              {isPreviewMode() ? (
                <span className="hidden rounded-[5px] border border-warning/30 bg-warning/[0.08] px-2 py-0.5 text-[0.7rem] font-medium text-warning sm:inline" title="Dev preview: sample data, no login">
                  Sample data
                </span>
              ) : null}
              <button
                type="button"
                onClick={() => commandMenu.setOpen(true)}
                className="grid size-8 cursor-pointer place-items-center rounded-[7px] border-0 bg-transparent p-0 text-fg-soft hover:bg-tint/[0.05] lg:hidden"
                aria-label="Search"
              >
                <Search className="size-4" />
              </button>
              <AppearanceMenu />
              <NotificationMenu />
              <Link
                href={paidCallingBlocked ? "/billing" : "/agents/new"}
                className="inline-flex h-8 items-center gap-1.5 rounded-[7px] border border-line-strong bg-surface px-3 text-[0.8rem] font-medium text-ink no-underline transition-colors hover:bg-surface-2"
              >
                {paidCallingBlocked ? <CreditCard className="size-3.5" /> : <Phone className="size-3.5" />}
                {paidCallingBlocked ? "Restore calling" : "Test call"}
              </Link>
            </div>
          </header>

          {paidCallingBlocked ? (
            <div role="alert" className="flex flex-wrap items-center gap-3 border-b border-warning/25 bg-warning/[0.07] px-4 py-2.5 text-[0.84rem] lg:px-6">
              <span className="font-medium text-ink">AI calling is paused.</span>
              <span className="text-fg-soft">
                {outOfMinutes
                  ? billingUsage?.trial
                    ? `The ${minutesLimit} trial minutes are used. Choose a plan to take new calls.`
                    : `All ${minutesLimit.toLocaleString("en-US")} minutes for this cycle are used. Upgrade to take new calls before ${formatCycleEnd(billingUsage?.cycleEndsAt)}.`
                  : "Agents and history stay available. Reactivate a plan to place new calls."}
              </span>
              <Link href="/billing" className="ml-auto font-medium text-ink underline underline-offset-4">Review billing</Link>
            </div>
          ) : null}

          {!paidCallingBlocked && outOfMessages ? (
            <div role="status" className="flex flex-wrap items-center gap-3 border-b border-warning/25 bg-warning/[0.07] px-4 py-2.5 text-[0.84rem] lg:px-6">
              <span className="font-medium text-ink">Automatic messages are paused.</span>
              <span className="text-fg-soft">
                {billingUsage?.trial
                  ? `The ${messagesLimit} trial messages are used. AI replies and texts stop until you choose a plan; your team can still reply from the inbox.`
                  : `All ${messagesLimit.toLocaleString("en-US")} messages for this cycle are used. AI replies and texts resume on ${formatCycleEnd(billingUsage?.cycleEndsAt)}; your team can still reply from the inbox.`}
              </span>
              <Link href="/billing" className="ml-auto font-medium text-ink underline underline-offset-4">Review billing</Link>
            </div>
          ) : null}

          <main className={cn("console-content min-w-0 flex-1", !isAgentStudio && "!w-full !max-w-[1280px] !px-4 !py-6 sm:!px-6 lg:!px-8 lg:!py-8")}>
            {session && !session.tenant && !isPreviewMode() ? <NoWorkspace /> : children}
          </main>
        </div>
      </div>
      <CommandMenu open={commandMenu.open} onOpenChange={commandMenu.setOpen} />
    </div>
  );
}

function NavGroup({ label, items, pathname, compact }: { label?: string; items: NavItem[]; pathname: string; compact: boolean }) {
  return (
    <div className="flex flex-col gap-px">
      {label && !compact ? <span className="px-2 pb-1 text-[0.72rem] font-medium text-fg-muted">{label}</span> : null}
      {items.map(({ label: itemLabel, href, icon: Icon }) => {
        const active = isActive(pathname, href);
        return (
          <Link
            key={href}
            href={href}
            title={compact ? itemLabel : undefined}
            aria-current={active ? "page" : undefined}
            className={cn(
              "flex h-8 items-center gap-2.5 rounded-[7px] border px-2 text-[0.84rem] no-underline transition-colors",
              active
                ? "border-line bg-surface font-medium text-ink shadow-[var(--shadow-card)]"
                : "border-transparent text-fg-soft hover:bg-tint/[0.05] hover:text-fg",
              compact && "justify-center px-0",
            )}
          >
            <Icon className={cn("size-4 shrink-0", active ? "text-ink" : "text-fg-muted")} />
            {!compact ? itemLabel : null}
          </Link>
        );
      })}
    </div>
  );
}

/** "1 Dec", the day the next cycle's minutes start. */
function formatCycleEnd(value: string | null | undefined) {
  if (!value) return "the next cycle";
  return new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short" }).format(new Date(value));
}
