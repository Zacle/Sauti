"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ArrowRight,
  Check,
  Clock3,
  CreditCard,
  FileText,
  LoaderCircle,
  Minus,
  Phone,
  Plus,
  ReceiptText,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  SlidersHorizontal,
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
import { cancelBillingSubscription, createBillingAddOnCheckout, createBillingCheckout, loadBillingAccount, loadBillingCheckoutStatus, loadBillingUsage, requestBillingPlanChange, resumeBillingSubscription } from "@/lib/api/billing";
import type { BillingAccount, BillingCheckoutStatus, BillingUsage } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/field";
import { Badge, Card, Skeleton } from "@/components/ui/primitives";
import { Dialog, DialogContent } from "@/components/ui/overlays";
import { PageHeader, SectionTitle } from "@/components/ui/console";
import { cn } from "@/lib/utils";
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
type Projection = ReturnType<typeof projectBilling>;

const mediumDate = new Intl.DateTimeFormat("en", { dateStyle: "medium" });
const formatDate = (value: string | null | undefined, fallback: string) => (value ? mediumDate.format(new Date(value)) : fallback);

export function BillingPage() {
  const [activeTab, setActiveTab] = useState<BillingTab>("overview");
  const [usage, setUsage] = useState<BillingUsage>(emptyUsage);
  const [account, setAccount] = useState<BillingAccount | null>(null);
  const [checkoutStatus, setCheckoutStatus] = useState<BillingCheckoutStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedPlanId, setSelectedPlanId] = useState<PricingPlanId>("growth");
  const [interval, setInterval] = useState<BillingInterval>("monthly");
  const [projectedMinutes, setProjectedMinutes] = useState(900);
  const [quantities, setQuantities] = useState<Partial<Record<BillingAddOnId, number>>>({});
  const [policy, setPolicy] = useState<LimitPolicy>("cap");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [managementIntent, setManagementIntent] = useState<"change" | "cancel">("change");
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [checkoutError, setCheckoutError] = useState("");
  const [billingActionMessage, setBillingActionMessage] = useState("");
  const [billingActionError, setBillingActionError] = useState("");
  const [resumeLoading, setResumeLoading] = useState(false);
  const [addOnCheckoutLoading, setAddOnCheckoutLoading] = useState<BillingAddOnId | null>(null);
  const [addOnCheckoutError, setAddOnCheckoutError] = useState("");

  useEffect(() => {
    const queryTab = new URLSearchParams(window.location.search).get("tab");
    if (billingTabs.some((tab) => tab.id === queryTab)) setActiveTab(queryTab as BillingTab);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([loadBillingUsage(), loadBillingAccount(), loadBillingCheckoutStatus().catch(() => null)])
      .then(([response, billingAccount, providerStatus]) => {
        if (cancelled) return;
        setUsage(response);
        setAccount(billingAccount);
        setQuantities(Object.fromEntries(
          (billingAccount.addOns ?? []).map((item) => [item.addOn, Math.max(1, item.quantity)]),
        ) as Partial<Record<BillingAddOnId, number>>);
        setCheckoutStatus(providerStatus);
        const current = resolvePlan(response);
        setSelectedPlanId(current.id);
        if (billingAccount.subscription?.interval) setInterval(billingAccount.subscription.interval);
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
    }
  }, [previewOpen]);

  useEffect(() => {
    function resetReturnedCheckout() {
      setCheckoutLoading(false);
      setAddOnCheckoutLoading(null);
    }
    function resetWhenVisible() {
      if (document.visibilityState === "visible") resetReturnedCheckout();
    }
    window.addEventListener("pageshow", resetReturnedCheckout);
    window.addEventListener("focus", resetReturnedCheckout);
    document.addEventListener("visibilitychange", resetWhenVisible);
    return () => {
      window.removeEventListener("pageshow", resetReturnedCheckout);
      window.removeEventListener("focus", resetReturnedCheckout);
      document.removeEventListener("visibilitychange", resetWhenVisible);
    };
  }, []);

  const currentPlan = useMemo(() => resolvePlan(usage), [usage]);
  const selectedPlan = useMemo(() => planById(selectedPlanId), [selectedPlanId]);
  const forecast = useMemo(
    () => estimateForecast(usage.minutesUsedThisCycle, Math.max(usage.monthlyMinutesLimit, currentPlan.includedMinutes)),
    [currentPlan.includedMinutes, usage.minutesUsedThisCycle, usage.monthlyMinutesLimit],
  );
  const currentProjection = useMemo(() => projectBilling(currentPlan, forecast, "monthly", quantities), [currentPlan, forecast, quantities]);
  const modelProjection = useMemo(() => projectBilling(selectedPlan, projectedMinutes, interval, quantities), [interval, projectedMinutes, quantities, selectedPlan]);
  const includedMinutes = Math.max(1, usage.monthlyMinutesLimit || currentPlan.includedMinutes);
  const remainingMinutes = Math.max(0, includedMinutes - usage.minutesUsedThisCycle);
  const resetDate = new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" }).format(
    new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0),
  );
  const isSandbox = checkoutStatus?.environment === "sandbox";
  const checkoutConfigured = checkoutStatus?.configured !== false;
  const checkoutProvider = checkoutStatus?.provider === "whop" ? "Whop" : checkoutStatus?.provider ?? "the billing provider";
  const hasPaidSubscription = subscriptionAllowsCalls(account?.subscription ?? null);
  const managing = hasPaidSubscription && Boolean(account?.subscription);
  const currentMembershipPrice = account?.subscription?.interval === "annual"
    ? `${money(currentPlan.monthlyPrice * 0.9 * 12)} per year`
    : `${money(currentPlan.monthlyPrice)} per month`;

  function switchTab(tab: BillingTab) {
    setActiveTab(tab);
    const url = new URL(window.location.href);
    if (tab === "overview") url.searchParams.delete("tab");
    else url.searchParams.set("tab", tab);
    window.history.replaceState(null, "", url);
  }

  function resetModel() {
    setSelectedPlanId(currentPlan.id);
    setInterval("monthly");
    setProjectedMinutes(Math.max(currentPlan.includedMinutes, forecast));
    setQuantities(Object.fromEntries(
      (account?.addOns ?? []).map((item) => [item.addOn, Math.max(1, item.quantity)]),
    ) as Partial<Record<BillingAddOnId, number>>);
  }

  function openPlanDialog(intent: "change" | "cancel") {
    setManagementIntent(intent);
    setPreviewOpen(true);
  }

  async function startCheckout() {
    setCheckoutLoading(true);
    setCheckoutError("");
    try {
      if (hasPaidSubscription && account?.subscription && managementIntent === "cancel") {
        await cancelBillingSubscription();
        setPreviewOpen(false);
        setBillingActionMessage("Cancellation scheduled. Your access remains available through the current paid period.");
        setReloadKey((key) => key + 1);
        return;
      }
      if (hasPaidSubscription && account?.subscription) {
        if (account.pendingPlanChange) {
          setPreviewOpen(false);
          setBillingActionMessage(
            `${planById(account.pendingPlanChange.targetPlan).name} is already pending for ${formatDate(account.pendingPlanChange.effectiveAt, "the end of the current paid period")}. Wait for that change to complete before requesting another one.`,
          );
          setCheckoutLoading(false);
          return;
        }
        const change = await requestBillingPlanChange(selectedPlan.id, interval);
        setPreviewOpen(false);
        setBillingActionMessage(change.status === "completed"
          ? `${selectedPlan.name} was already active in Whop and is now your synchronized Sauti plan.`
          : change.collectionMethod === "send_invoice"
            ? `Your current plan stays active for the rest of its paid period. Whop will then email a secure ${selectedPlan.name} invoice; the new plan activates only after that invoice is paid.`
            : `${selectedPlan.name} will take effect only after your current subscription ends.`);
        setReloadKey((key) => key + 1);
        return;
      }
      const checkout = await createBillingCheckout(selectedPlan.id, interval);
      window.location.assign(checkout.url);
    } catch (caught) {
      setCheckoutError(caught instanceof Error ? caught.message : "Secure checkout is temporarily unavailable.");
      setCheckoutLoading(false);
    }
  }

  async function startAddOnCheckout(addOn: BillingAddOnId) {
    setAddOnCheckoutLoading(addOn);
    setAddOnCheckoutError("");
    try {
      const checkout = await createBillingAddOnCheckout(addOn);
      window.location.assign(checkout.url);
    } catch (caught) {
      setAddOnCheckoutError(caught instanceof Error ? caught.message : "Add-on checkout is temporarily unavailable.");
      setAddOnCheckoutLoading(null);
    }
  }

  async function resumeSubscription() {
    setResumeLoading(true);
    setBillingActionError("");
    try {
      await resumeBillingSubscription();
      setBillingActionMessage("Renewal resumed. Whop will keep the current plan active for the next billing period.");
      setReloadKey((key) => key + 1);
    } catch (caught) {
      setBillingActionError(caught instanceof Error ? caught.message : "Renewal could not be resumed.");
    } finally {
      setResumeLoading(false);
    }
  }

  const providerState = !checkoutConfigured
    ? { label: "Setup required", tone: "warning" as const, text: "Add the server-only Whop credentials and plan IDs before testing checkout." }
    : isSandbox
      ? { label: "Sandbox", tone: "info" as const, text: "Whop sandbox checkout is on. Use test payment details; no real money moves." }
      : { label: "Live billing", tone: "success" as const, text: `Checkout opens in ${checkoutProvider}. Sauti never handles card details.` };

  return (
    <div className="grid min-w-0 grid-cols-1 gap-6 [&>*]:min-w-0">
      <PageHeader
        title="Usage & billing"
        description="See what you've used this cycle, what it will cost, and change your plan."
        actions={<Badge tone={providerState.tone} dot>{providerState.label}</Badge>}
      />

      <nav aria-label="Billing sections" className="-mb-2 flex gap-5 overflow-x-auto border-b border-line">
        {billingTabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            aria-current={activeTab === tab.id ? "page" : undefined}
            onClick={() => switchTab(tab.id)}
            className={cn(
              "-mb-px cursor-pointer whitespace-nowrap border-0 border-b-2 bg-transparent px-0 pb-2.5 pt-1 font-sans text-[0.86rem] font-medium transition-colors",
              activeTab === tab.id ? "border-ink text-ink" : "border-transparent text-fg-soft hover:text-fg",
            )}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <p className="m-0 flex items-center gap-2 text-[0.8rem] text-fg-muted"><ShieldCheck className="size-3.5 shrink-0" /> {providerState.text}</p>

      {billingActionMessage ? (
        <div role="status" className="flex items-start gap-3 rounded-[8px] border border-success/30 bg-success/[0.06] px-3.5 py-3 text-[0.85rem] text-fg">
          <Check className="mt-0.5 size-4 shrink-0 text-success" />
          <span className="flex-1">{billingActionMessage}</span>
          <button aria-label="Dismiss message" onClick={() => setBillingActionMessage("")} type="button" className="grid size-6 cursor-pointer place-items-center rounded-[5px] border-0 bg-transparent p-0 text-fg-muted hover:text-fg"><X className="size-3.5" /></button>
        </div>
      ) : null}
      {billingActionError ? <Alert tone="error">Billing action failed: {billingActionError}</Alert> : null}

      {account?.pendingPlanChange ? <PendingChange change={account.pendingPlanChange} /> : null}

      {error ? (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-[8px] border border-warning/30 bg-warning/[0.06] px-3.5 py-3 text-[0.85rem]">
          <AlertTriangle className="size-4 shrink-0 text-warning" />
          <span className="flex-1 text-fg"><span className="font-medium">Live usage is unavailable.</span> <span className="text-fg-soft">{error} You can still model plans below.</span></span>
          <Button variant="secondary" size="xs" onClick={() => setReloadKey((key) => key + 1)}><RefreshCw /> Retry</Button>
        </div>
      ) : null}

      {loading ? <BillingLoading /> : (
        <>
          {activeTab === "overview" ? (
            <OverviewTab
              account={account}
              currentPlanName={currentPlan.name}
              currentPlanPrice={currentPlan.monthlyPrice}
              forecast={forecast}
              includedMinutes={includedMinutes}
              policy={policy}
              projection={currentProjection}
              remainingMinutes={remainingMinutes}
              renewsAt={account?.subscription?.renewsAt ?? null}
              resetDate={resetDate}
              setPolicy={setPolicy}
              switchTab={switchTab}
              usedMinutes={usage.minutesUsedThisCycle}
            />
          ) : null}
          {activeTab === "usage" ? (
            <UsageTab forecast={forecast} includedMinutes={includedMinutes} resetDate={resetDate} usedMinutes={usage.minutesUsedThisCycle} switchTab={switchTab} />
          ) : null}
          {activeTab === "plans" ? (
            <PlansTab
              activeAddOns={account?.addOns ?? []}
              addOnCheckoutError={addOnCheckoutError}
              addOnCheckoutLoading={addOnCheckoutLoading}
              addOnsConfigured={checkoutStatus?.addOnsConfigured === true}
              checkoutConfigured={checkoutConfigured}
              checkoutProvider={checkoutProvider}
              currentPlanId={currentPlan.id}
              hasSubscription={hasPaidSubscription}
              interval={interval}
              isSandbox={isSandbox}
              openPlanDialog={openPlanDialog}
              pendingPlanChange={account?.pendingPlanChange ?? null}
              projection={modelProjection}
              projectedMinutes={projectedMinutes}
              resetModel={resetModel}
              resumeLoading={resumeLoading}
              resumeSubscription={resumeSubscription}
              selectedPlanId={selectedPlanId}
              setInterval={setInterval}
              setProjectedMinutes={setProjectedMinutes}
              setSelectedPlanId={setSelectedPlanId}
              startAddOnCheckout={startAddOnCheckout}
              subscription={account?.subscription ?? null}
            />
          ) : null}
          {activeTab === "invoices" ? <InvoicesTab switchTab={switchTab} /> : null}
        </>
      )}

      <Dialog open={previewOpen} onOpenChange={(open) => { if (!checkoutLoading) setPreviewOpen(open); }}>
        <DialogContent
          title={managing
            ? managementIntent === "cancel" ? `Cancel your ${currentPlan.name} plan?` : `Change ${currentPlan.name} to ${selectedPlan.name}`
            : `Review your ${isSandbox ? "test " : ""}checkout`}
          description={managing && account?.subscription
            ? managementIntent === "cancel"
              ? `You keep access until ${formatDate(account.subscription.renewsAt, "the end of the paid billing period")}. Sauti cancels the exact synchronized Whop membership.`
              : `${currentPlan.name} stays active until ${formatDate(account.subscription.renewsAt, "the end of its paid period")}. ${selectedPlan.name} starts after that. If Whop can't collect automatically, it emails a secure invoice that must be paid first.`
            : `Continuing opens ${checkoutProvider}. ${isSandbox ? "Use Whop's test payment details; no real money moves. " : ""}Your plan changes only after Sauti receives a signed subscription event.`}
        >
          <dl className="m-0 divide-y divide-line rounded-[8px] border border-line">
            <DialogRow label={managing && managementIntent === "change" ? "New plan" : "Plan"} value={managing && managementIntent === "cancel" ? currentPlan.name : selectedPlan.name} />
            {managing && managementIntent === "cancel" && account?.subscription ? (
              <>
                <DialogRow label="Current price" value={currentMembershipPrice} />
                <DialogRow label="Access ends" value={formatDate(account.subscription.renewsAt, "End of billing period")} />
              </>
            ) : (
              <>
                {managing && account?.subscription ? (
                  <DialogRow label="Takes effect" value={account.subscription.renewsAt ? `After ${mediumDate.format(new Date(account.subscription.renewsAt))}` : "After the current period"} />
                ) : null}
                <DialogRow label="Projected usage" value={`${projectedMinutes.toLocaleString()} minutes`} />
                <DialogRow label="Estimated monthly total" value={money(modelProjection.total)} />
              </>
            )}
          </dl>
          {checkoutError ? <Alert tone="error" className="mt-4">{checkoutError}</Alert> : null}
          <div className="mt-6 flex flex-wrap justify-end gap-2">
            <Button variant="secondary" size="sm" disabled={checkoutLoading} onClick={() => setPreviewOpen(false)}>Keep exploring</Button>
            <Button size="sm" variant={managing && managementIntent === "cancel" ? "danger" : "primary"} disabled={checkoutLoading} onClick={startCheckout}>
              {checkoutLoading ? <LoaderCircle className="animate-spin" /> : <CreditCard />}
              {checkoutLoading ? "Updating billing…" : managing
                ? managementIntent === "cancel" ? "Schedule cancellation" : "Schedule plan change"
                : isSandbox ? "Continue to Whop sandbox" : "Continue to secure checkout"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function subscriptionAllowsCalls(subscription: BillingAccount["subscription"]) {
  if (!subscription) return false;
  if (["active", "trialing"].includes(subscription.status)) return true;
  if (!["canceling", "canceled", "completed"].includes(subscription.status)) return false;
  return subscription.renewsAt != null && new Date(subscription.renewsAt).getTime() > Date.now();
}

function DialogRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 px-3.5 py-2.5 text-[0.85rem]">
      <dt className="text-fg-soft">{label}</dt>
      <dd className="m-0 text-right font-medium tabular-nums text-ink">{value}</dd>
    </div>
  );
}

function PendingChange({ change }: { change: NonNullable<BillingAccount["pendingPlanChange"]> }) {
  const target = planById(change.targetPlan).name;
  const current = planById(change.currentPlan).name;
  const title = change.collectionMethod === "send_invoice"
    ? "Plan change waiting for renewal payment"
    : change.status === "scheduled" ? `${target} plan change scheduled` : `${target} plan change is being prepared`;
  return (
    <div role="status" className="flex items-start gap-3 rounded-[8px] border border-info/30 bg-info/[0.06] px-3.5 py-3 text-[0.85rem]">
      <Clock3 className="mt-0.5 size-4 shrink-0 text-info" />
      <div>
        <p className="m-0 font-medium text-ink">{title}</p>
        <p className="m-0 mt-0.5 text-fg-soft">
          {current} stays active until {formatDate(change.effectiveAt, "the end of the paid period")}.{" "}
          {change.collectionMethod === "send_invoice"
            ? `After that, Whop emails the ${target} invoice; the new plan starts once it's paid.`
            : `After that, ${target} (${change.targetInterval}) starts automatically.`}{" "}
          Another change can be requested once this one completes.
        </p>
      </div>
    </div>
  );
}

/* ---------------- Overview ---------------- */

function OverviewTab({
  account, currentPlanName, currentPlanPrice, forecast, includedMinutes, policy, projection, remainingMinutes, renewsAt, resetDate, setPolicy, switchTab, usedMinutes,
}: {
  account: BillingAccount | null;
  currentPlanName: string;
  currentPlanPrice: number;
  forecast: number;
  includedMinutes: number;
  policy: LimitPolicy;
  projection: Projection;
  remainingMinutes: number;
  renewsAt: string | null;
  resetDate: string;
  setPolicy: (policy: LimitPolicy) => void;
  switchTab: (tab: BillingTab) => void;
  usedMinutes: number;
}) {
  const eighty = Math.round(includedMinutes * 0.8);
  return (
    <div className="grid gap-8">
      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-start justify-between gap-4 border-b border-line px-5 py-4">
          <div>
            <p className="m-0 text-[0.8rem] text-fg-muted">Current plan</p>
            <p className="m-0 mt-0.5 text-[1.15rem] font-semibold text-ink">
              {currentPlanName} <span className="text-[0.9rem] font-normal text-fg-soft">· {money(currentPlanPrice)}/month</span>
            </p>
            <p className="m-0 mt-1 text-[0.8rem] text-fg-muted">{renewsAt ? `Renews ${mediumDate.format(new Date(renewsAt))}` : `Cycle ends ${resetDate}`}</p>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" size="xs" onClick={() => switchTab("usage")}>View usage</Button>
            <Button size="xs" onClick={() => switchTab("plans")}>Change plan</Button>
          </div>
        </div>
        <div className="px-5 py-5">
          <UsageMeter used={usedMinutes} included={includedMinutes} forecast={forecast} />
          <dl className="m-0 mt-5 grid grid-cols-3 gap-4 text-[0.8rem]">
            <Fact label="Used" value={`${usedMinutes.toLocaleString()} min`} />
            <Fact label="Left" value={`${remainingMinutes.toLocaleString()} min`} />
            <Fact label="Forecast" value={`${forecast.toLocaleString()} min`} hint="by cycle end" />
          </dl>
        </div>
      </Card>

      <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 lg:grid-cols-2">
        <div className="grid content-start gap-3">
          <SectionTitle title="Projected bill" meta="This cycle" action={<TextButton onClick={() => switchTab("plans")}>Compare plans</TextButton>} />
          <Card>
            <Ledger rows={[
              { label: "Base plan", note: `${currentPlanName} plan`, value: money(projection.basePrice) },
              { label: "Overage", note: `${projection.overageMinutes.toLocaleString()} minutes over`, value: money(projection.overageCost) },
              { label: "Add-ons", note: "Active add-ons", value: money(projection.addOnCost) },
            ]} total={money(projection.total)} footnote="Estimate based on your forecast. Actual charges come from your invoice." />
          </Card>
        </div>
        <div className="grid content-start gap-3">
          <SectionTitle title="Usage alerts" />
          <Card>
            <ol className="m-0 grid list-none divide-y divide-line p-0">
              <AlertRow reached={usedMinutes >= eighty} title="80% of minutes" detail={`${eighty.toLocaleString()} minutes`} note={usedMinutes >= eighty ? "Reached. We let you know; nothing was paused." : "We'll let you know when you get here."} />
              <AlertRow reached={usedMinutes >= includedMinutes} title="All included minutes" detail={`${includedMinutes.toLocaleString()} minutes`} note={usedMinutes >= includedMinutes ? "Reached. Calls continue while billing is in preview." : "Calls keep going during testing after this point."} />
              <AlertRow reached={false} title="Cycle resets" detail={resetDate} note="Minutes start again from zero." />
            </ol>
          </Card>
        </div>
      </section>

      {account ? <CommunicationCosts account={account} /> : null}

      <section className="grid gap-3">
        <SectionTitle title="When you run out" meta="Preview, not saved" />
        <div role="radiogroup" aria-label="Limit behaviour" className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <PolicyOption active={policy === "cap"} icon={Check} label="Keep answering up to a cap" note="Best for not missing callers." onClick={() => setPolicy("cap")} />
          <PolicyOption active={policy === "pause"} icon={Phone} label="Pause new AI calls" note="Calls in progress are never cut off." onClick={() => setPolicy("pause")} />
          <PolicyOption active={policy === "approval"} icon={UsersRound} label="Ask an owner first" note="Notify before using more minutes." onClick={() => setPolicy("approval")} />
        </div>
      </section>
    </div>
  );
}

function UsageMeter({ used, included, forecast }: { used: number; included: number; forecast: number }) {
  const usedPercent = Math.min(100, (used / included) * 100);
  const forecastPercent = Math.min(100, (forecast / included) * 100);
  const tone = usedPercent >= 100 ? "bg-danger" : usedPercent >= 80 ? "bg-warning" : "bg-signal";
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <p className="m-0 text-ink">
          <span className="text-[1.6rem] font-medium tabular-nums tracking-[-0.02em]">{used.toLocaleString()}</span>
          <span className="ml-1.5 text-[0.86rem] text-fg-muted">of {included.toLocaleString()} minutes</span>
        </p>
        <span className="text-[0.86rem] tabular-nums text-fg-soft">{Math.round(usedPercent)}%</span>
      </div>
      <div
        role="progressbar"
        aria-label={`${used} of ${included} minutes used`}
        aria-valuemin={0}
        aria-valuemax={included}
        aria-valuenow={Math.min(used, included)}
        className="relative mt-3 h-2.5 rounded-full bg-surface-3"
      >
        <div className="absolute inset-y-0 left-0 rounded-full bg-signal/25" style={{ width: `${forecastPercent}%` }} />
        <div className={cn("absolute inset-y-0 left-0 rounded-full", tone)} style={{ width: `${usedPercent}%` }} />
        <div className="absolute inset-y-[-4px] w-px bg-fg-muted" style={{ left: "80%" }} title="80% alert" />
      </div>
      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[0.75rem] text-fg-muted">
        <span className="inline-flex items-center gap-1.5"><span className={cn("size-2 rounded-full", tone)} />Used</span>
        <span className="inline-flex items-center gap-1.5"><span className="size-2 rounded-full bg-signal/25" />Forecast</span>
        <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-px bg-fg-muted" />80% alert</span>
      </div>
    </div>
  );
}

