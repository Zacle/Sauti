import type { Metadata } from "next";
import { AnalyticsPage as AnalyticsFeaturePage } from "@/features/analytics/presentation/AnalyticsPage";


export const metadata: Metadata = { title: "Analytics | Sauti" };

export default function AnalyticsPage() {
  return <AnalyticsFeaturePage />;
}
