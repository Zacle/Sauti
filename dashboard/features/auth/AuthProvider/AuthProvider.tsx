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
import type { AuthSession } from "@/types/api";
import { clearSession, readSession, SESSION_CHANGED_EVENT, writeSession } from "@/lib/session";

type AuthContextValue = {
  session: AuthSession | null;
  ready: boolean;
  logout: () => void;
  updateSession: (session: AuthSession) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const synchronize = () => setSession(readSession());
    synchronize();
    setReady(true);
    window.addEventListener(SESSION_CHANGED_EVENT, synchronize);
    window.addEventListener("storage", synchronize);
    return () => {
      window.removeEventListener(SESSION_CHANGED_EVENT, synchronize);
      window.removeEventListener("storage", synchronize);
    };
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const updateSession = useCallback((nextSession: AuthSession) => {
    writeSession(nextSession);
    setSession(nextSession);
  }, []);

  const value = useMemo(
    () => ({ session, ready, logout, updateSession }),
    [session, ready, logout, updateSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
