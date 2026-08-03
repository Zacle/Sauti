"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowRight,
  BellRing,
  Bot,
  CalendarDays,
  Check,
  CircleHelp,
  Clock3,
  Code2,
  Gauge,
  Globe2,
  Headphones,
  Info,
  Languages,
  MessageSquareText,
  Phone,
  PhoneCall,
  Plus,
  ReceiptText,
  ShieldCheck,
  Sparkles,
  Target,
  UserPlus,
  Users,
  WandSparkles,
  X,
} from "lucide-react";
import {
  ANNUAL_DISCOUNT,
  billedMonthlyPrice,
  comparisonRows,
  estimateMonthlyMinutes,
  formatMoney,
  pricingPlans,
  recommendPlan,
  type PricingOutcome,
  type PricingPlan,
} from "@/features/marketing/Pricing/domain/pricing-model";
import styles from "./MarketingPricingPage.module.css";

const PricingRecommendationPlayer = dynamic(
  () => import("./PricingRecommendationMotion").then((module) => module.PricingRecommendationPlayer),
  { ssr: false },
);

const callsPerWeekOptions = [20, 50, 100, 200];
const callLengthOptions = [2, 3, 5, 10];
const outcomes: Array<{ id: PricingOutcome; label: string; hint: string; icon: typeof CalendarDays }> = [
  { id: "booking", label: "Book appointments", hint: "Check availability and confirm slots", icon: CalendarDays },
  { id: "answers", label: "Answer questions", hint: "Use approved business knowledge", icon: MessageSquareText },
  { id: "qualification", label: "Qualify leads", hint: "Capture intent and next steps", icon: Target },
];

const addOns = [
  {
    icon: Globe2,
    name: "Regional calling",
    price: "From $0.01 / min",
    detail: "Carrier pricing follows the destination. Higher-cost markets are shown before activation.",
  },
  {
    icon: UserPlus,
    name: "Additional agent",
    price: "$29 / month",
    detail: "Add a separate workflow, department, language, or location without changing the whole plan.",
  },
  {
    icon: Gauge,
    name: "Concurrent call line",
    price: "$25 / month",
    detail: "Increase how many live conversations can run at once when peak demand grows.",
  },
  {
    icon: Phone,
    name: "Business number",
    price: "From $5 / month",
    detail: "Local, mobile, and toll-free availability and regulatory fees vary by country.",
  },
  {
    icon: WandSparkles,
    name: "Premium voice",
    price: "Provider cost + 20%",
    detail: "Standard production voices are included. Pay only when you select a premium provider voice.",
  },
  {
    icon: MessageSquareText,
    name: "SMS and WhatsApp",
    price: "Usage cost + 20%",
    detail: "Messaging is optional and always shown separately from voice usage.",
  },
] as const;

function comparisonValue(plan: PricingPlan, row: (typeof comparisonRows)[number]) {
  if (row === "Included AI minutes") return `${plan.includedMinutes.toLocaleString()} minutes`;
  if (row === "Live agents (max)") return `${plan.maxAgents} ${plan.maxAgents === 1 ? "agent" : "agents"}`;
  if (row === "Concurrent calls") return String(plan.concurrentCalls);
  if (row === "Overage rate") return `${formatMoney(plan.overageRate, 2)} / minute`;
  return plan.features[row];
}

function ValueMark({ value }: { value: string | boolean | undefined }) {
  if (value === true) return <Check aria-label="Included" size={16} />;
  if (value === false || value === undefined) return <X aria-label="Not included" size={15} />;
  return <>{value}</>;
}

