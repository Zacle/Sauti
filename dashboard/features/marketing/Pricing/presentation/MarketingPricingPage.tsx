"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import NumberFlow from "@number-flow/react";
import { motion } from "motion/react";
import {
  ArrowRight,
  BellRing,
  CalendarDays,
  Check,
  Clock3,
  Code2,
  Gauge,
  Globe2,
  Info,
  MessageSquareText,
  Phone,
  PhoneCall,
  ReceiptText,
  ShieldCheck,
  Sparkles,
  Target,
  UserPlus,
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
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  CtaBand,
  IconTile,
  MarketingPage,
  PageHero,
  Reveal,
  Section,
  SectionHeading,
  SpotlightCard,
} from "@/features/marketing/shared/primitives";

const callsPerWeekOptions = [20, 50, 100, 200];
const callLengthOptions = [2, 3, 5, 10];
const outcomes: Array<{ id: PricingOutcome; label: string; hint: string; icon: typeof CalendarDays }> = [
  { id: "booking", label: "Book appointments", hint: "Check availability and confirm slots", icon: CalendarDays },
  { id: "answers", label: "Answer questions", hint: "Use approved business knowledge", icon: MessageSquareText },
  { id: "qualification", label: "Qualify leads", hint: "Capture intent and next steps", icon: Target },
];

const addOns = [
  { icon: Globe2, name: "Regional calling", price: "From $0.01 / min", detail: "Carrier pricing follows the destination. Higher-cost markets are shown before activation." },
  { icon: UserPlus, name: "Additional agent", price: "$29 / month", detail: "Add a separate workflow, department, language, or location without changing the whole plan." },
  { icon: Gauge, name: "Concurrent call line", price: "$25 / month", detail: "Increase how many live conversations can run at once when peak demand grows." },
  { icon: Phone, name: "Business number", price: "From $5 / month", detail: "Local, mobile, and toll-free availability and regulatory fees vary by country." },
  { icon: WandSparkles, name: "Premium voice", price: "Provider cost + 20%", detail: "Standard production voices are included. Pay only when you select a premium provider voice." },
  { icon: MessageSquareText, name: "SMS and WhatsApp", price: "Usage cost + 20%", detail: "Messaging is optional and always shown separately from voice usage." },
] as const;

const protections = [
  { icon: CalendarDays, title: "14-day trial included", text: "10 browser test minutes. No card required. Go live on a paid plan." },
  { icon: BellRing, title: "Usage alerts", text: "Get notified at 80% and 100% of included minutes." },
  { icon: ShieldCheck, title: "Spend control", text: "Set a hard cap or approve transparent overages." },
  { icon: Code2, title: "Built to scale", text: "Signed webhooks, APIs, and clear concurrency limits." },
];

const planAccent: Record<PricingPlan["id"], string> = {
  launch: "96 165 250",
  growth: "94 234 212",
  scale: "167 139 250",
};

function comparisonValue(plan: PricingPlan, row: (typeof comparisonRows)[number]) {
  if (row === "Included AI minutes") return `${plan.includedMinutes.toLocaleString()} minutes`;
  if (row === "Live agents (max)") return `${plan.maxAgents} ${plan.maxAgents === 1 ? "agent" : "agents"}`;
  if (row === "Concurrent calls") return String(plan.concurrentCalls);
  if (row === "Overage rate") return `${formatMoney(plan.overageRate, 2)} / minute`;
  return plan.features[row];
}

function ValueMark({ value }: { value: string | boolean | undefined }) {
  if (value === true) return <Check aria-label="Included" className="mx-auto size-4 text-signal" />;
  if (value === false || value === undefined) return <X aria-label="Not included" className="mx-auto size-4 text-fg-muted" />;
  return <>{value}</>;
}

/* ---------- small controls ---------- */