function Fact({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div>
      <dt className="text-fg-muted">{label}</dt>
      <dd className="m-0 mt-0.5 text-[0.92rem] font-medium tabular-nums text-ink">{value}{hint ? <span className="ml-1 text-[0.75rem] font-normal text-fg-muted">{hint}</span> : null}</dd>
    </div>
  );
}

function Ledger({ rows, total, totalLabel = "Estimated total", footnote }: { rows: Array<{ label: string; note: string; value: string }>; total: string; totalLabel?: string; footnote?: string }) {
  return (
    <div>
      <dl className="m-0 divide-y divide-line">
        {rows.map((row) => (
          <div key={row.label} className="flex items-baseline justify-between gap-4 px-5 py-3">
            <dt className="min-w-0">
              <span className="block text-[0.86rem] text-ink">{row.label}</span>
              <span className="block text-[0.76rem] text-fg-muted">{row.note}</span>
            </dt>
            <dd className="m-0 text-[0.88rem] tabular-nums text-fg">{row.value}</dd>
          </div>
        ))}
      </dl>
      <div className="flex items-baseline justify-between gap-4 border-t border-line-strong bg-surface-2 px-5 py-3.5">
        <span className="text-[0.88rem] font-medium text-ink">{totalLabel}</span>
        <span className="text-[1.15rem] font-semibold tabular-nums text-ink">{total}</span>
      </div>
      {footnote ? <p className="m-0 border-t border-line px-5 py-2.5 text-[0.75rem] text-fg-muted">{footnote}</p> : null}
    </div>
  );
}

