import type { Metadata } from "next";
import { CategoryScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";
import { groupFor } from "@/features/marketing/site-map";

const group = groupFor("solutions");

export const metadata: Metadata = {
  title: "Solutions | Sauti",
  description: "Explore Sauti AI call workflows for appointment booking, support, qualification, and escalation.",
};

export default function SolutionsIndexPage() {
  return <CategoryScreen section="solutions" />;
}
