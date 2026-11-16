import type { ReactNode } from "react";
import { AppShell } from "@/components/AppShell/AppShell";
import { AuthProvider } from "@/features/auth/AuthProvider/AuthProvider";
import { themeBootScript } from "@/lib/console-theme-boot";

export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return (
    <>
      {/* Applies the saved console theme before first paint, so reloads never flash the wrong colours. */}
      <script dangerouslySetInnerHTML={{ __html: themeBootScript }} />
      <AuthProvider>
        <AppShell>{children}</AppShell>
      </AuthProvider>
    </>
  );
}
