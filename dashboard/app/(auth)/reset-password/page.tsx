import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/features/auth/AuthForm/AuthForm";

export const metadata: Metadata = { title: "Choose a new password | Sauti" };
export default function ResetPasswordPage() {
  return <Suspense><AuthForm mode="reset" /></Suspense>;
}
