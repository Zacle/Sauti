import type { ReactNode } from "react";
import { AuthProvider } from "@/features/auth/AuthProvider/AuthProvider";

export default function OnboardingLayout({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}
