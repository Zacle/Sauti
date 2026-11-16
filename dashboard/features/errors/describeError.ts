import type { ErrorKind } from "@/components/ui/error-state";

export type DescribedError = {
  kind: ErrorKind;
  code: string;
  title: string;
  description: string;
  /** "retry" re-renders the segment; "reload" needs a full page load (new deploy). "login" sends to sign-in. */
  action: "retry" | "reload" | "login";
  actionLabel: string;
};

/** Reads an error caught by an error boundary and decides how to explain it. */
export function describeError(error: (Error & { digest?: string; status?: number }) | null | undefined): DescribedError {
  const message = error?.message ?? "";
  const name = error?.name ?? "";
  const offline = typeof navigator !== "undefined" && navigator.onLine === false;

  if (offline || (error && "status" in error && error.status === 0)) {
    return {
      kind: "offline",
      code: "No connection",
      title: "You’re offline",
      description: "Sauti can’t reach the internet right now. Check your Wi‑Fi or mobile data, then try again. Nothing you saved has been lost.",
      action: "retry",
      actionLabel: "Try again",
    };
  }

  // After a deploy, an open tab can ask for code files that no longer exist.
  if (name === "ChunkLoadError" || /Loading (CSS )?chunk [\w-]+ failed|Failed to fetch dynamically imported module|Importing a module script failed/i.test(message)) {
    return {
      kind: "error",
      code: "Update available",
      title: "Sauti has been updated",
      description: "A newer version was released while this tab was open. Reload to continue with the latest version.",
      action: "reload",
      actionLabel: "Reload page",
    };
  }

  if (error && "status" in error && error.status === 401) {
    return {
      kind: "forbidden",
      code: "Signed out",
      title: "Your session has ended",
      description: "For your security we sign you out after a while. Log in again to pick up where you left off.",
      action: "login",
      actionLabel: "Log in again",
    };
  }

  if (error && "status" in error && error.status === 403) {
    return {
      kind: "forbidden",
      code: "403 · No access",
      title: "You don’t have access to this",
      description: "Your role in this workspace doesn’t include this page. Ask a workspace owner to give you access.",
      action: "retry",
      actionLabel: "Try again",
    };
  }

  return {
    kind: "error",
    code: "Something went wrong",
    title: "This page hit a problem",
    description: "An unexpected error stopped it from loading. It’s not something you did. Try again, and if it keeps happening, contact support.",
    action: "retry",
    actionLabel: "Try again",
  };
}
