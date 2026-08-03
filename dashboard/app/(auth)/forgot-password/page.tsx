import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/features/auth/AuthForm/AuthForm";

export const metadata: Metadata = { title: "Reset your password | Sauti" };
export default function ForgotPasswordPage() {
  return <Suspense><AuthForm mode="forgot" /></Suspense>;
}
