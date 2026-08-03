"use client";

import type { CSSProperties } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  BellRing,
  Bot,
  CalendarDays,
  Check,
  CircleDollarSign,
  Clock3,
  CreditCard,
  FileText,
  Gauge,
  Info,
  LoaderCircle,
  Minus,
  Phone,
  Plus,
  ReceiptText,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Sparkles,
  UsersRound,
  X,
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { pricingPlans, type PricingPlanId } from "@/features/marketing/Pricing/domain/pricing-model";
import { createBillingCheckout, loadBillingAccount, loadBillingUsage } from "@/lib/api/billing";
import type { BillingAccount, BillingUsage } from "@/types/api";
import {
  billingAddOns,
  billingTabs,
  buildModelledUsageSeries,
  estimateForecast,
  money,
  planById,
  projectBilling,
  resolvePlan,
  type BillingAddOnId,
  type BillingInterval,
  type BillingTab,
} from "../domain/billing-preview";
import styles from "./BillingPage.module.css";

const emptyUsage: BillingUsage = {
  plan: "launch",
  status: "preview",
  monthlyMinutesLimit: 100,
  minutesUsedThisCycle: 0,
  remainingMinutes: 100,
  usagePercent: 0,
  limitReached: false,
};

type LimitPolicy = "cap" | "pause" | "approval";

