import { Suspense } from "react";
import { IntegrationsPage } from "@/features/integrations/IntegrationsPage/IntegrationsPage";

export default function ConsoleIntegrationsPage() {
  return (
    <Suspense fallback={null}>
      <IntegrationsPage />
    </Suspense>
  );
}
