import { Settings } from "lucide-react";
import { ConsolePlaceholder } from "@/features/console/ConsolePlaceholder/ConsolePlaceholder";

export default function SettingsPage() {
  return <ConsolePlaceholder icon={Settings} eyebrow="Workspace" title="Settings" description="Manage workspace defaults, team access, and operational preferences." />;
}
