"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { motion, useReducedMotion } from "motion/react";
import { ArrowRight, CircleAlert, LoaderCircle, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Alert, Input, Label } from "@/components/ui/field";
import { AuthShell } from "@/features/auth/AuthShell/AuthShell";
import {
  clearInvitationToken,
  retainInvitationToken,
  WORKSPACE_INVITATION_SESSION_KEY,
} from "@/features/auth/domain/invitation-token";
import { glowPalettes } from "@/features/marketing/VoiceHome/PixelGlow";
import { useAuth } from "@/hooks/useAuth";
import { ApiError, ensureSession } from "@/lib/api/client";
import { authApi, workspacesApi } from "@/lib/api/auth";
import type { InvitationPreview } from "@/types/api";
import { roleLabel } from "../domain/members";

const ease = [0.16, 1, 0.3, 1] as const;
const ACCEPT_PATH = "/invitations/accept";

type Stage =
  | { kind: "loading" }
  | { kind: "unavailable"; message: string }
  | { kind: "join"; invitation: InvitationPreview; signedInAs: string | null };

/**
 * Where a workspace invitation email leads. Four outcomes, decided by who is looking:
 * signed in as the invited email (join with one click), signed in as someone else (sign out
 * first), invited email already has an account (sign in first), or someone new (choose a password).
 */
