import type { BillingAccount, BillingAddOnCheckout, BillingCancellation, BillingCheckout, BillingCheckoutStatus, BillingUsage } from "@/types/api";
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

export function loadBillingCheckoutStatus(): Promise<BillingCheckoutStatus> {
  return apiRequest<BillingCheckoutStatus>("/billing/checkout/status");
}

export function createBillingAddOnCheckout(addOn: BillingAddOnCheckout["addOn"]): Promise<BillingAddOnCheckout> {
  return apiRequest<BillingAddOnCheckout>("/billing/checkout/add-on", {
    method: "POST",
    body: JSON.stringify({ addOn }),
  });
}

export function cancelBillingSubscription(): Promise<BillingCancellation> {
  return apiRequest<BillingCancellation>("/billing/subscription/cancel", { method: "POST" });
}
