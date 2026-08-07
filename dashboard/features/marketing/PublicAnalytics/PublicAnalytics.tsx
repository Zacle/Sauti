"use client";

import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";
import { trackPublicAnalyticsEvent } from "@/lib/api/public-analytics";

export function PublicAnalytics() {
  const pathname = usePathname();
  const lastPath = useRef("");
  useEffect(() => {
    if (!pathname || lastPath.current === pathname) return;
    lastPath.current = pathname;
    trackPublicAnalyticsEvent("page_view", pathname);
  }, [pathname]);
  return null;
}

