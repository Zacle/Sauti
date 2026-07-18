"use client";

import styles from "./OAuthCallback.module.css";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { CircleAlert, LoaderCircle } from "lucide-react";
import { consumeOAuthSessionFromHash } from "@/lib/session";

const ALLOWED_DESTINATIONS = new Set(["/dashboard", "/agents"]);

export function OAuthCallback() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState("");

  useEffect(() => {
    const session = consumeOAuthSessionFromHash();
    if (!session) {
      setError("Google sign-in did not return a valid session. Please try again.");
      return;
    }
    const requested = searchParams.get("next") ?? "/dashboard";
    router.replace(ALLOWED_DESTINATIONS.has(requested) ? requested : "/dashboard");
  }, [router, searchParams]);

  return (
    <main className={styles.page}>
      <section>
        {error ? <CircleAlert /> : <LoaderCircle className="spin" />}
        <h1>{error ? "Google sign-in failed" : "Finishing Google sign-in"}</h1>
        <p>{error || "Your workspace session is being prepared."}</p>
        {error && <a href="/login">Return to login</a>}
      </section>
    </main>
  );
}