function AlertRow({ reached, title, detail, note }: { reached: boolean; title: string; detail: string; note: string }) {
  return (
    <li className="flex items-start gap-3 px-5 py-3.5">
      <span className={cn("mt-1.5 size-2 shrink-0 rounded-full", reached ? "bg-warning" : "bg-line-strong")} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="m-0 flex items-baseline justify-between gap-3 text-[0.86rem] text-ink">
          {title}
          <span className="text-[0.76rem] tabular-nums text-fg-muted">{detail}</span>
        </p>
        <p className="m-0 mt-0.5 text-[0.78rem] text-fg-soft">{note}</p>
      </div>
    </li>
  );
}

function PolicyOption({ active, icon: Icon, label, note, onClick }: { active: boolean; icon: typeof Check; label: string; note: string; onClick: () => void }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      onClick={onClick}
      className={cn(
        "flex cursor-pointer items-start gap-3 rounded-[var(--radius-card)] border bg-surface p-4 text-left font-sans transition-colors",
        active ? "border-signal ring-2 ring-signal/20" : "border-line hover:border-line-strong",
      )}
    >
      <span className={cn("grid size-7 shrink-0 place-items-center rounded-[6px]", active ? "bg-signal text-signal-fg" : "bg-surface-2 text-fg-soft")}><Icon className="size-3.5" /></span>
      <span>
        <span className="block text-[0.86rem] font-medium text-ink">{label}</span>
        <span className="mt-0.5 block text-[0.78rem] text-fg-muted">{note}</span>
      </span>
    </button>
  );
}

