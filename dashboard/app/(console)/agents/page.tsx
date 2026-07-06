import type { Metadata } from "next";
import { AgentsPage } from "@/features/agents/AgentList/AgentList";

export const metadata: Metadata = {
  title: "Agents | Sauti",
};

export default function AgentsRoute() {
  return <AgentsPage />;
}
