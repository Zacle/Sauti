import {
  ANNUAL_DISCOUNT,
  pricingPlans,
  type PricingPlan,
  type PricingPlanId,
} from "@/features/marketing/Pricing/domain/pricing-model";
import type { BillingUsage } from "@/types/api";

export type BillingTab = "overview" | "usage" | "plans" | "invoices";
export type BillingInterval = "monthly" | "annual";
export type BillingAddOnId = "agent" | "line" | "number" | "voice" | "messaging";

export type BillingAddOn = {
  id: BillingAddOnId;
  name: string;
  description: string;
  monthlyPrice: number;
  unit: string;
};

export const billingTabs: Array<{ id: BillingTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "usage", label: "Usage" },
  { id: "plans", label: "Plans & add-ons" },
  { id: "invoices", label: "Invoices" },
];

export const billingAddOns: BillingAddOn[] = [
  { id: "agent", name: "Additional agent", description: "A separate workflow, department, language, or location.", monthlyPrice: 29, unit: "agent" },
  { id: "line", name: "Concurrent call line", description: "One more live conversation during peak demand.", monthlyPrice: 25, unit: "line" },
  { id: "number", name: "Business phone number", description: "Local or toll-free availability varies by country.", monthlyPrice: 5, unit: "number" },
  { id: "voice", name: "Premium voice", description: "A premium provider voice for production calls.", monthlyPrice: 19, unit: "voice" },
  { id: "messaging", name: "SMS / WhatsApp messaging", description: "Optional customer messaging after voice interactions.", monthlyPrice: 19, unit: "workspace" },
];

export type BillingProjection = {
  basePrice: number;
  overageMinutes: number;
  overageCost: number;
  addOnCost: number;
  total: number;
};

export function resolvePlan(usage: BillingUsage): PricingPlan {
  const normalized = usage.plan.toLowerCase();
  if (normalized.includes("growth")) return pricingPlans[1];
  if (normalized.includes("scale") || normalized.includes("pro")) return pricingPlans[2];
  if (normalized.includes("launch") || normalized.includes("starter") || normalized.includes("trial")) return pricingPlans[0];
  return pricingPlans.find((plan) => usage.monthlyMinutesLimit <= plan.includedMinutes) ?? pricingPlans[2];
}

export function projectBilling(
  plan: PricingPlan,
  projectedMinutes: number,
  interval: BillingInterval,
  quantities: Partial<Record<BillingAddOnId, number>>,
): BillingProjection {
  const basePrice = interval === "annual" ? plan.monthlyPrice * (1 - ANNUAL_DISCOUNT) : plan.monthlyPrice;
  const overageMinutes = Math.max(0, projectedMinutes - plan.includedMinutes);
  const overageCost = overageMinutes * plan.overageRate;
  const addOnCost = billingAddOns.reduce((sum, addOn) => sum + addOn.monthlyPrice * Math.max(0, quantities[addOn.id] ?? 0), 0);
  return { basePrice, overageMinutes, overageCost, addOnCost, total: basePrice + overageCost + addOnCost };
}

export function estimateForecast(usedMinutes: number, includedMinutes: number) {
  if (usedMinutes === 0) return 0;
  const conservativeUplift = Math.round(usedMinutes * 1.235);
  return Math.max(usedMinutes, Math.min(conservativeUplift, Math.max(includedMinutes * 3, usedMinutes)));
}

export function buildModelledUsageSeries(usedMinutes: number) {
  const today = new Date();
  const visibleDays = 18;
  const weights = Array.from({ length: visibleDays }, (_, index) => 0.78 + ((index * 7) % 9) / 20);
  const totalWeight = weights.reduce((sum, weight) => sum + weight, 0);
  let cumulative = 0;
  return weights.map((weight, index) => {
    cumulative += usedMinutes * (weight / totalWeight);
    const date = new Date(today.getFullYear(), today.getMonth(), index + 1);
    return {
      date: new Intl.DateTimeFormat("en", { month: "short", day: "numeric" }).format(date),
      actual: Math.round(cumulative),
    };
  });
}

export function planById(id: PricingPlanId) {
  return pricingPlans.find((plan) => plan.id === id) ?? pricingPlans[0];
}

export function money(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", minimumFractionDigits: value % 1 ? 2 : 0 }).format(value);
}
