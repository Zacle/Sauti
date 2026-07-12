"use client";
import { useRevealMotion } from "@/hooks/useRevealMotion";
import ProductHome from "@/features/marketing/ProductHome/ProductHome";

export default function HomePage() {
  useRevealMotion();

  return <ProductHome />;
}
