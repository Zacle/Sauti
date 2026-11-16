import type { Metadata } from "next";
import { CallsPage as CallsHistory } from "@/features/calls/CallsPage/CallsPage";


export const metadata: Metadata = { title: "Calls | Sauti" };

export default function CallsPage() {
  return <CallsHistory />;
}
