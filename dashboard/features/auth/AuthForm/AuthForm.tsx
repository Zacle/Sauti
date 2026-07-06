"use client";

import "./AuthForm.css";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import {
  ArrowRight,
  ChevronDown,
  Globe2,
  LoaderCircle,
  LockKeyhole,
  MailCheck,
  PhoneCall,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { authApi, getOnboardingStatus } from "@/lib/api/auth";
import {
  clearPendingEmail,
  readPendingEmail,
  writePendingEmail,
  writeSession,
} from "@/lib/session";
import { COUNTRIES } from "@/lib/countries";

type AuthMode = "login" | "register" | "verify";

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
  const [googleConfigured, setGoogleConfigured] = useState<boolean | null>(null);

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
    if (mode === "verify") return;
    fetch("/api/v1/auth/oauth/google/status")
      .then((response) => response.json())
      .then((body: { configured?: boolean }) => setGoogleConfigured(Boolean(body.configured)))
      .catch(() => setGoogleConfigured(false));
  }, [mode]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      if (mode === "register") {
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
        const onboarding = await getOnboardingStatus();
        router.replace(onboarding.hasAgent ? "/dashboard" : "/onboarding");
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
  const googleQuery = new URLSearchParams({
    businessName: mode === "register" ? businessName : "",
    countryCode: mode === "register" ? countryCode : "",
    returnPath: mode === "register" ? "/onboarding" : "/dashboard",
  });

  return (
    <main className="app-auth-page">
      <Link className="app-brand" href="/">
        <span className="brand-mark">S</span><strong>Sauti</strong>
      </Link>
      <section className="auth-shell">
        <div className="auth-copy">
          <span>{isVerify ? "Email verification" : isLogin ? "Sauti Console" : "Workspace setup"}</span>
          <h1>
            {isVerify
              ? "Verify your email to activate the workspace."
              : isLogin
                ? "Sign in to run your AI phone operations."
                : "Create the workspace for your first AI phone agent."}
          </h1>
          <p>
            Configure multilingual agents, monitor calls, review booking outcomes, and keep
            customer conversations moving from one workspace.
          </p>
          <div className="auth-copy-notes">
            <div><ShieldCheck size={18} /> Tenant-isolated tools and data</div>
            <div><PhoneCall size={18} /> Live calls, transcripts, and outcomes</div>
            <div><LockKeyhole size={18} /> Verified access before production launch</div>
          </div>
        </div>

        <form className="auth-card" onSubmit={submit}>
          <div className="auth-card-head">
            <span>{isVerify ? <MailCheck size={22} /> : <UserRound size={22} />}</span>
            <div>
              <h2>{isVerify ? "Verify email" : isLogin ? "Log in" : "Create account"}</h2>
              <p>{isVerify ? "Enter the six-digit code sent to your email." : "Use your workspace credentials."}</p>
            </div>
          </div>

          {!isVerify && (
            <>
              <a
                aria-disabled={googleConfigured === false}
                className={`google-auth-button ${googleConfigured === false ? "disabled" : ""}`}
                href={`/api/v1/auth/oauth/google/authorize?${googleQuery.toString()}`}
                onClick={(event) => {
                  if (mode === "register" && !businessName.trim()) {
                    event.preventDefault();
                    setError("Enter your business name before continuing with Google.");
                    return;
                  }
                  if (googleConfigured === false) {
                    event.preventDefault();
                    setError("Google sign-in is not configured. Add the Google OAuth client ID and secret to the backend environment.");
                  }
                }}
              >
                <span>G</span> Continue with Google
              </a>
              <div className="auth-divider"><span>or continue with email</span></div>
            </>
          )}

          {mode === "register" && (
            <div className="auth-field-row">
              <label>Business name<input required value={businessName} onChange={(e) => setBusinessName(e.target.value)} placeholder="Acme Health" /></label>
              <label>Country
                <span className="auth-country-select">
                  <Globe2 size={17} />
                  <select aria-label="Country" value={countryCode} onChange={(e) => setCountryCode(e.target.value)}>
                    {COUNTRIES.map((country) => (
                      <option value={country.code} key={country.code}>{country.name}</option>
                    ))}
                  </select>
                  <ChevronDown size={17} />
                </span>
              </label>
            </div>
          )}
          <label>Email<input required type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="owner@company.com" /></label>
          {isVerify ? (
            <label>Verification code<input required inputMode="numeric" minLength={6} maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} placeholder="123456" /></label>
          ) : (
            <label>Password<input required minLength={8} type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="At least 8 characters" /></label>
          )}

          {error && <div className="form-alert error" role="alert">{error}</div>}
          {message && <div className="form-alert success" role="status">{message}</div>}
          <button className="app-primary-button" disabled={busy} type="submit">
            {busy ? <LoaderCircle className="spin" size={17} /> : null}
            {isVerify ? "Verify email" : isLogin ? "Log in" : "Create workspace"}
            {!busy && <ArrowRight size={17} />}
          </button>

          <p className="auth-switch">
            {isVerify ? (
              <>Didn&apos;t receive it? <button type="button" onClick={resend}>Resend code</button></>
            ) : isLogin ? (
              <>New to Sauti? <Link href="/register">Create workspace</Link></>
            ) : (
              <>Already have an account? <Link href="/login">Log in</Link></>
            )}
          </p>
        </form>
      </section>
    </main>
  );
}
