import type { BillingAccount, BillingCheckout, BillingUsage } from "@/types/api";
import { apiRequest } from "./client";

export function loadBillingUsage(): Promise<BillingUsage> {
  return apiRequest<BillingUsage>("/billing/usage");
}

export function loadBillingAccount(): Promise<BillingAccount> {
  return apiRequest<BillingAccount>("/billing/account");
}

export function createBillingCheckout(plan: BillingCheckout["plan"], interval: BillingCheckout["interval"]): Promise<BillingCheckout> {
  return apiRequest<BillingCheckout>("/billing/checkout", {
    method: "POST",
    body: JSON.stringify({ plan, interval }),
  });
}
