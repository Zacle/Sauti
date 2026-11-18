"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useEffect, useRef, useState } from "react";
import { motion, useReducedMotion } from "motion/react";
import {
  ArrowRight,
  KeyRound,
  LoaderCircle,
  MailCheck,
  UserRound,
} from "lucide-react";
import { authApi, getOnboardingStatus } from "@/lib/api/auth";
import {
  clearPendingEmail,
  clearSession,
  readPendingEmail,
  sessionFromResponse,
  writePendingEmail,
  writeSession,
} from "@/lib/session";
import { COUNTRIES } from "@/lib/countries";
import { ApiError } from "@/lib/api/client";
import {
  clearInvitationToken,
  retainInvitationToken,
} from "@/features/auth/domain/invitation-token";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { toast } from "@/components/ui/sonner";
import { glowPalettes } from "@/features/marketing/VoiceHome/PixelGlow";
import { AuthShell } from "@/features/auth/AuthShell/AuthShell";
import { AdminTwoFactor } from "@/features/auth/AdminTwoFactor/AdminTwoFactor";
import type { AdminChallenge } from "@/types/api";

type AuthMode = "login" | "register" | "verify" | "invite" | "forgot" | "reset";

const ease = [0.16, 1, 0.3, 1] as const;
const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({ value: country.code, label: country.name, keywords: [country.code] }));