export function BillingPage() {
  const [activeTab, setActiveTab] = useState<BillingTab>("overview");
  const [usage, setUsage] = useState<BillingUsage>(emptyUsage);
  const [account, setAccount] = useState<BillingAccount | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedPlanId, setSelectedPlanId] = useState<PricingPlanId>("growth");
  const [interval, setInterval] = useState<BillingInterval>("monthly");
  const [projectedMinutes, setProjectedMinutes] = useState(900);
  const [quantities, setQuantities] = useState<Partial<Record<BillingAddOnId, number>>>({ agent: 1 });
  const [policy, setPolicy] = useState<LimitPolicy>("cap");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [checkoutError, setCheckoutError] = useState("");
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const queryTab = new URLSearchParams(window.location.search).get("tab");
    if (billingTabs.some((tab) => tab.id === queryTab)) setActiveTab(queryTab as BillingTab);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([loadBillingUsage(), loadBillingAccount()])
      .then(([response, billingAccount]) => {
        if (cancelled) return;
        setUsage(response);
        setAccount(billingAccount);
        const current = resolvePlan(response);
        setSelectedPlanId(current.id);
        setProjectedMinutes(Math.max(Math.round(current.includedMinutes * 1.2), estimateForecast(response.minutesUsedThisCycle, response.monthlyMinutesLimit)));
      })
      .catch((caught) => {
        if (!cancelled) setError(caught instanceof Error ? caught.message : "Unable to load billing usage.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  useEffect(() => {
    if (previewOpen) {
      setCheckoutError("");
      setCheckoutLoading(false);
      closeButtonRef.current?.focus();
    }
  }, [previewOpen]);

  const currentPlan = useMemo(() => resolvePlan(usage), [usage]);
  const selectedPlan = useMemo(() => planById(selectedPlanId), [selectedPlanId]);
  const forecast = useMemo(
    () => estimateForecast(usage.minutesUsedThisCycle, Math.max(usage.monthlyMinutesLimit, currentPlan.includedMinutes)),
    [currentPlan.includedMinutes, usage.minutesUsedThisCycle, usage.monthlyMinutesLimit],
  );
  const currentProjection = useMemo(
    () => projectBilling(currentPlan, forecast, "monthly", {}),
    [currentPlan, forecast],
  );
  const modelProjection = useMemo(
    () => projectBilling(selectedPlan, projectedMinutes, interval, quantities),
    [interval, projectedMinutes, quantities, selectedPlan],
  );
  const includedMinutes = Math.max(1, usage.monthlyMinutesLimit || currentPlan.includedMinutes);
  const remainingMinutes = Math.max(0, includedMinutes - usage.minutesUsedThisCycle);
  const usedPercent = Math.min(100, Math.round((usage.minutesUsedThisCycle / includedMinutes) * 100));
  const forecastPercent = Math.min(100, Math.round((forecast / includedMinutes) * 100));
  const resetDate = new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" }).format(
    new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0),
  );

  function switchTab(tab: BillingTab) {
    setActiveTab(tab);
    const url = new URL(window.location.href);
    if (tab === "overview") url.searchParams.delete("tab");
    else url.searchParams.set("tab", tab);
    window.history.replaceState(null, "", url);
  }

  function updateQuantity(id: BillingAddOnId, next: number) {
    setQuantities((current) => ({ ...current, [id]: Math.max(0, next) }));
  }

  function resetModel() {
    setSelectedPlanId(currentPlan.id);
    setInterval("monthly");
    setProjectedMinutes(Math.max(currentPlan.includedMinutes, forecast));
    setQuantities({ agent: 1 });
  }

  async function startCheckout() {
    setCheckoutLoading(true);
    setCheckoutError("");
    try {
      const checkout = await createBillingCheckout(selectedPlan.id, interval);
      window.location.assign(checkout.url);
    } catch (caught) {
      setCheckoutError(caught instanceof Error ? caught.message : "Secure checkout is temporarily unavailable.");
      setCheckoutLoading(false);
    }
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <span>Workspace billing</span>
          <h1>Usage &amp; billing</h1>
          <p>Understand usage, forecast costs, and test plan controls before billing goes live.</p>
        </div>
        <button className={styles.explainButton} onClick={() => switchTab("invoices")} type="button">
          <Info size={16} /> How billing works
        </button>
      </header>

      <nav className={styles.tabs} aria-label="Billing sections">
        {billingTabs.map((tab) => (
          <button className={activeTab === tab.id ? styles.activeTab : ""} key={tab.id} onClick={() => switchTab(tab.id)} type="button">
            {tab.label}
          </button>
        ))}
      </nav>

      <section className={styles.previewNotice} aria-label="Billing preview status">
        <Info size={18} />
        <div><strong>Billing preview — no charges are being made</strong><span>Your current plan, agents, and calls will not change.</span></div>
        <em>Testing mode</em>
      </section>

      {error && (
        <section className={styles.errorState} role="alert">
          <AlertTriangle size={19} />
          <div><strong>Live usage is temporarily unavailable</strong><span>{error} The pricing modeller still works without changing your workspace.</span></div>
          <button onClick={() => setReloadKey((key) => key + 1)} type="button"><RefreshCw size={15} /> Retry</button>
        </section>
      )}

      {loading ? <BillingLoading /> : (
        <>
          {activeTab === "overview" && (
            <OverviewTab
              currentPlanName={currentPlan.name}
              currentPlanPrice={currentPlan.monthlyPrice}
              account={account}
              forecast={forecast}
              forecastPercent={forecastPercent}
              includedMinutes={includedMinutes}
              policy={policy}
              projection={currentProjection}
              remainingMinutes={remainingMinutes}
              resetDate={resetDate}
              setPolicy={setPolicy}
              switchTab={switchTab}
              usedMinutes={usage.minutesUsedThisCycle}
              usedPercent={usedPercent}
            />
          )}
          {activeTab === "usage" && (
            <UsageTab
              forecast={forecast}
              includedMinutes={includedMinutes}
              resetDate={resetDate}
              usedMinutes={usage.minutesUsedThisCycle}
              switchTab={switchTab}
            />
          )}
          {activeTab === "plans" && (
            <PlansTab
              currentPlanId={currentPlan.id}
              interval={interval}
              projection={modelProjection}
              projectedMinutes={projectedMinutes}
              quantities={quantities}
              resetModel={resetModel}
              selectedPlanId={selectedPlanId}
              setInterval={setInterval}
              setPreviewOpen={setPreviewOpen}
              setProjectedMinutes={setProjectedMinutes}
              setSelectedPlanId={setSelectedPlanId}
              updateQuantity={updateQuantity}
            />
          )}
          {activeTab === "invoices" && <InvoicesTab switchTab={switchTab} />}
        </>
      )}

      {previewOpen && (
        <div className={styles.dialogBackdrop} onMouseDown={(event) => event.target === event.currentTarget && setPreviewOpen(false)}>
          <section aria-describedby="preview-description" aria-labelledby="preview-title" aria-modal="true" className={styles.dialog} role="dialog">
            <button aria-label="Close preview" className={styles.dialogClose} onClick={() => setPreviewOpen(false)} ref={closeButtonRef} type="button"><X size={18} /></button>
            <span className={styles.dialogIcon}><ShieldCheck size={23} /></span>
            <small>Secure hosted checkout</small>
            <h2 id="preview-title">Review before checkout</h2>
            <p id="preview-description">Continuing opens our secure merchant-of-record checkout. Your plan changes only after you confirm payment there and Sauti receives a signed subscription event.</p>
            <div className={styles.dialogRows}>
              <span><em>Plan</em><strong>{selectedPlan.name}</strong></span>
              <span><em>Projected usage</em><strong>{projectedMinutes.toLocaleString()} minutes</strong></span>
              <span><em>Estimated monthly total</em><strong>{money(modelProjection.total)}</strong></span>
            </div>
            {checkoutError && <p className={styles.checkoutError} role="alert"><AlertTriangle size={15} /> {checkoutError}</p>}
            <button className={styles.dialogDone} disabled={checkoutLoading} onClick={startCheckout} type="button">
              {checkoutLoading ? <LoaderCircle className="spin" size={16} /> : <CreditCard size={16} />}
              {checkoutLoading ? "Opening secure checkout…" : "Continue to secure checkout"}
            </button>
            <button className={styles.checkoutCancel} disabled={checkoutLoading} onClick={() => setPreviewOpen(false)} type="button">Keep exploring</button>
          </section>
        </div>
      )}
    </main>
  );
}

