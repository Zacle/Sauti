import { BarChart3 } from "lucide-react";
import { ConsolePlaceholder } from "@/features/console/ConsolePlaceholder/ConsolePlaceholder";

export default function AnalyticsPage() {
  return <ConsolePlaceholder icon={BarChart3} eyebrow="Insights" title="Analytics" description="Explore call volume, languages, outcomes, and agent performance." />;
}