function TextButton({ onClick, children }: { onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick} className="group inline-flex cursor-pointer items-center gap-1 border-0 bg-transparent p-0 font-sans text-[0.8rem] text-fg-soft transition-colors hover:text-ink">
      {children} <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
    </button>
  );
}

function CommunicationCosts({ account }: { account: BillingAccount }) {
  const confirmed = account.costTotals.filter((total) => total.costBasis === "provider_confirmed");
  const estimated = account.costTotals.filter((total) => total.costBasis === "rate_card" || total.costBasis === "provider_quote");
  const awaiting = account.reconciliation.pending + account.reconciliation.retrying;
  const attention = account.reconciliation.unavailable + account.unpricedUsage.length;
  return (
    <section className="grid gap-3" aria-label="Communication cost tracking">
      <SectionTitle
        title="Provider costs"
        meta="Phone, SMS and WhatsApp this cycle"
        action={<Badge tone={account.enforcementMode === "observe" ? "neutral" : "success"}>{account.enforcementMode === "observe" ? "Observe only" : "Enforced"}</Badge>}
      />
      <div className="grid grid-cols-2 gap-px overflow-hidden rounded-[var(--radius-card)] border border-line bg-line lg:grid-cols-4">
        <CostCell label="Confirmed by provider" values={confirmed.map((total) => providerMoney(total.amount, total.currency))} note={`${account.reconciliation.reconciled.toLocaleString()} items`} />
        <CostCell label="Estimated" values={estimated.map((total) => providerMoney(total.amount, total.currency))} note={`${account.reconciliation.estimated.toLocaleString()} rate-card items`} />
        <CostCell label="Waiting for provider" values={[awaiting.toLocaleString()]} note="items to reconcile" />
        <CostCell label="Needs attention" values={[attention.toLocaleString()]} note="missing or unpriced" warn={attention > 0} />
      </div>
      {attention > 0 ? (
        <p className="m-0 flex items-start gap-2 text-[0.8rem] text-warning">
          <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
          <span>
            Some usage has no final price yet:{" "}
            {account.unpricedUsage.map((item) => `${usageCategory(item.category)} ${formatQuantity(item.quantity)} ${item.unit}${item.quantity === 1 ? "" : "s"}`).join(" · ")}
            {account.reconciliation.unavailable > 0 ? `${account.unpricedUsage.length ? " · " : ""}${account.reconciliation.unavailable} unavailable` : ""}
          </span>
        </p>
      ) : (
        <p className="m-0 text-[0.78rem] text-fg-muted">These are Sauti&apos;s own costs with telephony providers, kept separate from your invoice.</p>
      )}
    </section>
  );
}

