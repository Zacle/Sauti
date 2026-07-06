import type { Metadata } from "next";
import { PricingScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";
import { pricingPage } from "@/features/marketing/site-map";

export const metadata: Metadata = {
  title: `${pricingPage.label} | Sauti`,
  description: pricingPage.description,
};

export default function PricingPage() {
  return <PricingScreen />;
}
