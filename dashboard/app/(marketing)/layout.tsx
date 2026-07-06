import type { ReactNode } from "react";
import { MarketingFooter, MarketingNav } from "@/features/marketing/MarketingChrome/MarketingChrome";

export default function MarketingLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <MarketingNav />
      {children}
      <MarketingFooter />
    </>
  );
}