function CostCell({ label, values, note, warn = false }: { label: string; values: string[]; note: string; warn?: boolean }) {
  return (
    <div className="bg-surface px-4 py-3.5">
      <p className="m-0 text-[0.8rem] text-fg-soft">{label}</p>
      {values.length ? values.map((value) => (
        <p key={value} className={cn("m-0 mt-1 text-[1.25rem] font-medium tabular-nums tracking-[-0.01em]", warn ? "text-warning" : "text-ink")}>{value}</p>
      )) : <p className="m-0 mt-1 text-[1.25rem] text-fg-muted">—</p>}
      <p className="m-0 mt-1 text-[0.75rem] text-fg-muted">{note}</p>
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

/* ---------------- Usage ---------------- */

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
    <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 xl:grid-cols-[minmax(0,1fr)_320px]" aria-label="Usage">
      <div className="grid content-start gap-3">
        <SectionTitle title="Minutes this cycle" meta="Cumulative" />
        <Card className="px-4 pb-3 pt-5 sm:px-5">
          <div className="h-[300px] text-[11px] [&_.recharts-cartesian-axis-tick-value]:fill-fg-muted">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 12, right: 12, bottom: 0, left: -8 }}>
                <CartesianGrid stroke="var(--color-line)" vertical={false} />
                <XAxis dataKey="date" axisLine={false} tickLine={false} minTickGap={32} />
                <YAxis axisLine={false} tickLine={false} width={42} domain={[0, chartMaximum]} />
                <Tooltip contentStyle={{ background: "var(--color-popover)", border: "1px solid var(--color-line-strong)", borderRadius: 8, color: "var(--color-fg)", fontSize: 12 }} />
                <ReferenceLine y={includedMinutes} stroke="var(--color-fg-muted)" strokeDasharray="4 4" label={{ value: `Included ${includedMinutes}`, fill: "var(--color-fg-soft)", position: "insideTopRight", fontSize: 11 }} />
                <ReferenceLine y={eighty} stroke="var(--color-warning)" strokeDasharray="4 4" label={{ value: `80% · ${eighty}`, fill: "var(--color-warning)", position: "insideBottomRight", fontSize: 11 }} />
                <Area dataKey="actual" name="Used" fill="var(--color-signal)" fillOpacity={0.12} stroke="var(--color-signal)" strokeWidth={2} type="monotone" />
                <Area dataKey="forecast" name="Forecast" fill="none" stroke="var(--color-signal)" strokeDasharray="5 5" strokeWidth={2} type="monotone" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <p className="m-0 text-[0.78rem] text-fg-muted">
          The {usedMinutes.toLocaleString()}-minute total is live. The day-by-day shape is modelled until per-call metering is available, and per-agent usage will appear here when it is.
        </p>
      </div>
      <div className="grid content-start gap-3">
        <SectionTitle title="Projected bill" meta={`Ends ${resetDate}`} />
        <Card>
          <Ledger rows={[
            { label: "Growth plan", note: "750 minutes included", value: money(projection.basePrice) },
            { label: "Overage", note: `${projection.overageMinutes} minutes`, value: money(projection.overageCost) },
            { label: "Add-ons", note: "Currently active", value: money(projection.addOnCost) },
          ]} total={money(projection.total)} />
        </Card>
        <div className="flex flex-wrap gap-2">
          <Button size="xs" onClick={() => switchTab("plans")}>Model another plan</Button>
          <Button size="xs" variant="ghost" onClick={() => switchTab("invoices")}>How it&apos;s calculated</Button>
        </div>
      </div>
    </section>
  );
}