export function MarketingPricingPage() {
  const [callsPerWeek, setCallsPerWeek] = useState(50);
  const [averageCallMinutes, setAverageCallMinutes] = useState(3);
  const [outcome, setOutcome] = useState<PricingOutcome>("booking");
  const [annual, setAnnual] = useState(false);

  const monthlyMinutes = useMemo(
    () => estimateMonthlyMinutes(callsPerWeek, averageCallMinutes),
    [averageCallMinutes, callsPerWeek],
  );
  const plan = recommendPlan(monthlyMinutes);
  const monthlyPrice = billedMonthlyPrice(plan, annual);
  const headroom = Math.max(0, plan.includedMinutes - monthlyMinutes);
  const overageMinutes = Math.max(0, monthlyMinutes - plan.includedMinutes);
  const estimatedSubscription = monthlyPrice + overageMinutes * plan.overageRate;
  const effectiveRate = estimatedSubscription / Math.max(monthlyMinutes, 1);
  const selectedOutcome = outcomes.find((item) => item.id === outcome) ?? outcomes[0];
  const SelectedOutcomeIcon = selectedOutcome.icon;
  const usageRatio = monthlyMinutes / plan.includedMinutes;

  return (
    <main className={styles.page}>
      <section className={styles.hero}>
        <span className={styles.eyebrow}><Sparkles size={14} /> Pricing guide</span>
        <h1>Price the coverage against{" "}<br />the calls you cannot afford to miss.</h1>
        <p>Estimate your call workload, see the plan that fits, and keep every cost boundary visible before you go live.</p>
      </section>

      <section className={styles.decisionWorkspace} aria-labelledby="pricing-estimator-title">
        <div className={styles.inputsPanel}>
          <header>
            <span>1</span>
            <div>
              <h2 id="pricing-estimator-title">Tell us about your calls</h2>
              <p>Adjust the inputs to reflect a typical week.</p>
            </div>
          </header>

          <fieldset className={styles.controlGroup}>
            <legend><PhoneCall size={18} /><span><strong>Calls per week</strong><small>How many inbound calls do you miss or cannot answer?</small></span></legend>
            <div className={styles.segmented}>
              {callsPerWeekOptions.map((value) => (
                <button
                  className={callsPerWeek === value ? styles.selected : ""}
                  key={value}
                  type="button"
                  aria-pressed={callsPerWeek === value}
                  onClick={() => setCallsPerWeek(value)}
                >
                  {value}
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className={styles.controlGroup}>
            <legend><Clock3 size={18} /><span><strong>Average call length</strong><small>Choose the closest typical conversation.</small></span></legend>
            <div className={styles.segmented}>
              {callLengthOptions.map((value) => (
                <button
                  className={averageCallMinutes === value ? styles.selected : ""}
                  key={value}
                  type="button"
                  aria-pressed={averageCallMinutes === value}
                  onClick={() => setAverageCallMinutes(value)}
                >
                  {value} min
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className={`${styles.controlGroup} ${styles.outcomeGroup}`}>
            <legend><Target size={18} /><span><strong>What should the agent do?</strong><small>Choose the primary outcome you want.</small></span></legend>
            <div className={styles.outcomeOptions}>
              {outcomes.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    className={outcome === item.id ? styles.selectedOutcome : ""}
                    key={item.id}
                    type="button"
                    aria-pressed={outcome === item.id}
                    onClick={() => setOutcome(item.id)}
                  >
                    <Icon size={19} />
                    <strong>{item.label}</strong>
                    <small>{item.hint}</small>
                    <span>{outcome === item.id ? <Check size={13} /> : null}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div className={styles.inputSummary}>
            <span>Your weekly input</span>
            <div><Phone size={14} /> {callsPerWeek} calls</div>
            <div><Clock3 size={14} /> {averageCallMinutes} min average</div>
            <div><SelectedOutcomeIcon size={14} /> {selectedOutcome.label}</div>
          </div>
        </div>

        <div className={styles.recommendationPanel} aria-live="polite">
          <div className={styles.motionLayer} aria-hidden="true">
            <PricingRecommendationPlayer
              motionKey={`${plan.id}-${monthlyMinutes}-${annual}`}
              usageRatio={usageRatio}
            />
          </div>
          <header>
            <span>2</span>
            <div><h2>Your recommendation</h2><p>Based on your inputs, here is the plan that fits.</p></div>
            <em><Sparkles size={12} /> Best fit</em>
          </header>

          <div className={styles.recommendationCard}>
            <div className={styles.planLead}>
              <span><Bot size={26} /></span>
              <div>
                <h3>{plan.name} fits this workload</h3>
                <small>Estimated monthly usage</small>
              </div>
            </div>
            <div className={styles.usageNumber}><strong>{monthlyMinutes.toLocaleString()}</strong><span>AI minutes / month</span></div>
            <div className={styles.planNumbers}>
              <div><strong>{formatMoney(estimatedSubscription)}</strong><span>estimated / month {annual ? "with annual billing" : "billed monthly"}</span></div>
              <div><strong>{plan.includedMinutes.toLocaleString()}</strong><span>AI minutes included</span></div>
            </div>
            <div className={styles.planProof}>
              <div><span>Estimated effective cost</span><strong>{formatMoney(effectiveRate, 2)}</strong><small>per handled minute</small></div>
              <div><span>{headroom > 0 ? "Minutes of headroom" : "Estimated overage"}</span><strong>{headroom > 0 ? headroom.toLocaleString() : overageMinutes.toLocaleString()}</strong><small>minutes</small></div>
            </div>
            <p className={styles.agentCapacity}><Users size={20} /><span><strong>Up to {plan.maxAgents} {plan.maxAgents === 1 ? "agent" : "agents"}</strong><small>{plan.description}</small></span></p>
          </div>

          <p className={styles.caveat}><Info size={17} /> This estimate includes the plan and AI-minute overage. Regional calling, numbers, messaging, and premium voices appear separately before activation.</p>
          <div className={styles.recommendationActions}>
            <Link href="/request-demo">Request a tailored demo <ArrowRight size={15} /></Link>
            <a href="#plan-comparison">See the full comparison</a>
          </div>
        </div>
      </section>

      <section className={styles.comparison} id="plan-comparison" aria-labelledby="comparison-title">
        <header className={styles.comparisonHeader}>
          <div><span>Plans</span><h2 id="comparison-title">Simple tiers with room to grow.</h2></div>
          <div className={styles.billingToggle} role="group" aria-label="Billing frequency">
            <button className={!annual ? styles.activeBilling : ""} type="button" aria-pressed={!annual} onClick={() => setAnnual(false)}>Monthly</button>
            <button className={annual ? styles.activeBilling : ""} type="button" aria-pressed={annual} onClick={() => setAnnual(true)}>Annual <span>Save {ANNUAL_DISCOUNT * 100}%</span></button>
          </div>
        </header>

        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th scope="col">Compare plans</th>
                {pricingPlans.map((item) => (
                  <th className={item.id === "growth" ? styles.recommendedColumn : ""} key={item.id} scope="col">
                    {item.id === "growth" ? <em>Most popular</em> : null}
                    <span>{item.name}</span>
                    <strong>{formatMoney(billedMonthlyPrice(item, annual))}<small>/month</small></strong>
                    <p>{annual ? "Billed annually" : "Billed monthly"}</p>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {comparisonRows.map((row) => (
                <tr key={row}>
                  <th scope="row">{row}<CircleHelp size={13} /></th>
                  {pricingPlans.map((item) => (
                    <td className={item.id === "growth" ? styles.recommendedColumn : ""} key={item.id}>
                      <ValueMark value={comparisonValue(item, row)} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className={styles.tableNote}><Languages size={15} /> Regional carrier, number, messaging, and optional premium-voice charges are previewed before activation and billed separately.</p>
      </section>

      <section className={styles.addOns} aria-labelledby="add-ons-title">
        <header className={styles.addOnsHeader}>
          <div>
            <span><Plus size={14} /> Optional add-ons</span>
            <h2 id="add-ons-title">Know what can change your bill.</h2>
            <p>Your plan covers the agent platform and included AI minutes. These extras apply only when you activate or use them.</p>
          </div>
          <div className={styles.billFormula}>
            <ReceiptText size={19} />
            <span><small>Your expected monthly bill</small><strong>Plan + overage + activated add-ons</strong></span>
          </div>
        </header>

        <div className={styles.addOnGrid}>
          {addOns.map((item) => {
            const Icon = item.icon;
            return (
              <article key={item.name}>
                <div><Icon size={20} /></div>
                <span><strong>{item.name}</strong><em>{item.price}</em></span>
                <p>{item.detail}</p>
              </article>
            );
          })}
        </div>

        <div className={styles.addOnFooter}>
          <ShieldCheck size={18} />
          <p><strong>No surprise activation.</strong> Destination rates and recurring add-ons are confirmed before they start. Usage alerts arrive at 80% and 100%, and you can set a hard spending cap.</p>
          <a href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Model my bill <ArrowRight size={14} /></a>
        </div>
      </section>

      <section className={styles.protectionStrip} aria-label="Trial and billing protections">
        <div><CalendarDays size={28} /><span><strong>14-day trial included</strong><small>10 browser test minutes. No card required. Go live on a paid plan.</small></span></div>
        <div><BellRing size={28} /><span><strong>Usage alerts</strong><small>Get notified at 80% and 100% of included minutes.</small></span></div>
        <div><ShieldCheck size={28} /><span><strong>Spend control</strong><small>Set a hard cap or approve transparent overages.</small></span></div>
        <div><Code2 size={28} /><span><strong>Built to scale</strong><small>Signed webhooks, APIs, and clear concurrency limits.</small></span></div>
      </section>

      <section className={styles.closingCta}>
        <div><Headphones size={26} /><span><strong>Not sure which plan fits?</strong><small>Bring your weekly call estimate and we will model it with you.</small></span></div>
        <a href="https://cal.com/sauti/demo" target="_blank" rel="noreferrer">Talk through the numbers <ArrowRight size={15} /></a>
      </section>
    </main>
  );
}
