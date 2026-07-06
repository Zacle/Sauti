import { CreditCard } from "lucide-react";
import { ConsolePlaceholder } from "@/features/console/ConsolePlaceholder/ConsolePlaceholder";

export default function BillingPage() {
  return <ConsolePlaceholder icon={CreditCard} eyebrow="Account" title="Usage and billing" description="Track plan minutes, usage thresholds, and billing history." />;
}
