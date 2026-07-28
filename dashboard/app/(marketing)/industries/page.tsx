import type { Metadata } from "next";
import { IndustriesOverviewPage } from "@/features/marketing/Industries/presentation/IndustriesPage";

export const metadata: Metadata = {
  title: "Industries | Sauti",
  description: "Explore AI phone workflows shaped for healthcare, beauty, real estate, professional services, education, and local businesses.",
};

export default function IndustriesIndexPage() {
  return <IndustriesOverviewPage />;
}
