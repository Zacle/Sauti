import type { Metadata } from "next";
import { MarketingIntegrationsOverviewPage } from "@/features/marketing/Integrations/presentation/MarketingIntegrationsPage";

export const metadata: Metadata = {
  title: "Integrations | Sauti",
  description: "See how Sauti connects live voice, AI models, calendars, business tools, and developer systems.",
};

export default function IntegrationsIndexPage() {
  return <MarketingIntegrationsOverviewPage />;
}