/* ---------------- Plans ---------------- */

function PlansTab({ activeAddOns, addOnCheckoutError, addOnCheckoutLoading, addOnsConfigured, checkoutConfigured, checkoutProvider, currentPlanId, hasSubscription, interval, isSandbox, openPlanDialog, pendingPlanChange, projection, projectedMinutes, resetModel, resumeLoading, resumeSubscription, selectedPlanId, setInterval, setProjectedMinutes, setSelectedPlanId, startAddOnCheckout, subscription }: {
  activeAddOns: NonNullable<BillingAccount["addOns"]>;
  addOnCheckoutError: string;
  addOnCheckoutLoading: BillingAddOnId | null;
  addOnsConfigured: boolean;
  checkoutConfigured: boolean;
  checkoutProvider: string;
  currentPlanId: PricingPlanId;
  hasSubscription: boolean;
  interval: BillingInterval;
  isSandbox: boolean;
  openPlanDialog: (intent: "change" | "cancel") => void;
  pendingPlanChange: BillingAccount["pendingPlanChange"];
  projection: Projection;
  projectedMinutes: number;
  resetModel: () => void;
  resumeLoading: boolean;
  resumeSubscription: () => void;
  selectedPlanId: PricingPlanId;
  setInterval: (value: BillingInterval) => void;
  setProjectedMinutes: (value: number) => void;
  setSelectedPlanId: (value: PricingPlanId) => void;
  startAddOnCheckout: (id: BillingAddOnId) => void;
  subscription: BillingAccount["subscription"];
}) {
  const selectedPlan = planById(selectedPlanId);
  const currentPlan = planById(currentPlanId);
  const activeAddOnIds = new Set(activeAddOns.map((item) => item.addOn));
  const currentPrice = subscription?.interval === "annual" ? `${money(currentPlan.monthlyPrice * 0.9 * 12)} / year` : `${money(currentPlan.monthlyPrice)} / month`;
  const subscriptionState = subscription?.status === "canceling" || subscription?.status === "canceled"
    ? "Cancellation scheduled"
    : subscription?.status === "past_due" || subscription?.status === "unresolved"
      ? "Payment issue"
      : subscription?.status === "expired" || subscription?.status === "completed"
        ? "Ended"
        : "Active";
  const subscriptionExplanation = subscriptionState === "Cancellation scheduled"
    ? `Renewal is cancelled. Access continues until ${formatDate(subscription?.renewsAt, "the end of the paid period")}.`
    : subscriptionState === "Payment issue"
      ? "Whop reported a payment problem. Update the payment method to avoid an interruption."
      : subscriptionState === "Ended"
        ? "This membership has ended. Choose a plan to restore paid service."
        : `Renews ${formatDate(subscription?.renewsAt, "at the end of the period")} through ${checkoutProvider}.`;
  const ctaLabel = !checkoutConfigured
    ? "Checkout setup required"
    : pendingPlanChange
      ? `${planById(pendingPlanChange.targetPlan).name} change pending`
      : hasSubscription
        ? selectedPlanId === currentPlanId ? "This is your current plan" : `Switch to ${selectedPlan.name}`
        : isSandbox ? "Continue to sandbox checkout" : "Continue to checkout";
  const stateTone = subscriptionState === "Active" ? "success" : subscriptionState === "Ended" ? "neutral" : "warning";
  const addOnIcon = (id: BillingAddOnId) => id === "line" ? Phone : id === "number" ? CreditCard : id === "voice" ? Sparkles : id === "messaging" ? FileText : UsersRound;

  return (
    <div className="grid gap-8">
      {subscription ? (
        <Card className="flex flex-wrap items-center gap-4 px-5 py-4">
          <div className="min-w-0 flex-1">
            <p className="m-0 flex flex-wrap items-center gap-2 text-[0.92rem] font-medium text-ink">
              {currentPlan.name} · {currentPrice}
              <Badge tone={stateTone} dot>{subscriptionState}</Badge>
            </p>
            <p className="m-0 mt-1 text-[0.8rem] text-fg-soft">{subscriptionExplanation}</p>
            {subscription.providerReference ? <p className="m-0 mt-1 text-[0.72rem] text-fg-muted">Reference {subscription.providerReference}</p> : null}
          </div>
          {subscriptionState === "Cancellation scheduled" ? (
            <Button variant="secondary" size="xs" disabled={resumeLoading} onClick={resumeSubscription}>
              {resumeLoading ? <LoaderCircle className="animate-spin" /> : <RefreshCw />}
              {resumeLoading ? "Resuming…" : `Resume ${currentPlan.name}`}
            </Button>
          ) : null}
        </Card>
      ) : null}

      <section className="grid gap-3">
        <SectionTitle
          title="Plans"
          action={
            <div role="radiogroup" aria-label="Billing interval" className="inline-flex items-center gap-0.5 rounded-[8px] border border-line bg-surface-2 p-0.5">
              {(["monthly", "annual"] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={interval === value}
                  onClick={() => setInterval(value)}
                  className={cn("h-7 cursor-pointer rounded-[6px] border-0 px-3 font-sans text-[0.8rem] capitalize transition-colors", interval === value ? "bg-selected text-selected-fg shadow-[var(--shadow-card)]" : "bg-transparent text-fg-soft hover:text-fg")}
                >
                  {value}{value === "annual" ? <span className="ml-1.5 text-[0.72rem] text-success">−10%</span> : null}
                </button>
              ))}
            </div>
          }
        />
        <div role="radiogroup" aria-label="Plan" className="grid grid-cols-1 gap-3 md:grid-cols-3">
          {pricingPlans.map((plan) => {
            const selected = selectedPlanId === plan.id;
            const current = currentPlanId === plan.id;
            return (
              <button
                key={plan.id}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => setSelectedPlanId(plan.id)}
                className={cn(
                  "relative flex cursor-pointer flex-col rounded-[var(--radius-card)] border bg-surface p-5 text-left font-sans transition-colors",
                  selected ? "border-ink ring-1 ring-ink" : "border-line hover:border-line-strong",
                )}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="text-[0.92rem] font-semibold text-ink">{plan.name}</span>
                  {current ? <Badge tone="neutral">{hasSubscription ? "Current" : "Previous"}</Badge> : null}
                </span>
                <span className="mt-3 text-ink">
                  <span className="text-[1.6rem] font-semibold tabular-nums tracking-[-0.02em]">{money(interval === "annual" ? plan.monthlyPrice * 0.9 : plan.monthlyPrice)}</span>
                  <span className="ml-1 text-[0.8rem] text-fg-muted">/ month</span>
                </span>
                <span className="mt-4 grid gap-1.5 border-t border-line pt-4 text-[0.82rem] text-fg-soft">
                  <span className="flex items-center gap-2"><Check className="size-3.5 text-signal" />{plan.includedMinutes.toLocaleString()} minutes included</span>
                  <span className="flex items-center gap-2"><Check className="size-3.5 text-signal" />{plan.concurrentCalls} {plan.concurrentCalls === 1 ? "call" : "calls"} at once</span>
                  <span className="flex items-center gap-2"><Check className="size-3.5 text-signal" />{money(plan.overageRate)} per extra minute</span>
                </span>
              </button>
            );
          })}
        </div>
        <p className="m-0 text-[0.78rem] text-fg-muted">Need more than {pricingPlans[2].concurrentCalls} calls at once? <Link href="/request-demo" className="text-fg underline underline-offset-4">Talk to us</Link></p>
      </section>

      <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="grid content-start gap-8">
          <div className="grid gap-3">
            <SectionTitle title="Expected minutes" meta="Per month" />
            <Card className="px-5 py-4">
              <div className="flex flex-wrap items-center gap-3">
                <div className="inline-flex items-center rounded-[8px] border border-line-strong">
                  <button type="button" aria-label="100 fewer minutes" onClick={() => setProjectedMinutes(Math.max(0, projectedMinutes - 100))} className="grid size-9 cursor-pointer place-items-center border-0 bg-transparent p-0 text-fg-soft hover:text-ink"><Minus className="size-4" /></button>
                  <input
                    aria-label="Projected AI minutes"
                    type="number"
                    min={0}
                    max={20000}
                    value={projectedMinutes}
                    onChange={(event) => setProjectedMinutes(Math.max(0, Number(event.target.value) || 0))}
                    className="h-9 w-24 border-x border-y-0 border-line-strong bg-transparent text-center font-sans text-[0.95rem] tabular-nums text-ink outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
                  />
                  <button type="button" aria-label="100 more minutes" onClick={() => setProjectedMinutes(Math.min(20000, projectedMinutes + 100))} className="grid size-9 cursor-pointer place-items-center border-0 bg-transparent p-0 text-fg-soft hover:text-ink"><Plus className="size-4" /></button>
                </div>
                <input
                  aria-label="Projected AI minutes slider"
                  type="range"
                  min={0}
                  max={Math.max(3000, selectedPlan.includedMinutes * 2)}
                  step={50}
                  value={projectedMinutes}
                  onChange={(event) => setProjectedMinutes(Number(event.target.value))}
                  className="min-w-[10rem] flex-1 accent-[var(--color-signal)]"
                />
              </div>
              <p className="m-0 mt-3 text-[0.8rem] text-fg-soft">
                {selectedPlan.name} includes {selectedPlan.includedMinutes.toLocaleString()} minutes.{" "}
                {projection.overageMinutes > 0 ? <>That&apos;s <span className="text-ink">{projection.overageMinutes.toLocaleString()} minutes over</span>.</> : "You'd stay within the allowance."}
              </p>
            </Card>
          </div>

          <div className="grid gap-3">
            <SectionTitle title="Add-ons" meta="Billed monthly" />
            <Card>
              <ul className="m-0 grid list-none divide-y divide-line p-0">
                {billingAddOns.map((addOn) => {
                  const active = activeAddOnIds.has(addOn.id);
                  const busy = addOnCheckoutLoading === addOn.id;
                  const Icon = addOnIcon(addOn.id);
                  return (
                    <li key={addOn.id} className="flex items-center gap-3 px-5 py-3.5">
                      <span className="grid size-8 shrink-0 place-items-center rounded-[7px] border border-line bg-surface-2 text-fg-soft"><Icon className="size-4" /></span>
                      <div className="min-w-0 flex-1">
                        <p className="m-0 flex flex-wrap items-center gap-2 text-[0.86rem] text-ink">{addOn.name}{active ? <Badge tone="success">Active</Badge> : null}</p>
                        <p className="m-0 mt-0.5 text-[0.78rem] text-fg-muted">{addOn.description}</p>
                      </div>
                      <span className="hidden text-[0.82rem] tabular-nums text-fg-soft sm:block">{money(addOn.monthlyPrice)}/mo</span>
                      <Button variant="secondary" size="xs" disabled={!hasSubscription || !addOnsConfigured || addOnCheckoutLoading != null} onClick={() => startAddOnCheckout(addOn.id)}>
                        {busy ? <LoaderCircle className="animate-spin" /> : active ? <SlidersHorizontal /> : <Plus />}
                        {busy ? "Opening…" : active ? "Manage" : "Add"}
                      </Button>
                    </li>
                  );
                })}
              </ul>
              {!hasSubscription || !addOnsConfigured || addOnCheckoutError ? (
                <div className="border-t border-line px-5 py-3 text-[0.78rem]">
                  {!hasSubscription ? <p className="m-0 text-fg-muted">Choose a plan first, then add extras.</p> : null}
                  {hasSubscription && !addOnsConfigured ? <p className="m-0 text-fg-muted">Add-on checkout needs the five Whop add-on plan IDs.</p> : null}
                  {addOnCheckoutError ? <p className="m-0 text-danger">{addOnCheckoutError}</p> : null}
                </div>
              ) : null}
            </Card>
          </div>
        </div>

        <div className="grid content-start gap-3 xl:sticky xl:top-20 xl:self-start">
          <SectionTitle title="Your estimate" meta="USD" />
          <Card>
            <Ledger
              rows={[
                { label: `${selectedPlan.name} plan`, note: interval === "annual" ? "Annual, shown per month" : "Monthly", value: money(projection.basePrice) },
                { label: "Overage", note: `${projection.overageMinutes.toLocaleString()} × ${money(selectedPlan.overageRate)}`, value: money(projection.overageCost) },
                { label: "Add-ons", note: `${activeAddOns.length} active`, value: money(projection.addOnCost) },
              ]}
              total={money(projection.total)}
              totalLabel="Per month"
            />
            <div className="grid gap-2 border-t border-line px-5 py-4">
              <Button size="sm" className="w-full" disabled={!checkoutConfigured || pendingPlanChange != null || (hasSubscription && selectedPlanId === currentPlanId)} onClick={() => openPlanDialog("change")}>
                <ShieldCheck /> {ctaLabel}
              </Button>
              {pendingPlanChange ? <p className="m-0 text-[0.76rem] text-fg-muted">Your current plan change has to finish before you request another.</p> : null}
              <div className="flex items-center justify-between gap-2">
                <Button variant="ghost" size="xs" onClick={resetModel} className="px-0"><RotateCcw /> Reset</Button>
                {hasSubscription && !pendingPlanChange && subscriptionState !== "Cancellation scheduled" && subscriptionState !== "Ended" ? (
                  <Button variant="ghost" size="xs" onClick={() => openPlanDialog("cancel")} className="px-0 text-danger hover:text-danger">Cancel subscription</Button>
                ) : null}
              </div>
            </div>
          </Card>
          <p className="m-0 text-[0.76rem] text-fg-muted">Payment happens in {checkoutProvider}. Sauti never sees card details.</p>
        </div>
      </section>
    </div>
  );
}

