"use client";

import "@/styles/console.css";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import {
  BarChart3,
  Bell,
  Bot,
  CalendarDays,
  ChevronDown,
  CircleHelp,
  CreditCard,
  LayoutDashboard,
  LogOut,
  Menu,
  PhoneCall,
  Plug,
  Search,
  Settings,
  Sparkles,
  X,
} from "lucide-react";
import { useState } from "react";
import { useAuth } from "@/hooks/useAuth";

const navigation = [
  { label: "Overview", href: "/dashboard", icon: LayoutDashboard },
  { label: "Agents", href: "/agents", icon: Bot },
  { label: "Calls", href: "/calls", icon: PhoneCall },
  { label: "Bookings", href: "/bookings", icon: CalendarDays },
  { label: "Analytics", href: "/analytics", icon: BarChart3 },
  { label: "Integrations", href: "/dashboard/integrations", icon: Plug },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);
  const { session, logout } = useAuth();
  const tenant = session?.tenant;

  function handleLogout() {
    logout();
    router.replace("/login");
  }

  return (
    <main className="console-shell">
      <aside className={`console-sidebar ${mobileOpen ? "open" : ""}`}>
        <div className="console-sidebar-head">
          <Link className="console-brand" href="/dashboard">
            <span className="brand-mark">S</span><strong>Sauti</strong>
          </Link>
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
          <Link href="/billing"><CreditCard size={18} /> Usage & billing</Link>
          <Link href="/settings"><Settings size={18} /> Settings</Link>
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
            <button className="top-icon-button" aria-label="Notifications"><Bell size={18} /></button>
            <Link className="test-agent-button" href="/agents/new"><Sparkles size={16} /> Test agent</Link>
            <span className="profile-avatar">{tenant?.businessName?.slice(0, 1).toUpperCase() ?? "S"}</span>
          </div>
        </header>
        <div className="console-content">{children}</div>
      </section>
    </main>
  );
}
