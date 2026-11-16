import Image from "next/image";
import type { ReactNode } from "react";
import { INTEGRATION_LOGO_MONO_CLASS, integrationLogo } from "@/features/integrations/domain/logos";

/** A provider's logo, in brand colour where available; falls back to `fallback`. */
export function IntegrationLogo({ provider, size = 22, fallback = null }: { provider: string; size?: number; fallback?: ReactNode }) {
  const logo = integrationLogo(provider);
  if (!logo) return <>{fallback}</>;
  return <Image alt="" src={logo.src} width={size} height={size} className={logo.mono ? INTEGRATION_LOGO_MONO_CLASS : undefined} />;
}