/* ---------------- Invoices ---------------- */

function InvoicesTab({ switchTab }: { switchTab: (tab: BillingTab) => void }) {
  const rules = [
    { icon: ShieldCheck, title: "Real records only", text: "Invoice numbers, receipts and taxes come from the payment provider, never from Sauti's estimates." },
    { icon: ReceiptText, title: "Estimates stay estimates", text: "Forecasts on this page are never shown as charges." },
    { icon: FileText, title: "Test invoices are labelled", text: "Anything created in sandbox mode is marked Test." },
  ];
  return (
    <section className="grid grid-cols-1 gap-8 [&>*]:min-w-0 lg:grid-cols-[minmax(0,1fr)_360px]" aria-label="Invoices">
      <Card className="flex flex-col items-start justify-center px-6 py-10">
        <span className="grid size-10 place-items-center rounded-[9px] border border-line bg-surface-2 text-fg-soft"><ReceiptText className="size-5" /></span>
        <h2 className="m-0 mt-5 text-[1.05rem] font-semibold text-ink">No invoices yet</h2>
        <p className="m-0 mt-1.5 max-w-md text-[0.86rem] leading-relaxed text-fg-soft">Invoices appear here once a paid subscription starts. Nothing has been charged to this workspace.</p>
        <Button size="xs" className="mt-5" onClick={() => switchTab("plans")}>See plans <ArrowRight /></Button>
      </Card>
      <div className="grid content-start gap-3">
        <SectionTitle title="How billing works" />
        <Card>
          <ul className="m-0 grid list-none divide-y divide-line p-0">
            {rules.map((rule) => (
              <li key={rule.title} className="flex gap-3 px-5 py-3.5">
                <rule.icon className="mt-0.5 size-4 shrink-0 text-fg-muted" />
                <span>
                  <span className="block text-[0.86rem] text-ink">{rule.title}</span>
                  <span className="mt-0.5 block text-[0.78rem] text-fg-soft">{rule.text}</span>
                </span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </section>
  );
}

function BillingLoading() {
  return (
    <div className="grid gap-6" aria-busy="true" aria-label="Loading billing">
      <Skeleton className="h-44 rounded-[var(--radius-card)]" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Skeleton className="h-56 rounded-[var(--radius-card)]" />
        <Skeleton className="h-56 rounded-[var(--radius-card)]" />
      </div>
    </div>
  );
}
