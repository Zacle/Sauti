import type { Metadata } from "next";
import { BillingPage as BillingFeaturePage } from "@/features/billing/presentation/BillingPage";


export const metadata: Metadata = { title: "Usage & billing | Sauti" };

export default function BillingPage() {
  return <BillingFeaturePage />;
}
