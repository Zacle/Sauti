import type { Metadata } from "next";
import { CategoryScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";
import { groupFor } from "@/features/marketing/site-map";

const group = groupFor("resources");

export const metadata: Metadata = {
  title: "Resources | Sauti",
  description: "Explore Sauti documentation, APIs, case studies, FAQs, and security resources.",
};

export default function ResourcesIndexPage() {
  return <CategoryScreen section="resources" />;
}
