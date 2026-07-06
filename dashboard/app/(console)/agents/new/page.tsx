import type { Metadata } from "next";
import { AgentCreator } from "@/features/agents/AgentCreator/AgentCreator";

export const metadata: Metadata = {
  title: "Create agent | Sauti",
};

export default function NewAgentPage() {
  return <AgentCreator />;
}
