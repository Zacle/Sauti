import type { Metadata } from "next";
import { MarketingResourcesOverviewPage } from "@/features/marketing/Resources/presentation/MarketingResourcesPage";

export const metadata: Metadata = {
  title: "Resources | Sauti",
  description: "Explore Sauti documentation, APIs, case studies, FAQs, and security resources.",
};

export default function ResourcesIndexPage() {
  return <MarketingResourcesOverviewPage />;
}
