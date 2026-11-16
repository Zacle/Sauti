import type { Metadata } from "next";
import { DashboardOverview } from "@/features/dashboard/DashboardOverview/DashboardOverview";

export const metadata: Metadata = {
  title: "Home | Sauti",
};

export default function DashboardPage() {
  return <DashboardOverview />;
}
