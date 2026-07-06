import type { ReactNode } from "react";
import { AppShell } from "@/components/AppShell/AppShell";
import { AuthProvider } from "@/features/auth/AuthProvider/AuthProvider";

export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AppShell>{children}</AppShell>
    </AuthProvider>
  );
}
