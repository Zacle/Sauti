"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { LoaderCircle, Phone, Search, X } from "lucide-react";
import { getAdminCustomer, getAdminCustomers } from "@/lib/api/admin";
import type { AdminCustomer, AdminCustomerDetail } from "@/types/api";
import styles from "./AdminDirectory.module.css";

export function AdminCustomers() {
  const [customers, setCustomers] = useState<AdminCustomer[]>([]);
  const [query, setQuery] = useState("");
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<AdminCustomerDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async (search = "") => {
    setLoading(true); setError("");
    try { const result = await getAdminCustomers(search); setCustomers(result.customers); setTotal(result.total); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to load customers."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  function search(event: FormEvent) { event.preventDefault(); void load(query); }
  async function open(customer: AdminCustomer) {
    setError("");
    try { setSelected(await getAdminCustomer(customer.tenantId, customer.phone)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Unable to load customer details."); }
  }

  return <div className={styles.page}>
    <div className={styles.heading}><div><span>CUSTOMER DIRECTORY</span><h1>Customers</h1><p>Customer identities remain separated by workspace, even when the same phone number contacts multiple businesses.</p></div></div>
    <form className={styles.searchBar} onSubmit={search}><Search size={17}/><input aria-label="Search customers" onChange={(event) => setQuery(event.target.value)} placeholder="Search phone number or workspace" value={query}/><button type="submit">Search</button></form>
    {error && <div className={styles.error} role="alert">{error}</div>}
    <div className={styles.listHeader}><strong>{total} customers</strong><span>Most recent contact first</span></div>
    {loading ? <div className={styles.loadingState}><LoaderCircle className={styles.spin} size={22}/>Loading customers…</div> : <section className={styles.dataList}>
      {customers.map((customer) => <button key={`${customer.tenantId}:${customer.phone}`} onClick={() => void open(customer)} type="button">
        <span className={styles.avatar}><Phone size={16}/></span><span className={styles.primary}><strong>{customer.phone}</strong><small>{customer.businessName}</small></span><span><small>CALLS</small><strong>{customer.calls}</strong></span><span><small>LAST CONTACT</small><strong>{new Date(customer.lastContactAt).toLocaleDateString()}</strong></span>
      </button>)}
      {!customers.length && <div className={styles.empty}>No customers match this search.</div>}
    </section>}
    {selected && <CustomerDetail customer={selected} onClose={() => setSelected(null)}/>} 
  </div>;
}

function CustomerDetail({ customer, onClose }: { customer: AdminCustomerDetail; onClose: () => void }) {
  return <div className={styles.detailBackdrop} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <aside className={styles.detailPanel} aria-label={`${customer.phone} details`}>
      <button className={styles.closeDetail} onClick={onClose} type="button"><X size={18}/><span className={styles.srOnly}>Close</span></button>
      <span className={styles.detailKicker}>CUSTOMER DETAIL</span><h2>{customer.phone}</h2><p>{customer.businessName} · {customer.calls} total calls</p>
      <h3>Recent calls</h3><section className={styles.timeline}>{customer.recentCalls.map((call) => <article key={call.id}><span/><div><strong>{call.agentName}</strong><small>{call.direction} · {call.outcome}{call.language ? ` · ${call.language}` : ""}</small></div><time>{new Date(call.startedAt).toLocaleString()}</time></article>)}</section>
      <p className={styles.readOnlyNote}>Only calls from this workspace are shown. This platform view is read-only.</p>
    </aside>
  </div>;
}
