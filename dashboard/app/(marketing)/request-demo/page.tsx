import type { Metadata } from "next";
import { DemoRequestPage } from "@/features/demo-request/presentation/DemoRequestPage";

export const metadata: Metadata = {
  title: "Request a demo | Sauti",
  description: "Request a tailored demonstration of Sauti's multilingual AI voice agents without creating a workspace.",
};

export default function RequestDemoPage() {
  return <DemoRequestPage />;
}
