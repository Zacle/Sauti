import { CircleHelp } from "lucide-react";
import { ConsolePlaceholder } from "@/features/console/ConsolePlaceholder/ConsolePlaceholder";

export default function HelpPage() {
  return <ConsolePlaceholder icon={CircleHelp} eyebrow="Resources" title="Help center" description="Find setup guidance and troubleshooting resources." />;
}
