import type { ReactNode } from "react";
import { AuthProvider } from "@/features/auth/AuthProvider/AuthProvider";
import { AdminShell } from "@/features/admin/presentation/AdminShell";

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AuthProvider><AdminShell>{children}</AdminShell></AuthProvider>;
}
