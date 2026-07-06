import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DestinationScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";
import { pageFor, paramsFor } from "@/features/marketing/site-map";

export function generateStaticParams() {
  return paramsFor("solutions");
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const destination = pageFor("solutions", slug);
  return destination ? { title: `${destination.title} | Sauti`, description: destination.description } : {};
}

export default async function SolutionsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const destination = pageFor("solutions", slug);
  if (!destination) {
    notFound();
  }
  return <DestinationScreen slug={slug} section="solutions" />;
}
