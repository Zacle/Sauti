import type { Metadata } from "next";
import { SolutionsOverviewPage } from "@/features/marketing/Solutions/SolutionPages";

export const metadata: Metadata = {
  title: "Solutions | Sauti",
  description: "Explore Sauti AI call workflows for appointment booking, support, qualification, and escalation.",
};

export default function SolutionsIndexPage() {
  return <SolutionsOverviewPage />;
}
