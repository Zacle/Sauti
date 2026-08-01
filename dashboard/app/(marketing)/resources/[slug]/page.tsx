import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { resourceFor, resourceSlugs } from "@/features/marketing/Resources/domain/resource-content";
import { MarketingResourceDetailPage } from "@/features/marketing/Resources/presentation/MarketingResourcesPage";

export function generateStaticParams() {
  return resourceSlugs.map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const resource = resourceFor(slug);
  return resource ? { title: `${resource.label} | Sauti`, description: resource.description } : {};
}

export default async function ResourcesPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const resource = resourceFor(slug);
  if (!resource) {
    notFound();
  }
  return <MarketingResourceDetailPage slug={slug} />;
}
