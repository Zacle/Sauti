import type { Metadata } from "next";
import { notFound } from "next/navigation";
import {
  marketingIntegrationFor,
  marketingIntegrations,
} from "@/features/marketing/Integrations/domain/integration-content";
import { MarketingIntegrationDetailPage } from "@/features/marketing/Integrations/presentation/MarketingIntegrationsPage";

export function generateStaticParams() {
  return marketingIntegrations.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const integration = marketingIntegrationFor(slug);
  return integration ? { title: `${integration.label} | Sauti`, description: integration.description } : {};
}

export default async function IntegrationsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  if (!marketingIntegrationFor(slug)) {
    notFound();
  }
  return <MarketingIntegrationDetailPage slug={slug} />;
}
