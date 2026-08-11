"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { BarChart3, Building2, ChevronDown, ChevronRight, ClipboardList, CreditCard, LayoutDashboard, LogOut, MessageSquareText, ShieldCheck, UserRound, Users } from "lucide-react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import { useAuth } from "@/hooks/useAuth";
import styles from "./AdminShell.module.css";

const links = [
  { href: "/admin", label: "Overview", icon: LayoutDashboard },
  { href: "/admin/demo-requests", label: "Demo requests", icon: MessageSquareText },
  { href: "/admin/workspaces", label: "Workspaces", icon: Building2 },
  { href: "/admin/customers", label: "Customers", icon: Users },
  { href: "/admin/analytics", label: "Analytics", icon: BarChart3 },
  { href: "/admin/billing", label: "Billing readiness", icon: CreditCard },
  { href: "/admin/audit", label: "Audit history", icon: ClipboardList },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { session, ready, logout } = useAuth();

  if (!ready) return <main className={styles.loading}>Opening Sauti Admin…</main>;
  if (!session || session.role !== "PLATFORM_ADMIN") {
    return (
      <main className={styles.denied}>
        <ShieldCheck size={34} />
        <h1>Platform administrator access required</h1>
        <p>This area is separate from business workspaces and customer-facing operations.</p>
        <Link href="/dashboard">Return to workspace</Link>
      </main>
    );
  }

  function signOut() {
    logout();
    router.replace("/login");
  }

  return (
    <main className={styles.shell}>
      <aside className={styles.sidebar}>
        <Link className={styles.brand} href="/admin"><BrandLogo /><strong>Sauti Admin</strong></Link>
        <div className={styles.operator}><ShieldCheck size={18} /><span><strong>Platform operations</strong><small>Restricted access</small></span><ChevronRight className={styles.operatorArrow} size={18}/></div>
        <nav aria-label="Sauti administration">
          <span>Control center</span>
          {links.map(({ href, label, icon: Icon }) => (
            <Link className={pathname === href ? styles.active : ""} href={href} key={href}><Icon size={18} />{label}</Link>
          ))}
        </nav>
        <button className={styles.logout} onClick={signOut} type="button"><LogOut size={18} />Log out</button>
      </aside>
      <section className={styles.main}>
        <header>
          <div><span>SAUTI PLATFORM</span><strong>Operations console</strong></div>
          <div className={styles.identity}><span><UserRound size={18}/></span><strong>{session.tenant.email}</strong><ChevronDown size={16}/></div>
        </header>
        <div className={styles.content}>{children}</div>
      </section>
    </main>
  );
}
