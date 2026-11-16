"use client";

import { GeistSans } from "geist/font/sans";
import { GeistMono } from "geist/font/mono";
import { CrashPage } from "@/features/errors/FullPageError";
import "../styles/tailwind.css";

/** Last line of defence: the root layout itself failed, so this page brings its own <html>. */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en" className={`${GeistSans.variable} ${GeistMono.variable}`}>
      <head>
        <title>Something went wrong | Sauti</title>
      </head>
      <body style={{ margin: 0 }}>
        <CrashPage error={error} reset={reset} />
      </body>
    </html>
  );
}
