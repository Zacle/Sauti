import type { Metadata } from "next";
import { CategoryScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";
import { groupFor } from "@/features/marketing/site-map";

const group = groupFor("integrations");

export const metadata: Metadata = {
  title: "Integrations | Sauti",
  description: "Explore Sauti integrations for voice, speech, AI models, calendars, business tools, and developer APIs.",
};

export default function IntegrationsIndexPage() {
  return <CategoryScreen section="integrations" />;
}
