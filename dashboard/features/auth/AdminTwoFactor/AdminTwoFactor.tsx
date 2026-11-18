"use client";

import { useEffect, useState, type FormEvent } from "react";
import { ArrowRight, Copy, KeyRound, LoaderCircle, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label } from "@/components/ui/field";
import { authApi } from "@/lib/api/auth";
import type { AdminChallenge, AuthenticatorSetup, SessionResponse } from "@/types/api";

/**
 * Administration sign-in, step two. With an authenticator already set up, it asks for the current
 * 6-digit code. Otherwise it first shows the setup key to add to an authenticator app; the first
 * correct code confirms it.
 */
export function AdminTwoFactor({
  challenge,
  onSignedIn,
  onRestart,
}: {
  challenge: AdminChallenge;
  onSignedIn: (session: SessionResponse) => void;
  onRestart: () => void;
}) {
  const [setup, setSetup] = useState<AuthenticatorSetup | null>(null);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);
  const needsSetup = !challenge.authenticatorReady;

  useEffect(() => {
    if (!needsSetup) return;
    authApi.adminAuthenticatorSetup(challenge.challenge)
      .then(setSetup)
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Unable to start authenticator setup."));
  }, [challenge.challenge, needsSetup]);

  async function verify(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      onSignedIn(await authApi.adminVerify(challenge.challenge, code));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "That code didn't work.");
      setCode("");
      setBusy(false);
    }
  }

  async function copySecret() {
    if (!setup) return;
    try {
      await navigator.clipboard.writeText(setup.secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <form onSubmit={verify} className="w-full max-w-[420px]">
      <span className="grid size-12 place-items-center rounded-2xl border border-line-strong bg-white/[0.04] text-fg shadow-[0_0_40px_-10px_rgb(94_234_212/0.4)]">
        {needsSetup ? <KeyRound className="size-5" aria-hidden="true" /> : <ShieldCheck className="size-5" aria-hidden="true" />}
      </span>
      <h2 className="mt-6 text-3xl font-medium tracking-[-0.03em] text-white">
        {needsSetup ? "Set up your authenticator" : "Enter your code"}
      </h2>
      <p className="mt-2 text-[0.95rem] leading-relaxed text-fg-soft">
        {needsSetup
          ? "Administration needs a second step. Add Sauti to an authenticator app such as Google Authenticator, 1Password or Authy, then enter the 6-digit code it shows."
          : "Open your authenticator app and enter the current 6-digit code for Sauti Admin."}
      </p>

      <div className="mt-8 grid gap-4">
        {needsSetup ? (
          setup ? (
            <div className="grid gap-3 rounded-xl border border-line-strong bg-white/[0.03] p-4">
              <span className="text-sm text-fg-soft">Setup key</span>
              <code className="break-all font-mono text-[0.95rem] tracking-[0.12em] text-white">
                {setup.secret.replace(/(.{4})/g, "$1 ").trim()}
              </code>
              <div className="flex flex-wrap gap-2">
                <Button type="button" size="xs" variant="secondary" onClick={() => void copySecret()}>
                  <Copy aria-hidden="true" />
                  {copied ? "Copied" : "Copy key"}
                </Button>
                <Button asChild size="xs" variant="ghost">
                  <a href={setup.otpauthUri}>Open in authenticator app</a>
                </Button>
              </div>
              <p className="m-0 text-xs leading-relaxed text-fg-muted">
                In the app, choose to add an account by entering a key. Use time-based codes.
              </p>
            </div>
          ) : !error ? (
            <p className="flex items-center gap-2 text-sm text-fg-soft">
              <LoaderCircle className="size-4 animate-spin" aria-hidden="true" /> Preparing your setup key…
            </p>
          ) : null
        ) : null}

        <Label>
          6-digit code
          <Input
            required
            autoFocus={!needsSetup}
            inputMode="numeric"
            autoComplete="one-time-code"
            minLength={6}
            maxLength={6}
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
            placeholder="123456"
            className="h-14 text-center font-mono text-2xl tracking-[0.5em] placeholder:tracking-[0.5em]"
          />
        </Label>

        {error ? <Alert tone="error">{error}</Alert> : null}

        <Button size="lg" type="submit" className="mt-2 w-full rounded-xl" disabled={busy || code.length !== 6}>
          {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
          {needsSetup ? "Confirm and sign in" : "Sign in"}
          {!busy ? <ArrowRight aria-hidden="true" /> : null}
        </Button>
      </div>

      <p className="mt-8 text-center text-sm text-fg-soft">
        Wrong account?{" "}
        <button type="button" onClick={onRestart} className="font-medium text-white underline-offset-4 hover:underline">
          Start again
        </button>
      </p>
    </form>
  );
}
