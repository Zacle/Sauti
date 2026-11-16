import type { Metadata } from "next";
import { AgentCreator } from "@/features/agents/AgentCreator/AgentCreator";

export const metadata: Metadata = { title: "Agent | Sauti" };

export default async function AgentPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ panel?: string }>;
}) {
  const { id } = await params;
  const { panel } = await searchParams;
  return (
    <AgentCreator
      agentId={id}
      openPersonalisation={panel === "personalise"}
      openPhonePicker={panel === "phone"}
    />
  );
}
