import type { BillingUsage } from "@/types/api";
import { apiRequest } from "./client";

export function loadBillingUsage(): Promise<BillingUsage> {
  return apiRequest<BillingUsage>("/billing/usage");
}