function OverviewTab({
  account,
  currentPlanName,
  currentPlanPrice,
  forecast,
  forecastPercent,
  includedMinutes,
  policy,
  projection,
  remainingMinutes,
  resetDate,
  setPolicy,
  switchTab,
  usedMinutes,
  usedPercent,
}: {
  account: BillingAccount | null;
  currentPlanName: string;
  currentPlanPrice: number;
  forecast: number;
  forecastPercent: number;
  includedMinutes: number;
  policy: LimitPolicy;
  projection: ReturnType<typeof projectBilling>;
  remainingMinutes: number;
  resetDate: string;
  setPolicy: (policy: LimitPolicy) => void;
  switchTab: (tab: BillingTab) => void;
  usedMinutes: number;
  usedPercent: number;
}) {
  const meterStyle = { "--usage-width": `${usedPercent}%`, "--forecast-left": `${forecastPercent}%` } as CSSProperties;
  const eighty = Math.round(includedMinutes * 0.8);
  return (
    <section className={styles.overview} aria-label="Billing overview">
      <article className={styles.usageHero}>
        <div className={styles.planSummary}>
          <span><BarChart3 size={20} /></span><small>Current plan</small><h2>{currentPlanName} plan</h2><strong>{money(currentPlanPrice)}<em>/month</em></strong>
        </div>
        <div className={styles.meterSummary}>
          <small>AI minutes</small>
          <h2>{usedMinutes.toLocaleString()} <span>of {includedMinutes.toLocaleString()} used</span></h2>
          <div aria-label={`${usedMinutes} of ${includedMinutes} AI minutes used`} aria-valuemax={includedMinutes} aria-valuemin={0} aria-valuenow={Math.min(usedMinutes, includedMinutes)} className={styles.meter} role="progressbar" style={meterStyle}>
            <i /><b><span>{forecast.toLocaleString()}</span></b>
          </div>
          <footer><span><i />{usedMinutes.toLocaleString()} used</span><span><i />{remainingMinutes.toLocaleString()} remaining</span><span><i />{forecast.toLocaleString()} forecast</span></footer>
        </div>
        <div className={styles.remainingSummary}>
          <strong>{remainingMinutes.toLocaleString()}</strong><span>minutes remaining</span><small><CalendarDays size={16} /> Preview cycle ends {resetDate}</small>
        </div>
      </article>

      {account && <CommunicationCostOverview account={account} />}

      <div className={styles.overviewGrid}>
        <article className={styles.costLedger}>
          <header><div className={styles.cardTitle}><CircleDollarSign size={18} /><h2>Projected cost breakdown</h2></div><Info size={18} /></header>
          <LedgerRow label="Base plan" note={`${currentPlanName} plan`} value={money(projection.basePrice)} />
          <LedgerRow label="Projected overage" note={`${projection.overageMinutes.toLocaleString()} minutes`} value={money(projection.overageCost)} />
          <LedgerRow label="Activated add-ons" note="None in preview" value={money(0)} />
          <div className={styles.ledgerTotal}><div><strong>Estimated total</strong><span>Plan + overage + activated add-ons</span></div><strong>{money(projection.total)}</strong></div>
          <p><Info size={14} /> All monetary amounts are modelled estimates.</p>
          <button onClick={() => switchTab("plans")} type="button">Compare plans <ArrowRight size={15} /></button>
        </article>

        <article className={styles.alertTimeline}>
          <header><div className={styles.cardTitle}><BellRing size={18} /><h2>Billing cycle alerts</h2></div></header>
          <div className={usedMinutes >= eighty ? styles.complete : ""}><i><span>80%</span></i><span><strong>80% usage</strong><small>{eighty.toLocaleString()} minutes</small><p>{usedMinutes >= eighty ? "Threshold reached. No action was taken." : `We will flag the preview at ${eighty.toLocaleString()} minutes.`}</p></span></div>
          <div className={usedMinutes >= includedMinutes ? styles.complete : ""}><i><span>100%</span></i><span><strong>100% allowance</strong><small>{includedMinutes.toLocaleString()} minutes</small><p>{usedMinutes >= includedMinutes ? "Calls are continuing while billing is in preview." : "Calls will continue during testing even after this point."}</p></span></div>
          <div><i><CalendarDays size={19} /></i><span><strong>Preview cycle resets</strong><small>{resetDate}</small><p>No real subscription renewal is created.</p></span></div>
        </article>
      </div>

      <article className={styles.policyCard}>
        <header><div><small>Testing only</small><h2>Model your future limit behaviour</h2><p>These choices are not saved and cannot pause agents or calls.</p></div><ShieldCheck size={23} /></header>
        <div className={styles.policyOptions}>
          <PolicyOption active={policy === "cap"} icon={Check} label="Continue to a cap" note="Recommended for service continuity" onClick={() => setPolicy("cap")} />
          <PolicyOption active={policy === "pause"} icon={Phone} label="Pause new AI calls" note="Never interrupt an active call" onClick={() => setPolicy("pause")} />
          <PolicyOption active={policy === "approval"} icon={UsersRound} label="Require approval" note="Notify an owner before more usage" onClick={() => setPolicy("approval")} />
        </div>
      </article>
    </section>
  );
}

