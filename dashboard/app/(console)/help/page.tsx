import type { Metadata } from "next";
import { HelpCenter } from "@/features/help/HelpCenter";


export const metadata: Metadata = { title: "Help | Sauti" };

export default function HelpPage() {
  return <HelpCenter />;
}
