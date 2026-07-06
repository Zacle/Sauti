"use client";

import { useRevealMotion } from "@/hooks/useRevealMotion";
import { HeroSection } from "@/features/marketing/HeroSection/HeroSection";
import {
  FaqSection,
  FinalCta,
  IntegrationsSection,
  LiveOpsSection,
  MetricsSection,
  PartnerStrip,
  ProductionSection,
  SmartSection,
  WorkflowSection,
} from "@/features/marketing/HomeSections/HomeSections";

export default function HomePage() {
  useRevealMotion();

  return (
    <main>
      <HeroSection />
      <PartnerStrip />
      <MetricsSection />
      <WorkflowSection />
      <LiveOpsSection />
      <SmartSection />
      <ProductionSection />
      <IntegrationsSection />
      <FaqSection />
      <FinalCta />
    </main>
  );
}