function CommunicationCostOverview({ account }: { account: BillingAccount }) {
  const confirmed = account.costTotals.filter((total) => total.costBasis === "provider_confirmed");
  const estimated = account.costTotals.filter((total) => total.costBasis === "rate_card" || total.costBasis === "provider_quote");
  const awaiting = account.reconciliation.pending + account.reconciliation.retrying;
  const hasWarning = account.reconciliation.unavailable > 0 || account.unpricedUsage.length > 0;

  return (
    <article className={styles.communicationCosts} aria-label="Communication cost tracking">
      <header>
        <div>
          <small>Provider cost evidence</small>
          <h2>Communication costs</h2>
          <p>This cycle&apos;s provider costs are tracked separately from customer invoices and plan forecasts.</p>
        </div>
        <span className={account.enforcementMode === "observe" ? styles.observeBadge : styles.enforceBadge}>
          {account.enforcementMode === "observe" ? "Observe only" : "Enforcement active"}
        </span>
      </header>

      <div className={styles.costEvidenceGrid}>
        <CostEvidenceCard label="Provider confirmed" totals={confirmed} empty="No confirmed costs yet" />
        <CostEvidenceCard label="Estimated or quoted" totals={estimated} empty="No estimates recorded" />
        <div className={styles.evidenceCard}>
          <span>Reconciliation</span>
          <strong>{awaiting.toLocaleString()}</strong>
          <small>awaiting provider evidence</small>
        </div>
        <div className={hasWarning ? `${styles.evidenceCard} ${styles.warningEvidence}` : styles.evidenceCard}>
          <span>Needs attention</span>
          <strong>{(account.reconciliation.unavailable + account.unpricedUsage.length).toLocaleString()}</strong>
          <small>unavailable prices or unpriced usage groups</small>
        </div>
      </div>

      {(account.unpricedUsage.length > 0 || account.reconciliation.unavailable > 0) && (
        <div className={styles.costWarning} role="status">
          <AlertTriangle size={17} />
          <div>
            <strong>Some usage does not have a final provider price yet</strong>
            <span>
              {account.unpricedUsage.map((item) => `${usageCategory(item.category)}: ${formatQuantity(item.quantity)} ${item.unit}${item.quantity === 1 ? "" : "s"}`).join(" · ")}
              {account.reconciliation.unavailable > 0 ? ` · ${account.reconciliation.unavailable} reconciliation ${account.reconciliation.unavailable === 1 ? "job" : "jobs"} unavailable` : ""}
            </span>
          </div>
        </div>
      )}

      <footer>
        <span><Check size={15} /> {account.reconciliation.reconciled.toLocaleString()} provider-confirmed</span>
        <span><Clock3 size={15} /> {account.reconciliation.estimated.toLocaleString()} rate-card estimates</span>
        <span><Info size={15} /> Charging remains {account.enforcementMode === "observe" ? "disabled" : "enabled"}</span>
      </footer>
    </article>
  );
}

