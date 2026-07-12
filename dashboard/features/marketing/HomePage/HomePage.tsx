"use client";
import { useRevealMotion } from "@/hooks/useRevealMotion";
import ReferenceHome from "@/features/marketing/ReferenceHome/ReferenceHome";

export default function HomePage() {
  useRevealMotion();

  return <ReferenceHome />;
}
