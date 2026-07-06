import type { Metadata } from "next";
import { OnboardingFlow } from "@/features/onboarding/OnboardingFlow/OnboardingFlow";

export const metadata: Metadata = {
  title: "Tenant onboarding | Sauti",
};

export default function OnboardingPage() {
  return <OnboardingFlow />;
}
