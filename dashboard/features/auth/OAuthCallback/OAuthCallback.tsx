"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { CircleAlert, LoaderCircle } from "lucide-react";
import { refreshSession } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { PixelGlow, glowPalettes } from "@/features/marketing/VoiceHome/PixelGlow";

const ALLOWED_DESTINATIONS = new Set(["/dashboard", "/agents"]);

/**
 * Google sends the person back here after the backend has set the session cookie. No token is in
 * the URL; the page exchanges the cookie for an access token and continues.
 */
export function OAuthCallback() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    void refreshSession()
      .then((session) => {
        if (!active) return;
        if (!session) {
          setError("Google sign-in did not return a valid session. Please try again.");
          return;
        }
        const requested = searchParams.get("next") ?? "/dashboard";
        router.replace(ALLOWED_DESTINATIONS.has(requested) ? requested : "/dashboard");
      })
      .catch(() => active && setError("Google sign-in did not return a valid session. Please try again."));
    return () => {
      active = false;
    };
  }, [router, searchParams]);

  return (
    <main className="mk-v2 relative isolate grid min-h-dvh place-items-center overflow-hidden bg-canvas px-5">
      <PixelGlow
        seed={2}
        palette={error ? glowPalettes.dusk : glowPalettes.signal}
        intensity={0.7}
        className="pointer-events-none absolute left-1/2 top-1/2 -z-10 h-[34rem] w-[52rem] -translate-x-1/2 -translate-y-1/2 [mask-image:radial-gradient(closest-side,black_20%,transparent)]"
      />
      <section className="flex max-w-sm flex-col items-center text-center" aria-live="polite">
        <span className="grid size-14 place-items-center rounded-2xl border border-line-strong bg-black/60 text-fg backdrop-blur">
          {error ? (
            <CircleAlert className="size-6 text-[#ff9a9a]" aria-hidden="true" />
          ) : (
            <LoaderCircle className="size-6 animate-spin text-signal" aria-hidden="true" />
          )}
        </span>
        <h1 className="mt-6 text-2xl font-medium tracking-tight text-white">
          {error ? "Google sign-in failed" : "Finishing Google sign-in"}
        </h1>
        <p className="mt-2 text-[0.95rem] leading-relaxed text-fg-soft">
          {error || "Your workspace session is being prepared."}
        </p>
        {error && (
          <Button asChild className="mt-8">
            <Link href="/login">Return to login</Link>
          </Button>
        )}
      </section>
    </main>
  );
}