export function JoinWorkspace() {
  const router = useRouter();
  const reduce = useReducedMotion();
  const { startSession, updateSession, selectWorkspace, logout } = useAuth();
  const [stage, setStage] = useState<Stage>({ kind: "loading" });
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const tokenRef = useRef("");

  useEffect(() => {
    const token = retainInvitationToken(window.location.hash, window.sessionStorage, WORKSPACE_INVITATION_SESSION_KEY);
    tokenRef.current = token;
    if (window.location.hash) window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    if (!token) {
      setStage({ kind: "unavailable", message: "This link is incomplete. Open the invitation email again and use its button." });
      return;
    }
    Promise.all([workspacesApi.previewInvitation(token), ensureSession()])
      .then(([invitation, session]) => setStage({ kind: "join", invitation, signedInAs: session?.account.email ?? null }))
      .catch((caught) => {
        if (caught instanceof ApiError && caught.status >= 400 && caught.status < 500) {
          clearInvitationToken(window.sessionStorage, WORKSPACE_INVITATION_SESSION_KEY);
        }
        setStage({ kind: "unavailable", message: caught instanceof Error ? caught.message : "This invitation is no longer valid." });
      });
  }, []);

  function finish(workspaceId: string) {
    clearInvitationToken(window.sessionStorage, WORKSPACE_INVITATION_SESSION_KEY);
    selectWorkspace(workspaceId);
    router.replace("/dashboard");
  }

  async function joinAsSignedIn() {
    setBusy(true);
    setError("");
    try {
      const joined = await workspacesApi.acceptInvitation(tokenRef.current);
      const session = await ensureSession();
      const account = await authApi.me();
      if (session) updateSession({ ...session, account });
      finish(joined.workspace.id);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to join the workspace.");
      setBusy(false);
    }
  }

  async function createAccountAndJoin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const response = await workspacesApi.acceptInvitationAsNewAccount(tokenRef.current, password);
      const session = startSession(response);
      finish(session.account.lastWorkspaceId ?? session.tenant?.id ?? "");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Unable to create your account.");
      setBusy(false);
    }
  }

  async function signOutFirst() {
    setBusy(true);
    await logout();
    window.location.reload();
  }

  const invitation = stage.kind === "join" ? stage.invitation : null;
  const signedInAs = stage.kind === "join" ? stage.signedInAs : null;
  const wrongAccount = Boolean(invitation && signedInAs && signedInAs.toLowerCase() !== invitation.email.toLowerCase());
  const role = invitation ? roleLabel(invitation.role) : "";

  const title = stage.kind === "loading"
    ? "Opening your invitation"
    : stage.kind === "unavailable"
      ? "This invitation no longer works"
      : wrongAccount
        ? `This invitation is for ${invitation?.email}`
        : `Join ${invitation?.workspaceName}`;
  const description = stage.kind === "loading"
    ? "Checking the link…"
    : stage.kind === "unavailable"
      ? stage.message
      : wrongAccount
        ? `You're signed in as ${signedInAs}. Sign out, then sign in as ${invitation?.email} to join.`
        : signedInAs
          ? `You'll join as ${role}, signed in as ${signedInAs}.`
          : invitation?.accountExists
            ? `You already have a Sauti account. Sign in as ${invitation.email} to join as ${role}.`
            : `Choose a password to create your Sauti account and join as ${role}.`;

  return (
    <AuthShell
      eyebrow="Workspace invitation"
      hero={{ title: "Your team is", accent: "already on Sauti." }}
      glowSeed={2}
      glowPalette={stage.kind === "unavailable" ? glowPalettes.dusk : glowPalettes.signal}
    >
      <motion.div
        initial={reduce ? false : { opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease }}
        className="w-full max-w-[420px]"
      >
        <span className="grid size-12 place-items-center rounded-2xl border border-line-strong bg-white/[0.04] text-fg shadow-[0_0_40px_-10px_rgb(94_234_212/0.4)]">
          {stage.kind === "loading" ? (
            <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
          ) : stage.kind === "unavailable" ? (
            <CircleAlert className="size-5" aria-hidden="true" />
          ) : (
            <Users className="size-5" aria-hidden="true" />
          )}
        </span>
        <h2 className="mt-6 text-3xl font-medium tracking-[-0.03em] text-white">{title}</h2>
        <p className="mt-2 text-[0.95rem] leading-relaxed text-fg-soft" aria-live="polite">{description}</p>

        {stage.kind === "join" && !wrongAccount && signedInAs ? (
          <div className="mt-8 grid gap-4">
            {error ? <Alert tone="error">{error}</Alert> : null}
            <Button size="lg" className="w-full rounded-xl" disabled={busy} onClick={() => void joinAsSignedIn()}>
              {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
              Join workspace
              {!busy ? <ArrowRight aria-hidden="true" /> : null}
            </Button>
          </div>
        ) : null}

        {stage.kind === "join" && wrongAccount ? (
          <div className="mt-8 grid gap-4">
            <Button size="lg" variant="secondary" className="w-full rounded-xl" disabled={busy} onClick={() => void signOutFirst()}>
              {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
              Sign out
            </Button>
          </div>
        ) : null}

        {stage.kind === "join" && !signedInAs && invitation?.accountExists ? (
          <div className="mt-8 grid gap-4">
            <Button asChild size="lg" className="w-full rounded-xl">
              <Link href={`/login?email=${encodeURIComponent(invitation.email)}&next=${encodeURIComponent(ACCEPT_PATH)}`}>
                Sign in to join
                <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
          </div>
        ) : null}

        {stage.kind === "join" && !signedInAs && invitation && !invitation.accountExists ? (
          <form className="mt-8 grid gap-4" onSubmit={createAccountAndJoin}>
            <Label>
              Email
              <Input type="email" readOnly value={invitation.email} autoComplete="email" />
            </Label>
            <Label>
              Password
              <Input
                required
                minLength={8}
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="At least 8 characters"
              />
            </Label>
            {error ? <Alert tone="error">{error}</Alert> : null}
            <Button size="lg" type="submit" className="mt-2 w-full rounded-xl" disabled={busy || password.length < 8}>
              {busy ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
              Create account and join
              {!busy ? <ArrowRight aria-hidden="true" /> : null}
            </Button>
          </form>
        ) : null}

        <p className="mt-8 text-center text-sm text-fg-soft">
          {stage.kind === "unavailable" ? (
            <>Already a member? <Link href="/login" className="font-medium text-white underline-offset-4 hover:underline">Log in</Link></>
          ) : (
            <>Not expecting this? You can close this page. Nothing changes until you join.</>
          )}
        </p>
      </motion.div>
    </AuthShell>
  );
}
