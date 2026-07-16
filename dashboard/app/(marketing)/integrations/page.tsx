import type { Metadata } from "next";
import { CategoryScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";

export const metadata: Metadata = {
  title: "Integrations | Sauti",
  description: "Explore Sauti integrations for voice, speech, AI models, calendars, business tools, and developer APIs.",
};

export default function IntegrationsIndexPage() {
  return <CategoryScreen section="integrations" />;
}
