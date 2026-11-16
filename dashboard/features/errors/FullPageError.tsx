"use client";

/* Plain <a> links on purpose: this page may switch the console theme on, and a
   full page load is what resets it before the next page renders. */
/* eslint-disable @next/next/no-html-link-for-pages */

import * as React from "react";
import { ArrowLeft, Home, LayoutDashboard, RotateCcw } from "lucide-react";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import { Button } from "@/components/ui/button";
import { ErrorLinks, ErrorState } from "@/components/ui/error-state";
import { readSession } from "@/lib/session";
import { describeError, type DescribedError } from "./describeError";

const SUPPORT_EMAIL = "support@sauti.uk";

/**
 * Applies the console theme before paint when someone is signed in, so a
 * customer who mistypes a console URL stays in the calm console look; visitors
 * keep the site's own look. Navigation away uses full page loads, which resets it.
 */
export const errorThemeBootScript = `(function(){try{if(!localStorage.getItem("sauti.auth.session"))return;var p=JSON.parse(localStorage.getItem("sauti.console.theme")||"null")||{};var m=p.mode==="dark"||p.mode==="system"?p.mode:"light";var d=m==="dark"||(m==="system"&&matchMedia("(prefers-color-scheme: dark)").matches);var r=document.documentElement;r.classList.add(d?"console-dark":"console-light");r.dataset.accent=["blue","violet","amber","rose","graphite"].indexOf(p.accent)>-1?p.accent:"green";}catch(e){}})();`;

function useSignedIn() {
  const [signedIn, setSignedIn] = React.useState<boolean | null>(null);
  React.useEffect(() => setSignedIn(Boolean(readSession())), []);
  return signedIn;
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas font-sans text-fg antialiased">
      <script dangerouslySetInnerHTML={{ __html: errorThemeBootScript }} />
      <header className="mx-auto flex w-full max-w-5xl items-center justify-between px-5 py-5 sm:px-8">
        <a href="/" className="inline-flex items-center gap-2.5 text-[0.95rem] font-semibold tracking-tight text-ink no-underline">
          <BrandLogo size={28} className="block overflow-hidden rounded-full" />
          Sauti
        </a>
        <a href={`mailto:${SUPPORT_EMAIL}`} className="text-[0.82rem] text-fg-soft no-underline transition-colors hover:text-ink">
          Contact support
        </a>
      </header>
      <main className="flex flex-1 items-center px-5 sm:px-8">{children}</main>
      <footer className="mx-auto w-full max-w-5xl px-5 py-6 text-center text-[0.75rem] text-fg-muted sm:px-8">
        Still stuck? Email <a href={`mailto:${SUPPORT_EMAIL}`} className="text-fg-soft underline underline-offset-2 hover:text-ink">{SUPPORT_EMAIL}</a> and we’ll help.
      </footer>
    </div>
  );
}

function BackButton() {
  return (
    <Button
      variant="secondary"
      size="sm"
      onClick={() => {
        if (window.history.length > 1) window.history.back();
        else window.location.assign("/");
      }}
    >
      <ArrowLeft /> Go back
    </Button>
  );
}

export function NotFoundPage() {
  const signedIn = useSignedIn();
  const links = signedIn
    ? [
        { href: "/agents", label: "Agents", description: "Edit, test and publish your voice agents." },
        { href: "/calls", label: "Calls", description: "Recent conversations and transcripts." },
        { href: "/help", label: "Help centre", description: "Guides and answers to common questions." },
      ]
    : [
        { href: "/solutions", label: "Solutions", description: "How clinics, salons and services use Sauti." },
        { href: "/pricing", label: "Pricing", description: "Plans, minutes and what’s included." },
        { href: "/request-demo", label: "Book a demo", description: "Hear an agent answer like your front desk." },
      ];

  return (
    <Shell>
      <ErrorState
        kind="not-found"
        code="404 · Page not found"
        title="This line isn’t connected"
        description="The page you dialled doesn’t exist or has moved. Check the address, or pick up from one of the pages below."
        className="max-w-2xl"
        actions={
          <>
            <Button asChild size="sm">
              {signedIn ? (
                <a href="/dashboard"><LayoutDashboard /> Go to dashboard</a>
              ) : (
                <a href="/"><Home /> Go to homepage</a>
              )}
            </Button>
            <BackButton />
          </>
        }
      >
        {signedIn !== null ? <ErrorLinks links={links} /> : <div className="mt-8 h-[74px]" aria-hidden="true" />}
      </ErrorState>
    </Shell>
  );
}

export function CrashPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const signedIn = useSignedIn();
  const described = describeError(error);
  React.useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <Shell>
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
              {signedIn ? <a href="/dashboard">Go to dashboard</a> : <a href="/">Go to homepage</a>}
            </Button>
          </>
        }
      />
    </Shell>
  );
}

/** Shared by every error boundary. */
export function runErrorAction(action: DescribedError["action"], reset: () => void) {
  if (action === "reload") window.location.reload();
  else if (action === "login") window.location.assign("/login");
  else React.startTransition(() => reset());
}