function Segmented<T extends string | number>({
  id,
  value,
  options,
  onChange,
  format = (option) => String(option),
  label,
}: {
  id: string;
  value: T;
  options: readonly T[];
  onChange: (value: T) => void;
  format?: (option: T) => string;
  label: string;
}) {
  return (
    <div role="group" aria-label={label} className="grid grid-flow-col gap-1 rounded-2xl border border-line bg-white/[0.02] p-1">
      {options.map((option) => {
        const selected = option === value;
        return (
          <button
            key={String(option)}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(option)}
            className={cn(
              "relative h-10 rounded-xl text-sm transition-colors focus-visible:outline-2 focus-visible:outline-signal",
              selected ? "text-black" : "text-fg-soft hover:text-fg",
            )}
          >
            {selected ? (
              <motion.span layoutId={`seg-${id}`} className="absolute inset-0 rounded-xl bg-white" transition={{ type: "spring", bounce: 0.15, duration: 0.45 }} />
            ) : null}
            <span className="relative font-medium tabular-nums">{format(option)}</span>
          </button>
        );
      })}
    </div>
  );
}

function BillingToggle({ id, annual, onChange }: { id: string; annual: boolean; onChange: (annual: boolean) => void }) {
  return (
    <div role="group" aria-label="Billing frequency" className="inline-flex gap-1 rounded-full border border-line bg-white/[0.03] p-1">
      {[false, true].map((isAnnual) => {
        const selected = annual === isAnnual;
        return (
          <button
            key={String(isAnnual)}
            type="button"
            aria-pressed={selected}
            onClick={() => onChange(isAnnual)}
            className={cn(
              "relative flex h-9 items-center gap-2 rounded-full px-4 text-sm transition-colors focus-visible:outline-2 focus-visible:outline-signal",
              selected ? "text-black" : "text-fg-soft hover:text-fg",
            )}
          >
            {selected ? (
              <motion.span layoutId={`billing-pill-${id}`} className="absolute inset-0 rounded-full bg-white" transition={{ type: "spring", bounce: 0.15, duration: 0.45 }} />
            ) : null}
            <span className="relative font-medium">{isAnnual ? "Annual" : "Monthly"}</span>
            {isAnnual ? (
              <span className={cn("relative rounded-full px-2 py-0.5 font-mono text-[0.6rem] uppercase tracking-wider", selected ? "bg-black/10 text-black" : "bg-signal/15 text-signal")}>
                Save {ANNUAL_DISCOUNT * 100}%
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

/* ---------- page ---------- */

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
  const usageRatio = Math.min(1, monthlyMinutes / plan.includedMinutes);

  return (
    <MarketingPage>
      <PageHero
        align="center"
        eyebrow="Pricing"
        title="Pay for the calls"
        accent="you can't afford to miss."
        description="Simple plans with included AI minutes. Every extra cost is shown before it starts, and you can cap spend at any time."
        actions={<BillingToggle id="t1" annual={annual} onChange={setAnnual} />}
        glowSeed={12}
      />

      {/* Plans */}
      <section aria-label="Plans" className="-mt-6 pb-20 sm:pb-28">
        <div className="mx-auto grid w-full max-w-[1240px] gap-4 px-4 sm:px-8 lg:grid-cols-3">
          {pricingPlans.map((item, index) => {
            const popular = item.id === "growth";
            return (
              <Reveal key={item.id} delay={index * 0.07} className="h-full">
                <SpotlightCard
                  accent={planAccent[item.id]}
                  className={cn("p-7 sm:p-8", popular && "border-signal/35 bg-[linear-gradient(180deg,#0f1513,#060606)] shadow-[0_0_80px_-30px_rgb(94_234_212/0.55)]")}
                >
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-medium text-white">{item.name}</h2>
                    {popular ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-signal/15 px-2.5 py-1 font-mono text-[0.6rem] uppercase tracking-wider text-signal">
                        <Sparkles className="size-3" aria-hidden="true" /> Most popular
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-2 min-h-[3rem] text-sm leading-relaxed text-fg-soft">{item.description}</p>
                  <p className="mt-6 flex items-baseline gap-1.5">
                    <span className="text-5xl font-medium tracking-tight text-white tabular-nums">
                      <NumberFlow locales="en-US" value={billedMonthlyPrice(item, annual)} format={{ style: "currency", currency: "USD", maximumFractionDigits: 0 }} />
                    </span>
                    <span className="text-sm text-fg-muted">/ month</span>
                  </p>
                  <p className="mt-1 text-xs text-fg-muted">{annual ? "Billed annually" : "Billed monthly"}</p>

                  <Button asChild variant={popular ? "primary" : "secondary"} className="mt-7 w-full">
                    <Link href="/request-demo">
                      Get started <ArrowRight aria-hidden="true" />
                    </Link>
                  </Button>

                  <ul className="mt-8 grid gap-3 border-t border-line pt-7 text-sm">
                    <PlanLine strong>{item.includedMinutes.toLocaleString()} AI minutes included</PlanLine>
                    <PlanLine>Up to {item.maxAgents} live {item.maxAgents === 1 ? "agent" : "agents"}</PlanLine>
                    <PlanLine>{item.concurrentCalls} concurrent {item.concurrentCalls === 1 ? "call" : "calls"}</PlanLine>
                    {Object.entries(item.features).map(([feature, value]) =>
                      value === false ? (
                        <li key={feature} className="flex gap-3 text-fg-muted line-through decoration-white/20">
                          <X className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                          {feature}
                        </li>
                      ) : (
                        <PlanLine key={feature}>
                          {feature}
                          {typeof value === "string" ? <span className="text-fg-muted"> · {value}</span> : null}
                        </PlanLine>
                      ),
                    )}
                    <PlanLine>{formatMoney(item.overageRate, 2)} / extra minute</PlanLine>
                  </ul>
                </SpotlightCard>
              </Reveal>
            );
          })}
        </div>
      </section>

      {/* Estimator */}
      <Section id="estimator">
        <SectionHeading
          eyebrow="Plan finder"
          title="Find the plan"
          accent="that fits your week."
          description="Tell us roughly how many calls you miss and how long they last. We'll recommend a plan and show the full monthly estimate."
        />

        <div className="mt-12 grid gap-4 lg:grid-cols-[1fr_1.05fr]">
          <Reveal className="h-full">
            <div className="flex h-full flex-col gap-8 rounded-3xl border border-line bg-[linear-gradient(180deg,#0d0d0d,#060606)] p-6 sm:p-8">
              <Control icon={PhoneCall} title="Calls per week" hint="How many inbound calls do you miss or can't answer?">
                <Segmented id="calls" label="Calls per week" value={callsPerWeek} options={callsPerWeekOptions} onChange={setCallsPerWeek} />
              </Control>
              <Control icon={Clock3} title="Average call length" hint="Pick the closest typical conversation.">
                <Segmented id="length" label="Average call length" value={averageCallMinutes} options={callLengthOptions} onChange={setAverageCallMinutes} format={(v) => `${v} min`} />
              </Control>
              <Control icon={Target} title="What should the agent do?" hint="Choose the main outcome.">
                <div className="grid gap-2 sm:grid-cols-3">
                  {outcomes.map((item) => {
                    const Icon = item.icon;
                    const selected = outcome === item.id;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        aria-pressed={selected}
                        onClick={() => setOutcome(item.id)}
                        className={cn(
                          "flex flex-col items-start gap-2 rounded-2xl border p-3.5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-signal",
                          selected ? "border-signal/45 bg-signal/[0.08]" : "border-line bg-white/[0.02] hover:border-line-strong",
                        )}
                      >
                        <Icon className={cn("size-[18px]", selected ? "text-signal" : "text-fg-soft")} aria-hidden="true" />
                        <span className="text-sm font-medium text-fg">{item.label}</span>
                        <span className="text-xs leading-snug text-fg-muted">{item.hint}</span>
                      </button>
                    );
                  })}
                </div>
              </Control>
            </div>
          </Reveal>

          <Reveal delay={0.08} className="h-full">
            <div
              aria-live="polite"
              className="relative flex h-full flex-col overflow-hidden rounded-3xl border border-signal/30 bg-[radial-gradient(120%_80%_at_100%_0%,rgb(94_234_212/0.12),transparent_55%),linear-gradient(180deg,#0e1312,#060606)] p-6 sm:p-8"
            >
              <div className="flex items-center justify-between gap-4">
                <p className="font-mono text-[0.65rem] uppercase tracking-[0.18em] text-signal">Recommended</p>
                <BillingToggle id="t2" annual={annual} onChange={setAnnual} />
              </div>
              <h3 className="mt-4 text-3xl font-medium tracking-tight text-white">
                {plan.name} <span className="font-serif font-normal italic text-fg-soft">fits this workload.</span>
              </h3>

              <div className="mt-8 grid grid-cols-2 gap-6">
                <Stat label="Estimated / month">
                  <NumberFlow locales="en-US" value={estimatedSubscription} format={{ style: "currency", currency: "USD", maximumFractionDigits: 0 }} />
                </Stat>
                <Stat label="AI minutes / month">
                  <NumberFlow locales="en-US" value={monthlyMinutes} />
                </Stat>
              </div>

              <div className="mt-8">
                <div className="flex items-baseline justify-between text-xs text-fg-muted">
                  <span>Usage of included minutes</span>
                  <span className="tabular-nums">
                    {monthlyMinutes.toLocaleString()} / {plan.includedMinutes.toLocaleString()}
                  </span>
                </div>
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-white/[0.06]">
                  <motion.div
                    className={cn("h-full rounded-full", overageMinutes > 0 ? "bg-[#fbbf24]" : "bg-signal")}
                    initial={false}
                    animate={{ width: `${Math.max(4, usageRatio * 100)}%` }}
                    transition={{ type: "spring", bounce: 0.1, duration: 0.8 }}
                    style={{ boxShadow: "0 0 18px rgb(94 234 212 / 0.45)" }}
                  />
                </div>
              </div>

              <dl className="mt-8 grid grid-cols-2 gap-px overflow-hidden rounded-2xl border border-line bg-line">
                <div className="bg-black/60 p-4">
                  <dt className="text-xs text-fg-muted">Effective cost</dt>
                  <dd className="mt-1 text-lg font-medium text-white tabular-nums">
                    <NumberFlow locales="en-US" value={effectiveRate} format={{ style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 2 }} />
                    <span className="text-xs font-normal text-fg-muted"> / min</span>
                  </dd>
                </div>
                <div className="bg-black/60 p-4">
                  <dt className="text-xs text-fg-muted">{headroom > 0 ? "Headroom" : "Estimated overage"}</dt>
                  <dd className="mt-1 text-lg font-medium text-white tabular-nums">
                    <NumberFlow locales="en-US" value={headroom > 0 ? headroom : overageMinutes} />
                    <span className="text-xs font-normal text-fg-muted"> min</span>
                  </dd>
                </div>
              </dl>

              <p className="mt-6 flex gap-2 text-xs leading-relaxed text-fg-muted">
                <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                Includes the plan and AI-minute overage. Regional calling, numbers, messaging and premium voices appear separately before activation.
              </p>

              <div className="mt-auto flex flex-wrap gap-3 pt-8">
                <Button asChild>
                  <Link href="/request-demo">
                    Request a tailored demo <ArrowRight aria-hidden="true" />
                  </Link>
                </Button>
                <Button asChild variant="secondary">
                  <a href="#plan-comparison">Compare plans</a>
                </Button>
              </div>
            </div>
          </Reveal>
        </div>
      </Section>

      {/* Comparison */}
      <Section id="plan-comparison">
        <SectionHeading eyebrow="Compare" title="Every plan," accent="side by side." action={<BillingToggle id="t3" annual={annual} onChange={setAnnual} />} />
        <Reveal className="mt-12">
          <div className="overflow-x-auto rounded-3xl border border-line">
            <table className="w-full min-w-[720px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-line">
                  <th scope="col" className="sticky left-0 bg-canvas p-5 text-left font-mono text-[0.65rem] font-normal uppercase tracking-[0.16em] text-fg-muted">
                    Features
                  </th>
                  {pricingPlans.map((item) => (
                    <th key={item.id} scope="col" className={cn("p-5 text-center align-bottom font-normal", item.id === "growth" && "bg-signal/[0.05]")}>
                      <span className="block text-base font-medium text-white">{item.name}</span>
                      <span className="mt-1 block text-fg-soft tabular-nums">
                        <NumberFlow locales="en-US" value={billedMonthlyPrice(item, annual)} format={{ style: "currency", currency: "USD", maximumFractionDigits: 0 }} />
                        <span className="text-fg-muted"> / mo</span>
                      </span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {comparisonRows.map((row) => (
                  <tr key={row} className="border-b border-line last:border-0 hover:bg-white/[0.02]">
                    <th scope="row" className="sticky left-0 bg-canvas p-5 text-left font-normal text-fg">
                      {row}
                    </th>
                    {pricingPlans.map((item) => (
                      <td key={item.id} className={cn("p-5 text-center text-fg-soft", item.id === "growth" && "bg-signal/[0.05]")}>
                        <ValueMark value={comparisonValue(item, row)} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-4 flex gap-2 text-xs text-fg-muted">
            <Globe2 className="size-3.5 shrink-0" aria-hidden="true" />
            Regional carrier, number, messaging and optional premium-voice charges are previewed before activation and billed separately.
          </p>
        </Reveal>
      </Section>

      {/* Add-ons */}
      <Section>
        <SectionHeading
          eyebrow="Optional add-ons"
          title="Know what can"
          accent="change your bill."
          description="Your plan covers the agent platform and included AI minutes. These extras apply only when you switch them on."
          action={
            <div className="flex items-center gap-3 rounded-2xl border border-line bg-white/[0.02] px-4 py-3">
              <ReceiptText className="size-5 text-signal" aria-hidden="true" />
              <span className="text-sm">
                <span className="block text-xs text-fg-muted">Your monthly bill</span>
                <span className="text-fg">Plan + overage + add-ons you activate</span>
              </span>
            </div>
          }
        />
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {addOns.map(({ icon: Icon, name, price, detail }, index) => (
            <Reveal key={name} delay={index * 0.05} className="h-full">
              <SpotlightCard>
                <div className="flex items-start justify-between gap-4">
                  <IconTile>
                    <Icon aria-hidden="true" />
                  </IconTile>
                  <span className="rounded-full border border-line px-2.5 py-1 font-mono text-[0.65rem] text-fg-soft">{price}</span>
                </div>
                <h3 className="mt-6 text-lg font-medium text-white">{name}</h3>
                <p className="mt-2 text-sm leading-relaxed text-fg-soft">{detail}</p>
              </SpotlightCard>
            </Reveal>
          ))}
        </div>
      </Section>

      {/* Protections */}
      <section aria-label="Trial and billing protections" className="border-t border-line">
        <div className="mx-auto grid max-w-[1240px] gap-px bg-line sm:grid-cols-2 lg:grid-cols-4">
          {protections.map(({ icon: Icon, title, text }) => (
            <div key={title} className="bg-canvas px-6 py-10 sm:px-8">
              <Icon className="size-5 text-signal" aria-hidden="true" />
              <p className="mt-5 font-medium text-white">{title}</p>
              <p className="mt-1.5 text-sm leading-relaxed text-fg-soft">{text}</p>
            </div>
          ))}
        </div>
      </section>

      <CtaBand title="Not sure which" accent="plan" titleEnd="fits?" description="Bring your weekly call estimate and we'll model your bill with you." seed={13} />
    </MarketingPage>
  );
}

function PlanLine({ children, strong = false }: { children: React.ReactNode; strong?: boolean }) {
  return (
    <li className={cn("flex gap-3", strong ? "text-white" : "text-fg-soft")}>
      <Check className="mt-0.5 size-4 shrink-0 text-signal" aria-hidden="true" />
      <span>{children}</span>
    </li>
  );
}

function Control({
  icon: Icon,
  title,
  hint,
  children,
}: {
  icon: typeof PhoneCall;
  title: string;
  hint: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <div className="mb-3 flex gap-3">
        <Icon className="mt-0.5 size-[18px] shrink-0 text-signal" aria-hidden="true" />
        <div>
          <p className="text-[0.95rem] font-medium text-fg">{title}</p>
          <p className="text-xs text-fg-muted">{hint}</p>
        </div>
      </div>
      {children}
    </div>
  );
}

function Stat({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col-reverse gap-1">
      <span className="font-mono text-[0.62rem] uppercase tracking-[0.16em] text-fg-muted">{label}</span>
      <span className="text-4xl font-medium tracking-tight text-white tabular-nums">{children}</span>
    </div>
  );
}
