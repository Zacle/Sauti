import type { Metadata } from "next";
import { MarketingPricingPage } from "@/features/marketing/Pricing/presentation/MarketingPricingPage";

export const metadata: Metadata = {
  title: "Pricing | Sauti",
  description: "Estimate your AI call workload, compare transparent Sauti plans, and keep usage, overages, and regional voice costs under control.",
};

export default function PricingPage() {
  return <MarketingPricingPage />;
}
