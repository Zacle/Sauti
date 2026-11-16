"use client";

import * as React from "react";
import Link from "next/link";
import { ArrowLeft, LayoutDashboard, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { describeError } from "./describeError";
import { runErrorAction } from "./FullPageError";

/** Error inside the console: the sidebar stays, only the page area is replaced. */
export function ConsoleCrash({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const described = describeError(error);
  React.useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <ErrorState
      kind={described.kind}
      code={described.code}
      title={described.title}
      description={described.description}
      reference={described.kind === "error" && described.action === "retry" ? error.digest : undefined}
      className="min-h-[60dvh] justify-center"
      actions={
        <>
          <Button size="sm" onClick={() => runErrorAction(described.action, reset)}>
            <RotateCcw /> {described.actionLabel}
          </Button>
          <Button asChild variant="secondary" size="sm">
            <Link href="/dashboard"><LayoutDashboard /> Go to dashboard</Link>
          </Button>
        </>
      }
    />
  );
}

export function ConsoleNotFound({
  title = "We couldn’t find that page",
  description = "It may have been moved or deleted, or the link is out of date.",
  backHref = "/dashboard",
  backLabel = "Go to dashboard",
}: {
  title?: string;
  description?: string;
  backHref?: string;
  backLabel?: string;
}) {
  return (
    <ErrorState
      kind="not-found"
      code="404 · Not found"
      title={title}
      description={description}
      className="min-h-[60dvh] justify-center"
      actions={
        <>
          <Button asChild size="sm">
            <Link href={backHref}><ArrowLeft /> {backLabel}</Link>
          </Button>
          <Button asChild variant="secondary" size="sm">
            <Link href="/help">Visit help centre</Link>
          </Button>
        </>
      }
    />
  );
}

/**
 * For data that failed to load inside a page (a record, a list). 404s become a
 * friendly "not found", everything else offers a retry.
 */
export function LoadFailure({
  status,
  message,
  onRetry,
  notFoundTitle,
  notFoundDescription,
  backHref,
  backLabel,
}: {
  status?: number;
  message: string;
  onRetry: () => void;
  notFoundTitle: string;
  notFoundDescription: string;
  backHref: string;
  backLabel: string;
}) {
  if (status === 404) {
    return <ConsoleNotFound title={notFoundTitle} description={notFoundDescription} backHref={backHref} backLabel={backLabel} />;
  }
  const offline = status === 0;
  return (
    <ErrorState
      kind={offline ? "offline" : status === 403 ? "forbidden" : "error"}
      code={offline ? "No connection" : status === 403 ? "403 · No access" : "Couldn’t load"}
      title={offline ? "You’re offline" : status === 403 ? "You don’t have access to this" : "This didn’t load"}
      description={message}
      className="min-h-[60dvh] justify-center"
      actions={
        <>
          <Button size="sm" onClick={onRetry}><RotateCcw /> Try again</Button>
          <Button asChild variant="secondary" size="sm">
            <Link href={backHref}><ArrowLeft /> {backLabel}</Link>
          </Button>
        </>
      }
    />
  );
}
