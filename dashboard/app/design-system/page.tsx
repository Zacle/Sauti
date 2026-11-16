import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DesignSystemShowcase } from "@/features/design-system/DesignSystemShowcase";

export const metadata: Metadata = {
  title: "Design system | Sauti",
  robots: { index: false, follow: false },
};

/** Developer reference for every Sauti UI token and component. Hidden in production builds. */
export default function DesignSystemPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <DesignSystemShowcase />;
}
