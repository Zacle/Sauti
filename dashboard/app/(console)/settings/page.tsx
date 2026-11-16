import type { Metadata } from "next";
import { WorkspaceSettings } from "@/features/settings/presentation/WorkspaceSettings";


export const metadata: Metadata = { title: "Settings | Sauti" };

export default function SettingsPage() {
  return <WorkspaceSettings />;
}
