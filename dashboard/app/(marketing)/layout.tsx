import type { ReactNode } from "react";
import { MarketingFooter, MarketingNav } from "@/features/marketing/MarketingChrome/MarketingChrome";
import { PublicAnalytics } from "@/features/marketing/PublicAnalytics/PublicAnalytics";

export default function MarketingLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <PublicAnalytics />
      <MarketingNav />
      {children}
      <MarketingFooter />
    </>
  );
}
