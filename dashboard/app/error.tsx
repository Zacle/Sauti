"use client";

import { CrashPage } from "@/features/errors/FullPageError";

/** Catches errors on pages outside the console and marketing groups (auth, onboarding, admin, public call pages). */
export default function RootError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return <CrashPage error={error} reset={reset} />;
}
