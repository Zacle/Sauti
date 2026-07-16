import type { Metadata } from "next";
import { CategoryScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";

export const metadata: Metadata = {
  title: "Industries | Sauti",
  description: "Explore Sauti AI phone agent use cases by industry.",
};

export default function IndustriesIndexPage() {
  return <CategoryScreen section="industries" />;
}
