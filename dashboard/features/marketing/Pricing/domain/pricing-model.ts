export type PricingPlanId = "launch" | "growth" | "scale";
export type PricingOutcome = "booking" | "answers" | "qualification";

export type PricingPlan = {
  id: PricingPlanId;
  name: string;
  monthlyPrice: number;
  includedMinutes: number;
  maxAgents: number;
  concurrentCalls: number;
  overageRate: number;
  description: string;
  features: Record<string, string | boolean>;
};

export const ANNUAL_DISCOUNT = 0.1;
export const WEEKS_PER_MONTH = 13 / 3;

export const pricingPlans: PricingPlan[] = [
  {
    id: "launch",
    name: "Launch",
    monthlyPrice: 49,
    includedMinutes: 100,
    maxAgents: 1,
    concurrentCalls: 1,
    overageRate: 0.2,
    description: "For one front-desk workflow and occasional live demand.",
    features: {
      "Booking + FAQ tools": true,
      "Analytics & reporting": "Core",
      "CRM / calendar connections": "1 connection",
      "API & signed webhooks": false,
      Support: "Email",
    },
  },
  {
    id: "growth",
    name: "Growth",
    monthlyPrice: 149,
    includedMinutes: 750,
    maxAgents: 3,
    concurrentCalls: 2,
    overageRate: 0.17,
    description: "For growing teams with steady call volume.",
    features: {
      "Booking + FAQ tools": true,
      "Analytics & reporting": "Advanced",
      "CRM / calendar connections": "3 connections",
      "API & signed webhooks": "Standard",
      Support: "Priority email",
    },
  },
  {
    id: "scale",
    name: "Scale",
    monthlyPrice: 399,
    includedMinutes: 2_500,
    maxAgents: 10,
    concurrentCalls: 5,
    overageRate: 0.14,
    description: "For multi-location coverage and controlled scale.",
    features: {
      "Booking + FAQ tools": true,
      "Analytics & reporting": "Advanced",
      "CRM / calendar connections": "10 connections",
      "API & signed webhooks": "Advanced",
      Support: "Priority + rollout",
    },
  },
];

export const comparisonRows = [
  "Included AI minutes",
  "Live agents (max)",
  "Concurrent calls",
  "Booking + FAQ tools",
  "Analytics & reporting",
  "CRM / calendar connections",
  "API & signed webhooks",
  "Support",
  "Overage rate",
] as const;

export function estimateMonthlyMinutes(callsPerWeek: number, averageCallMinutes: number) {
  return Math.round(callsPerWeek * averageCallMinutes * WEEKS_PER_MONTH);
}

export function recommendPlan(monthlyMinutes: number) {
  return pricingPlans.find((plan) => monthlyMinutes <= plan.includedMinutes) ?? pricingPlans[2];
}

export function billedMonthlyPrice(plan: PricingPlan, annual: boolean) {
  return annual ? plan.monthlyPrice * (1 - ANNUAL_DISCOUNT) : plan.monthlyPrice;
}

export function formatMoney(value: number, digits = 0) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  }).format(value);
}
