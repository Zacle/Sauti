import type { Metadata } from "next";
import { NotFoundPage } from "@/features/errors/FullPageError";

export const metadata: Metadata = {
  title: "Page not found | Sauti",
  robots: { index: false },
};

export default function NotFound() {
  return <NotFoundPage />;
}