export function AuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const reduce = useReducedMotion();
  const [businessName, setBusinessName] = useState("");
  const [countryCode, setCountryCode] = useState("KE");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [googleBusy, setGoogleBusy] = useState(false);
  const [googleError, setGoogleError] = useState("");
  const [googleConfigured, setGoogleConfigured] = useState<boolean | null>(null);
  const [invitationLoading, setInvitationLoading] = useState(mode === "invite");
  const [adminChallenge, setAdminChallenge] = useState<AdminChallenge | null>(null);
  const isAdminSurface = searchParams.get("surface") === "admin";
  const businessNameRef = useRef<HTMLInputElement>(null);
  const invitationTokenRef = useRef("");
  const invitationInitializedRef = useRef(false);

  useEffect(() => {
    if (mode === "verify" || mode === "forgot" || mode === "reset") {
      setEmail(searchParams.get("email") ?? readPendingEmail());
      if (mode === "reset" && searchParams.get("requested") === "1") {
        const text = "If the account exists, a six-digit reset code has been sent. The code expires in 15 minutes.";
        toast.success("Check your email", { id: "reset-requested", description: text });
      }
    } else if (mode === "login") {
      setEmail(searchParams.get("email") ?? "");
      if (searchParams.get("google") === "cancelled") {
        setError("Google sign-in was cancelled. No account changes were made.");
      }
      if (searchParams.get("reset") === "1") {
        toast.success("Password updated", { id: "password-reset", description: "Sign in with your new password." });
      }
      if (searchParams.get("verified") === "1") {
        toast.success("Email verified", { id: "email-verified", description: "Log in to finish setting up your workspace." });
      }
    }
  }, [mode, searchParams]);

  useEffect(() => {
    if (mode !== "invite" || invitationInitializedRef.current) return;
    invitationInitializedRef.current = true;

    const token = retainInvitationToken(window.location.hash, window.sessionStorage);
    invitationTokenRef.current = token;
    if (window.location.hash) {
      window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    }
    if (!token) {
      setError("This invitation link is invalid.");
      setInvitationLoading(false);
      return;
    }
    authApi.previewInvitation(token)
      .then((invitation) => {
        setBusinessName(invitation.businessName);
        setCountryCode(invitation.countryCode);
        setEmail(invitation.email);
      })
      .catch((caught) => {
        if (caught instanceof ApiError && caught.status >= 400 && caught.status < 500) {
          clearInvitationToken(window.sessionStorage);
          invitationTokenRef.current = "";
        }
        setError(caught instanceof Error ? caught.message : "This invitation is unavailable.");
      })
      .finally(() => setInvitationLoading(false));
  }, [mode]);

  useEffect(() => {
    if (mode !== "login" && mode !== "register") return;
    if (isAdminSurface) return;
    fetch("/api/v1/auth/oauth/google/status")
      .then((response) => response.json())
      .then((body: { configured?: boolean }) => {
        const configured = Boolean(body.configured);
        setGoogleConfigured(configured);
        if (!configured) {
          setGoogleError("Google sign-in is temporarily unavailable. Please continue with email.");
        }
      })
      .catch(() => {
        setGoogleConfigured(false);
        setGoogleError("Google sign-in is temporarily unavailable. Please continue with email.");
      });
  }, [isAdminSurface, mode]);

  // Dev-only verification codes outlive the navigation to /verify-email as a toast.
  function announceDevCode(devCode?: string | null) {
    if (!devCode) return;
    toast.info(`Development code: ${devCode}`, { id: "dev-code", duration: 30_000 });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (mode === "forgot") {
        await authApi.forgotPassword(email);
        writePendingEmail(email);
        const query = new URLSearchParams({ email, requested: "1" });
        if (isAdminSurface) query.set("surface", "admin");
        router.push(`/reset-password?${query.toString()}`);
      } else if (mode === "reset") {
        await authApi.resetPassword(email, code, password);
        clearPendingEmail();
        clearSession();
        const query = new URLSearchParams({ email, reset: "1" });
        if (isAdminSurface) query.set("surface", "admin");
        router.push(`/login?${query.toString()}`);
      } else if (mode === "invite") {
        const result = await authApi.acceptInvitation(invitationTokenRef.current, password);
        clearInvitationToken(window.sessionStorage);
        invitationTokenRef.current = "";
        writePendingEmail(email);
        router.push(`/verify-email?email=${encodeURIComponent(email)}`);
        announceDevCode(result.devVerificationCode);
      } else if (mode === "register") {
        const result = await authApi.register({ businessName, email, countryCode, password });
        writePendingEmail(email);
        router.push(`/verify-email?email=${encodeURIComponent(email)}`);
        announceDevCode(result.devVerificationCode);
      } else if (mode === "verify") {
        await authApi.verifyEmail(email, code);
        clearPendingEmail();
        router.push(`/login?verified=1&email=${encodeURIComponent(email)}`);
      } else {
        if (isAdminSurface) {
          // Administration never uses the normal session: password first, then an authenticator code.
          setAdminChallenge(await authApi.adminLogin(email, password));
          setPassword("");
          return;
        }
        const session = sessionFromResponse(await authApi.login(email, password));
        writeSession(session);
        if (searchParams.get("next") === "/invitations/accept") {
          router.replace("/invitations/accept");
          return;
        }
        if (!session.tenant) {
          router.replace("/dashboard");
          return;
        }
        const onboarding = await getOnboardingStatus();
        router.replace(onboarding.hasAgent ? "/dashboard" : "/agents");
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    if (!email) return;
    setBusy(true);
    setError("");
    try {
      const response = await authApi.resendVerification(email);
      toast.success("Code sent", { description: response.message });
    } catch (caught) {
      const text = caught instanceof Error ? caught.message : "Unable to resend the code.";
      setError(text);
      toast.error("Couldn't resend the code", { description: text });
    } finally {
      setBusy(false);
    }
  }

  const isLogin = mode === "login";
  const isVerify = mode === "verify";
  const isInvite = mode === "invite";
  const isForgot = mode === "forgot";
  const isReset = mode === "reset";
  const recoverySurface = isAdminSurface ? "&surface=admin" : "";
  const isRecovery = isForgot || isReset;
  const showGoogle = !isVerify && !isInvite && !isRecovery && !isAdminSurface;

  const hero = isForgot
    ? { title: "Recover access", accent: "without the risk." }
    : isReset
      ? { title: "A fresh password,", accent: "in a minute." }
      : isAdminSurface
        ? { title: "Operate Sauti from a", accent: "protected console." }
        : isInvite
          ? { title: "Your pilot workspace", accent: "is ready." }
          : isVerify
            ? { title: "One quick check", accent: "before you go live." }
            : isLogin
              ? { title: "Your phones are answered.", accent: "Welcome back." }
              : { title: "Every call answered,", accent: "in every language." };

  const cardTitle = isForgot ? "Forgot your password?" : isReset ? "Set a new password" : isVerify
    ? "Verify your email" : isInvite ? "Activate your workspace" : isLogin ? "Welcome back" : "Create your workspace";
  const cardDescription = isForgot
    ? "Enter your account email. If it exists, we will send a six-digit reset code."
    : isReset
      ? "Enter the reset code from your email and choose a new password."
      : isAdminSurface
        ? "Sign in with an authorized Sauti platform administrator account."
        : isInvite
          ? `Activate ${businessName || "your approved workspace"} with a secure password.`
          : isVerify
            ? "Enter the six-digit code sent to your email."
            : isLogin
              ? "Sign in to manage your voice agents."
              : "Start building your first AI voice agent today.";
  const submitLabel = isForgot ? "Send reset code" : isReset ? "Update password" : isVerify
    ? "Verify email" : isLogin ? "Log in" : isInvite ? "Activate workspace" : "Create workspace";
  const CardIcon = isRecovery ? KeyRound : isVerify ? MailCheck : UserRound;

  const googleQuery = new URLSearchParams({
    businessName: mode === "register" ? businessName : "",
    countryCode: mode === "register" ? countryCode : "",
    returnPath: mode === "register" ? "/onboarding" : "/dashboard",
  });
  const googleUrl = `/api/v1/auth/oauth/google/authorize?${googleQuery.toString()}`;

  function continueWithGoogle() {
    setError("");
    setGoogleError("");

    if (mode === "register" && !businessName.trim()) {
      setGoogleError("Enter your business name before continuing with Google.");
      businessNameRef.current?.focus();
      return;
    }

    if (!googleConfigured) {
      setGoogleError("Google sign-in is temporarily unavailable. Please continue with email.");
      return;
    }

    setGoogleBusy(true);
    window.location.assign(googleUrl);
  }

  return (
    <AuthShell
      eyebrow={isVerify || isRecovery ? "Secure workspace access" : isAdminSurface ? "Platform administration" : "Sauti voice operations"}
      hero={hero}
      glowSeed={isLogin ? 1 : isRecovery ? 4 : 2}
      glowPalette={isRecovery || isVerify ? glowPalettes.ocean : glowPalettes.signal}
    >
          {adminChallenge ? (
            <AdminTwoFactor
              challenge={adminChallenge}
              onRestart={() => setAdminChallenge(null)}
              onSignedIn={(response) => {
                writeSession(sessionFromResponse(response));
                router.replace("/admin");
              }}
            />
          ) : (
          <motion.form
            onSubmit={submit}
            initial={reduce ? false : { opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease }}
            className="w-full max-w-[420px]"
          >
            <span className="grid size-12 place-items-center rounded-2xl border border-line-strong bg-white/[0.04] text-fg shadow-[0_0_40px_-10px_rgb(94_234_212/0.4)]">
              <CardIcon className="size-5" aria-hidden="true" />
            </span>
            <h2 className="mt-6 text-3xl font-medium tracking-[-0.03em] text-white">{cardTitle}</h2>
            <p className="mt-2 text-[0.95rem] leading-relaxed text-fg-soft">{cardDescription}</p>

            <div className="mt-8 grid gap-4">
              {mode === "register" && (
                <div className="grid gap-4 sm:grid-cols-2">
                  <Label>
                    Business name
                    <Input
                      ref={businessNameRef}
                      required
                      value={businessName}
                      autoComplete="organization"
                      onChange={(event) => {
                        setBusinessName(event.target.value);
                        if (googleError.startsWith("Enter your business")) setGoogleError("");
                      }}
                      placeholder="Acme Health"
                    />
                  </Label>
                  <Label>
                    Country
                    <Select ariaLabel="Country" value={countryCode} onValueChange={setCountryCode} searchPlaceholder="Search countries…" options={COUNTRY_OPTIONS} />
                  </Label>
                </div>
              )}

              {showGoogle && (
                <>
                  <button
                    className="inline-flex h-11 w-full items-center justify-center gap-3 rounded-xl border border-line-strong bg-white/[0.03] text-[0.95rem] font-medium text-fg transition-colors hover:border-white/25 hover:bg-white/[0.06] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal disabled:cursor-not-allowed disabled:opacity-55"
                    disabled={googleBusy || googleConfigured !== true}
                    onClick={continueWithGoogle}
                    type="button"
                  >
                    {googleBusy ? <LoaderCircle className="size-[18px] animate-spin" aria-hidden="true" /> : <GoogleMark />}
                    {googleConfigured === null
                      ? "Checking Google sign-in…"
                      : googleBusy
                        ? "Opening Google…"
                        : "Continue with Google"}
                  </button>
                  <p
                    className={googleError ? "-mt-1 text-xs text-[#ffb4b4]" : "-mt-1 text-xs text-fg-muted"}
                    role={googleError ? "alert" : undefined}
                  >
                    {googleError || (mode === "register"
                      ? "Your workspace name and country will be used to finish setup."
                      : "Use the Google account connected to your workspace.")}
                  </p>
                  <div className="my-1 flex items-center gap-3 font-mono text-[0.62rem] uppercase tracking-[0.16em] text-fg-muted">
                    <span className="h-px flex-1 bg-line" />
                    or continue with email
                    <span className="h-px flex-1 bg-line" />
                  </div>
                </>
              )}

              <Label>
                Email
                <Input
                  required
                  readOnly={isInvite}
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="owner@company.com"
                />
              </Label>
              {(isVerify || isReset) && (
                <Label>
                  {isReset ? "Reset code" : "Verification code"}
                  <Input
                    required
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
              )}
              {!isVerify && !isForgot && (
                <Label>
                  <span className="flex items-baseline justify-between">
                    {isReset ? "New password" : "Password"}
                    {isLogin && (
                      <Link
                        className="text-xs font-normal text-fg-soft transition-colors hover:text-white"
                        href={`/forgot-password?email=${encodeURIComponent(email)}${recoverySurface}`}
                      >
                        Forgot password?
                      </Link>
                    )}
                  </span>
                  <Input
                    required
                    minLength={8}
                    type="password"
                    autoComplete={isLogin ? "current-password" : "new-password"}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="At least 8 characters"
                  />
                </Label>
              )}

              {error && <Alert tone="error">{error}</Alert>}

              <Button
                size="lg"
                className="mt-2 w-full rounded-xl"
                disabled={busy || invitationLoading || Boolean(isInvite && error)}
                type="submit"
              >
                {busy || invitationLoading ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
                {submitLabel}
                {!busy && <ArrowRight aria-hidden="true" />}
              </Button>
            </div>

            <p className="mt-8 text-center text-sm text-fg-soft">
              {isForgot ? (
                <>Remembered it? <AuthLink href={`/login?email=${encodeURIComponent(email)}${recoverySurface}`}>Return to login</AuthLink></>
              ) : isReset ? (
                <>Need another code? <AuthLink href={`/forgot-password?email=${encodeURIComponent(email)}${recoverySurface}`}>Send a new one</AuthLink></>
              ) : isVerify ? (
                <>Didn&apos;t receive it?{" "}
                  <button type="button" onClick={resend} disabled={busy} className="font-medium text-white underline-offset-4 hover:underline disabled:opacity-60">
                    Resend code
                  </button>
                </>
              ) : isInvite ? (
                <>Already activated? <AuthLink href={`/login?email=${encodeURIComponent(email)}`}>Log in</AuthLink></>
              ) : isAdminSurface ? (
                <>Business workspace? <a className="font-medium text-white underline-offset-4 hover:underline" href="https://sauti.uk/login">Use workspace login</a></>
              ) : isLogin ? (
                <>Interested in Sauti? <AuthLink href="/request-demo">Request a demo</AuthLink></>
              ) : (
                <>Already have an account? <AuthLink href="/login">Log in</AuthLink></>
              )}
            </p>
          </motion.form>
          )}
    </AuthShell>
  );
}

function AuthLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href} className="font-medium text-white underline-offset-4 hover:underline">
      {children}
    </Link>
  );
}

function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-[18px]" aria-hidden="true">
      <path fill="#4285F4" d="M23.5 12.27c0-.85-.08-1.67-.22-2.45H12v4.64h6.46a5.52 5.52 0 0 1-2.4 3.62v3h3.88c2.27-2.09 3.56-5.17 3.56-8.81Z" />
      <path fill="#34A853" d="M12 24c3.24 0 5.96-1.07 7.94-2.91l-3.88-3c-1.07.72-2.45 1.15-4.06 1.15-3.12 0-5.77-2.11-6.71-4.95H1.28v3.1A12 12 0 0 0 12 24Z" />
      <path fill="#FBBC05" d="M5.29 14.29A7.2 7.2 0 0 1 4.91 12c0-.8.14-1.57.38-2.29v-3.1H1.28A12 12 0 0 0 0 12c0 1.94.46 3.77 1.28 5.39l4.01-3.1Z" />
      <path fill="#EA4335" d="M12 4.77c1.76 0 3.34.61 4.59 1.8l3.44-3.44A11.5 11.5 0 0 0 12 0 12 12 0 0 0 1.28 6.61l4.01 3.1C6.23 6.88 8.88 4.77 12 4.77Z" />
    </svg>
  );
}