function CostEvidenceCard({ label, totals, empty }: {
  label: string;
  totals: BillingAccount["costTotals"];
  empty: string;
}) {
  return (
    <div className={styles.evidenceCard}>
      <span>{label}</span>
      {totals.length === 0 ? <strong>—</strong> : totals.map((total) => (
        <strong key={`${total.costBasis}-${total.currency}`}>{providerMoney(total.amount, total.currency)}</strong>
      ))}
      <small>{totals.length === 0 ? empty : "net provider cost this cycle"}</small>
    </div>
  );
}

function providerMoney(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat("en", { style: "currency", currency, minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(4)}`;
  }
}

function usageCategory(category: string) {
  return ({ voice_call: "Voice calls", sms_message: "SMS", whatsapp_message: "WhatsApp" } as Record<string, string>)[category]
    ?? category.replaceAll("_", " ");
}

function formatQuantity(quantity: number) {
  return new Intl.NumberFormat("en", { maximumFractionDigits: 4 }).format(quantity);
}

function UsageTab({ forecast, includedMinutes, resetDate, usedMinutes, switchTab }: { forecast: number; includedMinutes: number; resetDate: string; usedMinutes: number; switchTab: (tab: BillingTab) => void }) {
  const actual = buildModelledUsageSeries(usedMinutes);
  const chartData = [
    ...actual.map((item, index) => ({ ...item, forecast: index === actual.length - 1 ? item.actual : null })),
    { date: resetDate.replace(/, \d{4}/, ""), actual: null, forecast },
  ];
  const eighty = Math.round(includedMinutes * 0.8);
  const chartMaximum = Math.max(100, Math.ceil(Math.max(includedMinutes, forecast) / 50) * 50);
  const projection = projectBilling(planById("growth"), forecast, "monthly", { agent: 1 });
  return (
    <section className={styles.usageWorkbench} aria-label="Usage forecasting workbench">
      <p className={styles.contextLine}><Info size={15} /> Aggregate minutes are live. The daily curve and monetary amounts are clearly modelled until detailed metering is available.</p>
      <div className={styles.workbenchGrid}>
        <article className={styles.chartPanel}>
          <header><div><small>Modelled distribution</small><h2>AI-minute usage</h2><p>Current preview cycle</p></div><div><button type="button">All agents</button><button type="button">All channels</button></div></header>
          <div className={styles.chartLegend}><span><i />Aggregate</span><span><i />Forecast</span><span><i />Allowance</span><span><i />80% threshold</span></div>
          <div className={styles.chart}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 18, right: 28, bottom: 4, left: 0 }}>
                <CartesianGrid stroke="rgba(120,168,207,.12)" strokeDasharray="3 4" vertical={false} />
                <XAxis dataKey="date" axisLine={false} tickLine={false} minTickGap={32} />
                <YAxis axisLine={false} tickLine={false} width={42} domain={[0, chartMaximum]} />
                <Tooltip contentStyle={{ background: "#06172a", border: "1px solid rgba(93,157,207,.28)", borderRadius: 12 }} />
                <ReferenceLine y={includedMinutes} stroke="#458bff" strokeDasharray="8 6" label={{ value: `Allowance ${includedMinutes}`, fill: "#70a5ff", position: "insideTopRight" }} />
                <ReferenceLine y={eighty} stroke="#f5a524" strokeDasharray="8 6" label={{ value: `80% ${eighty}`, fill: "#f5b64d", position: "insideBottomRight" }} />
                <Area dataKey="actual" fill="#18b7aa" fillOpacity={0.16} stroke="#2ee6d5" strokeWidth={3} type="monotone" />
                <Area dataKey="forecast" fill="none" stroke="#2ee6d5" strokeDasharray="7 7" strokeWidth={3} type="monotone" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <footer><Info size={14} /> Daily attribution is illustrative; only the aggregate {usedMinutes.toLocaleString()}-minute total comes from the current billing endpoint.</footer>
          <div className={styles.breakdownUnavailable}>
            <span><Bot size={18} /></span><div><strong>Per-agent usage is coming with shadow metering</strong><p>We will show tenant-scoped agent and channel rows here only when the durable ledger can support them.</p></div>
          </div>
        </article>
        <article className={styles.stickyLedger}>
          <header><small>Estimate</small><h2>Projected bill</h2><p>Preview cycle ends {resetDate}</p></header>
          <LedgerRow label="Growth plan" note="Up to 750 AI minutes" value="$149" />
          <LedgerRow label="Projected overage" note={`${projection.overageMinutes} minutes`} value={money(projection.overageCost)} />
          <LedgerRow label="Additional agent" note="1 modelled add-on" value="$29" />
          <div className={styles.ledgerTotal}><div><strong>Estimated total</strong><span>Plan + overage + activated add-ons</span></div><strong>{money(projection.total)}</strong></div>
          <button onClick={() => switchTab("plans")} type="button">Model another plan <ArrowRight size={15} /></button>
          <button className={styles.textButton} onClick={() => switchTab("invoices")} type="button">How usage is calculated</button>
        </article>
      </div>
    </section>
  );
}

function PlansTab({ currentPlanId, interval, projection, projectedMinutes, quantities, resetModel, selectedPlanId, setInterval, setPreviewOpen, setProjectedMinutes, setSelectedPlanId, updateQuantity }: {
  currentPlanId: PricingPlanId;
  interval: BillingInterval;
  projection: ReturnType<typeof projectBilling>;
  projectedMinutes: number;
  quantities: Partial<Record<BillingAddOnId, number>>;
  resetModel: () => void;
  selectedPlanId: PricingPlanId;
  setInterval: (value: BillingInterval) => void;
  setPreviewOpen: (value: boolean) => void;
  setProjectedMinutes: (value: number) => void;
  setSelectedPlanId: (value: PricingPlanId) => void;
  updateQuantity: (id: BillingAddOnId, value: number) => void;
}) {
  const selectedPlan = planById(selectedPlanId);
  return (
    <section className={styles.planStudio} aria-label="Plan and add-on modeller">
      <article className={styles.planSelector}>
        <header><small>Preview different plans</small><h2>Select a plan</h2></header>
        <div className={styles.intervalToggle} role="group" aria-label="Billing interval">
          <button aria-pressed={interval === "monthly"} className={interval === "monthly" ? styles.selected : ""} onClick={() => setInterval("monthly")} type="button">Monthly</button>
          <button aria-pressed={interval === "annual"} className={interval === "annual" ? styles.selected : ""} onClick={() => setInterval("annual")} type="button">Annual</button>
          <span>Save 10%</span>
        </div>
        <div className={styles.planRows}>
          {pricingPlans.map((plan) => (
            <button aria-pressed={selectedPlanId === plan.id} className={selectedPlanId === plan.id ? styles.activePlan : ""} key={plan.id} onClick={() => setSelectedPlanId(plan.id)} type="button">
              <span><strong>{plan.name}{currentPlanId === plan.id && <em>Current</em>}</strong><small>{plan.includedMinutes.toLocaleString()} AI minutes / month</small><small>{plan.concurrentCalls} concurrent {plan.concurrentCalls === 1 ? "line" : "lines"}</small></span>
              <strong className={styles.planPrice}>{money(interval === "annual" ? plan.monthlyPrice * 0.9 : plan.monthlyPrice)}<em>/ month</em></strong>
              {selectedPlanId === plan.id && <Check className={styles.planCheck} size={15} />}
            </button>
          ))}
        </div>
        <p>Need more than {pricingPlans[2].concurrentCalls} lines? <a href="https://cal.com/sauti/demo">Contact sales</a></p>
      </article>

      <article className={styles.configurator}>
        <header><small>Safe model</small><h2>Configure your setup</h2><p>Adjust the model to see how each choice affects the estimate.</p></header>
        <label className={styles.minutesInput}><span><Clock3 size={18} /><strong>Projected AI minutes</strong></span><input max={20000} min={0} onChange={(event) => setProjectedMinutes(Math.max(0, Number(event.target.value) || 0))} type="number" value={projectedMinutes} /><small>Includes {selectedPlan.includedMinutes.toLocaleString()} minutes. Projected overage: {projection.overageMinutes.toLocaleString()} minutes.</small></label>
        <QuantityControl description="A separate workflow, department, language, or location." label="Additional agents" onChange={(value) => updateQuantity("agent", value)} value={quantities.agent ?? 0} />
        <div className={styles.addOnList}><h3>Add-ons <span>(optional)</span></h3><p>Only active items are included in your estimate.</p>
          {billingAddOns.filter((item) => item.id !== "agent").map((addOn) => {
            const active = (quantities[addOn.id] ?? 0) > 0;
            return <div key={addOn.id}><span>{addOn.id === "line" ? <Phone size={17} /> : addOn.id === "number" ? <CreditCard size={17} /> : addOn.id === "voice" ? <Sparkles size={17} /> : <FileText size={17} />}</span><div><strong>{addOn.name}</strong><small>{addOn.description}</small></div><button aria-label={`${active ? "Remove" : "Add"} ${addOn.name}`} aria-pressed={active} className={active ? styles.toggleActive : ""} onClick={() => updateQuantity(addOn.id, active ? 0 : 1)} type="button"><i /></button><em>from {money(addOn.monthlyPrice)} /mo</em></div>;
          })}
        </div>
      </article>

      <article className={styles.estimateSummary}>
        <header><small>All amounts in USD</small><h2>Estimate summary</h2></header>
        <LedgerRow label={`Base plan — ${selectedPlan.name}`} note={interval === "annual" ? "Annual billing equivalent" : "Monthly billing"} value={money(projection.basePrice)} />
        <LedgerRow label="Overage minutes" note={`${projection.overageMinutes.toLocaleString()} × ${money(selectedPlan.overageRate)}`} value={money(projection.overageCost)} />
        <LedgerRow label="Activated add-ons" note="Modelled selection" value={money(projection.addOnCost)} />
        <div className={styles.ledgerTotal}><div><strong>Estimated total / month</strong><span>Plan + overage + activated add-ons</span></div><strong>{money(projection.total)}</strong></div>
        <p><Info size={15} /> If a higher plan produces a lower total for your model, Sauti will explain the arithmetic instead of forcing a recommendation.</p>
        <button onClick={() => setPreviewOpen(true)} type="button"><ShieldCheck size={16} /> Preview this setup</button>
        <button className={styles.resetButton} onClick={resetModel} type="button"><RotateCcw size={15} /> Reset model</button>
        <footer>This preview makes no API mutation and cannot change your current plan or calls.</footer>
      </article>
    </section>
  );
}

function InvoicesTab({ switchTab }: { switchTab: (tab: BillingTab) => void }) {
  return (
    <section className={styles.invoices} aria-label="Invoices preview">
      <article className={styles.invoiceIntro}>
        <span><ReceiptText size={27} /></span><small>Financial records</small><h2>Invoices will appear after billing is activated</h2><p>Sauti has not created a subscription, charged a payment method, or issued a real invoice for this workspace.</p>
        <button onClick={() => switchTab("plans")} type="button">Model a future bill <ArrowRight size={15} /></button>
      </article>
      <article className={styles.invoiceRules}>
        <header><small>What to expect</small><h2>Clear records without surprises</h2></header>
        <div><ShieldCheck size={18} /><span><strong>Verified provider records only</strong><p>Invoice numbers, receipts, and taxes will come from the connected merchant of record.</p></span></div>
        <div><CircleDollarSign size={18} /><span><strong>Actual and estimated stay separate</strong><p>Forecasts never appear as completed financial transactions.</p></span></div>
        <div><FileText size={18} /><span><strong>Sandbox invoices stay labelled Test</strong><p>Payment controls remain hidden until the processor is connected safely.</p></span></div>
      </article>
    </section>
  );
}

function LedgerRow({ label, note, value }: { label: string; note: string; value: string }) {
  return <div className={styles.ledgerRow}><div><strong>{label}</strong><span>{note}</span></div><strong>{value}</strong></div>;
}

function PolicyOption({ active, icon: Icon, label, note, onClick }: { active: boolean; icon: typeof Gauge; label: string; note: string; onClick: () => void }) {
  return <button aria-pressed={active} className={active ? styles.policyActive : ""} onClick={onClick} type="button"><span>{active ? <Check size={16} /> : <Icon size={17} />}</span><strong>{label}</strong><small>{note}</small></button>;
}

function QuantityControl({ description, label, onChange, value }: { description: string; label: string; onChange: (value: number) => void; value: number }) {
  return <div className={styles.quantityControl}><div><UsersRound size={18} /><span><strong>{label}</strong><small>{description}</small></span></div><div><button aria-label={`Decrease ${label}`} disabled={value === 0} onClick={() => onChange(value - 1)} type="button"><Minus size={15} /></button><strong>{value}</strong><button aria-label={`Increase ${label}`} onClick={() => onChange(value + 1)} type="button"><Plus size={15} /></button></div></div>;
}

function BillingLoading() {
  return <section className={styles.loading}><LoaderCircle className="spin" size={22} /><strong>Loading billing preview</strong><span>Reading your current aggregate usage…</span></section>;
}
