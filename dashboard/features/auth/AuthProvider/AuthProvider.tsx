"use client";

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import type { AuthSession, SessionResponse } from "@/types/api";
import {
  announceSignOut,
  clearSession,
  isSignOutSignal,
  readSession,
  selectWorkspace as selectTabWorkspace,
  SESSION_CHANGED_EVENT,
  sessionFromResponse,
  writeSession,
} from "@/lib/session";
import { ensureSession, resetSessionBootstrap } from "@/lib/api/client";
import { authApi } from "@/lib/api/auth";
import { isPreviewMode } from "@/lib/preview/mode";
import { previewSession } from "@/lib/preview/session";

type AuthContextValue = {
  session: AuthSession | null;
  /** True once the session has been restored from the refresh cookie, or found to be absent. */
  ready: boolean;
  logout: () => Promise<void>;
  /** Stores the session returned by sign-in, verification or invitation acceptance. */
  startSession: (response: SessionResponse) => AuthSession;
  updateSession: (session: AuthSession) => void;
  /** Switches this tab to another of the person's workspaces. */
  selectWorkspace: (workspaceId: string) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let active = true;
    const synchronize = () => setSession(readSession() ?? (isPreviewMode() ? previewSession : null));
    const onStorage = (event: StorageEvent) => {
      if (isSignOutSignal(event)) clearSession();
    };
    window.addEventListener(SESSION_CHANGED_EVENT, synchronize);
    window.addEventListener("storage", onStorage);
    void ensureSession().finally(() => {
      if (!active) return;
      synchronize();
      setReady(true);
    });
    return () => {
      active = false;
      window.removeEventListener(SESSION_CHANGED_EVENT, synchronize);
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Signing out locally must still happen if the network call fails.
    }
    clearSession();
    resetSessionBootstrap();
    announceSignOut();
    setSession(null);
  }, []);

  const startSession = useCallback((response: SessionResponse) => {
    const next = sessionFromResponse(response);
    writeSession(next);
    setSession(next);
    return next;
  }, []);

  const updateSession = useCallback((nextSession: AuthSession) => {
    writeSession(nextSession);
    setSession(nextSession);
  }, []);

  const selectWorkspace = useCallback((workspaceId: string) => {
    const switched = selectTabWorkspace(workspaceId);
    if (switched) void authApi.rememberWorkspace(workspaceId).catch(() => undefined);
    return switched;
  }, []);

  const value = useMemo(
    () => ({ session, ready, logout, startSession, updateSession, selectWorkspace }),
    [session, ready, logout, startSession, updateSession, selectWorkspace],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
