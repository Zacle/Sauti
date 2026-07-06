import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DestinationScreen } from "@/features/marketing/DestinationScreen/DestinationScreen";
import { pageFor, paramsFor } from "@/features/marketing/site-map";

export function generateStaticParams() {
  return paramsFor("industries");
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const destination = pageFor("industries", slug);
  return destination ? { title: `${destination.title} | Sauti`, description: destination.description } : {};
}

export default async function IndustriesPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const destination = pageFor("industries", slug);
  if (!destination) {
    notFound();
  }
  return <DestinationScreen slug={slug} section="industries" />;
}
