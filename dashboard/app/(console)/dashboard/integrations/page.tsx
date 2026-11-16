import type { Metadata } from "next";
import { Suspense } from "react";
import { IntegrationsPage } from "@/features/integrations/IntegrationsPage/IntegrationsPage";


export const metadata: Metadata = { title: "Integrations | Sauti" };

export default function ConsoleIntegrationsPage() {
  return (
    <Suspense fallback={null}>
      <IntegrationsPage />
    </Suspense>
  );
}
