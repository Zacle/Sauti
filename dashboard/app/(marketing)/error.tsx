"use client";

import * as React from "react";
import Link from "next/link";
import { RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { describeError } from "@/features/errors/describeError";
import { runErrorAction } from "@/features/errors/FullPageError";

/** Marketing pages keep their navigation and footer; only the page body is replaced. */
export default function MarketingError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const described = describeError(error);
  React.useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <div className="mk-v2 px-5 pb-16 pt-28 sm:px-8">
      <ErrorState
        kind={described.kind}
        code={described.code}
        title={described.title}
        description={described.description}
        reference={described.kind === "error" && described.action === "retry" ? error.digest : undefined}
        actions={
          <>
            <Button size="sm" onClick={() => runErrorAction(described.action, reset)}>
              <RotateCcw /> {described.actionLabel}
            </Button>
            <Button asChild variant="secondary" size="sm">
              <Link href="/">Go to homepage</Link>
            </Button>
          </>
        }
      />
    </div>
  );
}
