"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, Building2, CalendarCheck, MessageSquareText, PhoneCall, Users } from "lucide-react";
import { getAdminOverview } from "@/lib/api/admin";
import type { AdminOverview as Overview } from "@/types/api";
import styles from "./AdminViews.module.css";

export function AdminOverview() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState("");

  useEffect(() => { getAdminOverview().then(setData).catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to load platform data.")); }, []);
  const metrics = [
    ["Workspaces", data?.workspaces, Building2], ["Customers", data?.customers, Users],
    ["Calls", data?.calls, PhoneCall], ["Bookings", data?.bookings, CalendarCheck],
  ] as const;

  return <div className={styles.page}>
    <div className={styles.heading}><div><span>PLATFORM OVERVIEW</span><h1>Good decisions start with the whole picture.</h1><p>Monitor acquisition and platform activity without entering a tenant workspace.</p></div></div>
    {error && <div className={styles.error} role="alert">{error}</div>}
    <section className={styles.metrics}>{metrics.map(([label, value, Icon]) => <article key={label}><span><Icon size={18}/></span><small>{label}</small><strong>{data ? value?.toLocaleString() : "—"}</strong></article>)}</section>
    <section className={styles.leadCard}>
      <div><span><MessageSquareText size={18}/></span><div><small>PILOT PIPELINE</small><h2>{data?.newDemoRequests ?? "—"} demo requests need review</h2><p>{data?.invitedDemoRequests ?? 0} invitations awaiting activation · {data?.activatedPilots ?? 0} pilots activated</p></div></div>
      <Link href="/admin/demo-requests">Review requests <ArrowRight size={16}/></Link>
    </section>
  </div>;
}
