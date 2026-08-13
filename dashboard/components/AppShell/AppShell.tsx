"use client";

import "@/styles/console.css";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import {
  BarChart3,
  Bot,
  CalendarDays,
  ChevronDown,
  CircleHelp,
  CreditCard,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageCircle,
  PhoneCall,
  PanelLeftClose,
  PanelLeftOpen,
  Plug,
  Search,
  Settings,
  ShieldAlert,
  Sparkles,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "@/hooks/useAuth";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import { NotificationMenu } from "@/features/notifications/presentation/NotificationMenu";
import { loadBillingAccount } from "@/lib/api/billing";

const navigation = [
  { label: "Overview", href: "/dashboard", icon: LayoutDashboard },
  { label: "Agents", href: "/agents", icon: Bot },
  { label: "Calls", href: "/calls", icon: PhoneCall },
  { label: "Inbox", href: "/inbox", icon: MessageCircle },
  { label: "Bookings", href: "/bookings", icon: CalendarDays },
  { label: "Analytics", href: "/analytics", icon: BarChart3 },
  { label: "Integrations", href: "/dashboard/integrations", icon: Plug },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [paidCallingBlocked, setPaidCallingBlocked] = useState(false);
  const { session, logout } = useAuth();
  const tenant = session?.tenant;
  const isAgentStudio = pathname === "/agents/new" || /^\/agents\/[^/]+$/.test(pathname);

  useEffect(() => {
    const savedPreference = window.localStorage.getItem("sauti-sidebar-collapsed")
      ?? window.localStorage.getItem("sauti-agents-sidebar-collapsed");
    setSidebarCollapsed(savedPreference === "true");
  }, []);

  useEffect(() => {
    if (!session?.tenant) return;
    let active = true;
    loadBillingAccount()
      .then((account) => active && setPaidCallingBlocked(
        account.enforcementMode === "enforce" && !account.paidResourcesAllowed
      ))
      .catch(() => undefined);
    return () => { active = false; };
  }, [session?.tenant]);

  function toggleSidebar() {
    setSidebarCollapsed((collapsed) => {
      const next = !collapsed;
      window.localStorage.setItem("sauti-sidebar-collapsed", String(next));
      return next;
    });
  }

  function handleLogout() {
    logout();
    router.replace("/login");
  }

  return (
    <main className={`console-shell ${["/agents", "/dashboard", "/calls", "/inbox", "/bookings", "/analytics", "/dashboard/integrations", "/billing", "/settings"].includes(pathname) || pathname.startsWith("/agents/") ? "agents-console-shell" : ""} ${isAgentStudio ? "agent-studio-console-shell" : ""} ${sidebarCollapsed ? "sidebar-collapsed" : ""} ${pathname === "/dashboard" ? "dashboard-console-shell" : ""} ${pathname === "/calls" ? "calls-console-shell" : ""} ${pathname === "/inbox" ? "inbox-console-shell" : ""} ${pathname === "/bookings" ? "bookings-console-shell" : ""} ${pathname === "/analytics" ? "analytics-console-shell" : ""} ${pathname === "/dashboard/integrations" ? "integrations-console-shell" : ""} ${pathname === "/billing" ? "billing-console-shell" : ""} ${pathname === "/settings" ? "settings-console-shell" : ""}`}>
      <aside className={`console-sidebar ${mobileOpen ? "open" : ""}`}>
        <div className="console-sidebar-head">
          <Link className="console-brand" href="/dashboard">
            <BrandLogo /><strong>Sauti</strong>
          </Link>
          <button
            className="nav-collapse-toggle"
            type="button"
            onClick={toggleSidebar}
            aria-label={sidebarCollapsed ? "Expand navigation" : "Collapse navigation"}
            aria-pressed={sidebarCollapsed}
            title={sidebarCollapsed ? "Expand navigation" : "Collapse navigation"}
          >
            {sidebarCollapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
          </button>
          <button className="mobile-close" onClick={() => setMobileOpen(false)} aria-label="Close navigation"><X size={19} /></button>
        </div>
        <button className="workspace-switcher" type="button">
          <span>{tenant?.businessName?.slice(0, 1).toUpperCase() ?? "S"}</span>
          <div><strong>{tenant?.businessName ?? "Sauti workspace"}</strong><small>{tenant?.plan ?? "Starter"} plan</small></div>
          <ChevronDown size={16} />
        </button>
        <nav className="console-nav" aria-label="Primary navigation">
          <span>Workspace</span>
          {navigation.map(({ label, href, icon: Icon }) => {
            const active = href === "/dashboard" ? pathname === href : pathname.startsWith(href);
            return <Link className={active ? "active" : ""} href={href} key={href}><Icon size={18} />{label}</Link>;
          })}
        </nav>
        <div className="console-sidebar-footer">
          <Link className={pathname === "/billing" ? "active" : ""} href="/billing"><CreditCard size={18} /> Usage & billing</Link>
          <Link className={pathname === "/settings" ? "active" : ""} href="/settings"><Settings size={18} /> Settings</Link>
          <Link href="/help"><CircleHelp size={18} /> Help center</Link>
          <button type="button" onClick={handleLogout}><LogOut size={18} /> Log out</button>
        </div>
      </aside>

      {mobileOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
      <section className="console-main">
        <header className="console-topbar">
          <button className="mobile-menu" onClick={() => setMobileOpen(true)} aria-label="Open navigation"><Menu size={20} /></button>
          <label className="console-search"><Search size={17} /><input aria-label="Search workspace" placeholder="Search calls, agents, bookings..." /><kbd>⌘ K</kbd></label>
          <div className="console-top-actions">
            <NotificationMenu />
            <Link className="test-agent-button" href={paidCallingBlocked ? "/billing" : "/agents/new"}>
              {paidCallingBlocked ? <CreditCard size={16} /> : <Sparkles size={16} />}
              {paidCallingBlocked ? "Restore calling" : "Test agent"}
            </Link>
            <span className="profile-avatar">{tenant?.businessName?.slice(0, 1).toUpperCase() ?? "S"}</span>
          </div>
        </header>
        {paidCallingBlocked && (
          <section className="billing-access-banner" role="alert">
            <ShieldAlert size={18} />
            <div><strong>AI calling is paused</strong><span>Your workspace remains available, including agents and call history. Reactivate a plan to start new browser, web, inbound, or outbound calls.</span></div>
            <Link href="/billing">Review billing</Link>
          </section>
        )}
        <div className="console-content">{children}</div>
      </section>
    </main>
  );
}
