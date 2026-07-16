import type { Metadata } from "next";
import { CategoryScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";

export const metadata: Metadata = {
  title: "Solutions | Sauti",
  description: "Explore Sauti AI call workflows for appointment booking, support, qualification, and escalation.",
};

export default function SolutionsIndexPage() {
  return <CategoryScreen section="solutions" />;
}
