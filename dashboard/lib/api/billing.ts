import type { BillingAccount, BillingUsage } from "@/types/api";
import { apiRequest } from "./client";

export function loadBillingUsage(): Promise<BillingUsage> {
  return apiRequest<BillingUsage>("/billing/usage");
}

export function loadBillingAccount(): Promise<BillingAccount> {
  return apiRequest<BillingAccount>("/billing/account");
}
