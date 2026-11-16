"use client";

import { ConsoleCrash } from "@/features/errors/ConsoleErrors";

export default function ConsoleError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return <ConsoleCrash error={error} reset={reset} />;
}
