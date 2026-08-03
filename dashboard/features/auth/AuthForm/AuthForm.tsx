"use client";

import "./AuthForm.css";
import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  ChevronDown,
  Globe2,
  LoaderCircle,
  MailCheck,
  ShieldCheck,
  UserRound,
  Zap,
} from "lucide-react";
import { authApi, getOnboardingStatus } from "@/lib/api/auth";
import { BrandLogo } from "@/components/BrandLogo/BrandLogo";
import {
  clearPendingEmail,
  clearSession,
  readPendingEmail,
  writePendingEmail,
  writeSession,
} from "@/lib/session";
import { COUNTRIES } from "@/lib/countries";

type AuthMode = "login" | "register" | "verify" | "invite";

export function AuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [businessName, setBusinessName] = useState("");
  const [countryCode, setCountryCode] = useState("KE");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [googleBusy, setGoogleBusy] = useState(false);
  const [googleError, setGoogleError] = useState("");
  const [googleConfigured, setGoogleConfigured] = useState<boolean | null>(null);
  const [invitationLoading, setInvitationLoading] = useState(mode === "invite");
  const isAdminSurface = mode === "login" && searchParams.get("surface") === "admin";
  const businessNameRef = useRef<HTMLInputElement>(null);
  const invitationTokenRef = useRef("");

  useEffect(() => {
    if (mode === "verify") {
      setEmail(searchParams.get("email") ?? readPendingEmail());
    } else if (mode === "login") {
      setEmail(searchParams.get("email") ?? "");
      if (searchParams.get("google") === "cancelled") {
        setError("Google sign-in was cancelled. No account changes were made.");
      }
    }
  }, [mode, searchParams]);

  useEffect(() => {
    if (mode !== "invite") return;
    const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ""));
    const token = fragment.get("token") ?? "";
    invitationTokenRef.current = token;
    window.history.replaceState(null, "", window.location.pathname);
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
      .catch((caught) => setError(caught instanceof Error ? caught.message : "This invitation is unavailable."))
      .finally(() => setInvitationLoading(false));
  }, [mode, searchParams]);

  useEffect(() => {
    if (mode === "verify" || mode === "invite" || isAdminSurface) return;
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (mode === "invite") {
        const result = await authApi.acceptInvitation(invitationTokenRef.current, password);
        writePendingEmail(email);
        router.push(`/verify-email?email=${encodeURIComponent(email)}`);
        if (result.devVerificationCode) setMessage(`Development code: ${result.devVerificationCode}`);
      } else if (mode === "register") {
        const result = await authApi.register({ businessName, email, countryCode, password });
        writePendingEmail(email);
        router.push(`/verify-email?email=${encodeURIComponent(email)}`);
        if (result.devVerificationCode) setMessage(`Development code: ${result.devVerificationCode}`);
      } else if (mode === "verify") {
        await authApi.verifyEmail(email, code);
        clearPendingEmail();
        router.push(`/login?verified=1&email=${encodeURIComponent(email)}`);
      } else {
        const session = await authApi.login(email, password);
        writeSession(session);
        if (isAdminSurface) {
          if (session.role !== "PLATFORM_ADMIN") {
            clearSession();
            setError("This account does not have access to Sauti platform administration.");
            return;
          }
          router.replace("/admin");
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
      setMessage(response.message);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to resend the code.");
    } finally {
      setBusy(false);
    }
  }

  const isLogin = mode === "login";
  const isVerify = mode === "verify";
  const isInvite = mode === "invite";
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
    <main className="app-auth-page">
      <Link className="app-brand" href="/">
        <BrandLogo /><strong>Sauti</strong>
      </Link>
      <section className="auth-shell">
        <div className="auth-copy">
          <span>{isVerify ? "Secure workspace access" : "Sauti voice operations"}</span>
          <h1>
            {isAdminSurface
              ? "Operate Sauti from a protected control center."
              : isInvite
              ? "Your private Sauti pilot workspace is ready."
              : isVerify
              ? "One quick check before your workspace goes live."
              : isLogin
                ? "Your AI phone operations are ready when you are."
                : "AI voice agents. Every language. Any scale."}
          </h1>
          <p>
            Launch natural, multilingual conversations, monitor outcomes, and keep every
            customer interaction moving from one focused workspace.
          </p>
          <div className="auth-waveform" aria-hidden="true">
            <Image
              alt=""
              fill
              priority
              sizes="(max-width: 900px) 0px, 52vw"
              src="/images/marketing/conversation-waveform.png"
            />
          </div>
          <div className="auth-copy-notes">
            <div><Globe2 size={18} /><span><strong>Multilingual by default</strong>Natural conversations across markets.</span></div>
            <div><Zap size={18} /><span><strong>Live operational insight</strong>Calls, transcripts, and outcomes in one place.</span></div>
            <div><ShieldCheck size={18} /><span><strong>Built for trusted access</strong>Workspace-isolated tools and customer data.</span></div>
          </div>
        </div>

        <div className="auth-form-panel">
          <form className="auth-card" onSubmit={submit}>
            <div className="auth-card-head">
              <span>{isVerify ? <MailCheck size={22} /> : <UserRound size={22} />}</span>
              <div>
                <h2>{isVerify ? "Verify your email" : isLogin ? "Welcome back" : "Create your workspace"}</h2>
                <p>
                  {isAdminSurface
                    ? "Sign in with an authorized Sauti platform administrator account."
                    : isInvite
                    ? `Activate ${businessName || "your approved workspace"} with a secure password.`
                    : isVerify
                    ? "Enter the six-digit code sent to your email."
                    : isLogin
                      ? "Sign in to manage your voice agents."
                      : "Start building your first AI voice agent today."}
                </p>
              </div>
            </div>

            {mode === "register" && (
              <div className="auth-field-row">
                <label>
                  Business name
                  <input
                    ref={businessNameRef}
                    required
                    value={businessName}
                    onChange={(event) => {
                      setBusinessName(event.target.value);
                      if (googleError.startsWith("Enter your business")) setGoogleError("");
                    }}
                    placeholder="Acme Health"
                  />
                </label>
                <label>
                  Country
                  <span className="auth-country-select">
                    <Globe2 size={17} />
                    <select aria-label="Country" value={countryCode} onChange={(event) => setCountryCode(event.target.value)}>
                      {COUNTRIES.map((country) => (
                        <option value={country.code} key={country.code}>{country.name}</option>
                      ))}
                    </select>
                    <ChevronDown size={17} />
                  </span>
                </label>
              </div>
            )}

            {!isVerify && !isInvite && !isAdminSurface && (
              <>
                <button
                  className="google-auth-button"
                  disabled={googleBusy || googleConfigured !== true}
                  onClick={continueWithGoogle}
                  type="button"
                >
                  {googleBusy ? <LoaderCircle className="spin" size={18} /> : <span aria-hidden="true">G</span>}
                  {googleConfigured === null
                    ? "Checking Google sign-in…"
                    : googleBusy
                      ? "Opening Google…"
                      : "Continue with Google"}
                </button>
                <p className={`google-auth-helper ${googleError ? "error" : ""}`} role={googleError ? "alert" : undefined}>
                  {googleError || (mode === "register"
                    ? "Your workspace name and country will be used to finish setup."
                    : "Use the Google account connected to your workspace.")}
                </p>
                <div className="auth-divider"><span>or continue with email</span></div>
              </>
            )}

            <label>
              Email
              <input required readOnly={isInvite} type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="owner@company.com" />
            </label>
            {isVerify ? (
              <label>
                Verification code
                <input required inputMode="numeric" minLength={6} maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))} placeholder="123456" />
              </label>
            ) : (
              <label>
                Password
                <input required minLength={8} type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" />
              </label>
            )}

            {error && <div className="form-alert error" role="alert">{error}</div>}
            {message && <div className="form-alert success" role="status">{message}</div>}
            <button className="app-primary-button" disabled={busy || invitationLoading || Boolean(isInvite && error)} type="submit">
              {busy || invitationLoading ? <LoaderCircle className="spin" size={17} /> : null}
              {isVerify ? "Verify email" : isLogin ? "Log in" : isInvite ? "Activate workspace" : "Create workspace"}
              {!busy && <ArrowRight size={17} />}
            </button>

            <p className="auth-switch">
              {isVerify ? (
                <>Didn&apos;t receive it? <button type="button" onClick={resend}>Resend code</button></>
              ) : isInvite ? (
                <>Already activated? <Link href={`/login?email=${encodeURIComponent(email)}`}>Log in</Link></>
              ) : isAdminSurface ? (
                <>Business workspace? <a href="https://sauti.uk/login">Use workspace login</a></>
              ) : isLogin ? (
                <>Interested in Sauti? <Link href="/request-demo">Request a demo</Link></>
              ) : (
                <>Already have an account? <Link href="/login">Log in</Link></>
              )}
            </p>
          </form>
        </div>
      </section>
    </main>
  );
}
