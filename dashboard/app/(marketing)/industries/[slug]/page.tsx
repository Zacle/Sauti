import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { industryFor, industries } from "@/features/marketing/Industries/domain/industry-content";
import { IndustryDetailPage } from "@/features/marketing/Industries/presentation/IndustriesPage";

export function generateStaticParams() {
  return industries.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const industry = industryFor(slug);
  return industry ? { title: `${industry.label} AI Phone Agent | Sauti`, description: industry.description } : {};
}

export default async function IndustriesPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  if (!industryFor(slug)) {
    notFound();
  }
  return <IndustryDetailPage slug={slug} />;
}
