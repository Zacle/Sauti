import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/features/auth/AuthForm/AuthForm";

export const metadata: Metadata = {
  title: "Verify email | Sauti",
};

export default function VerifyEmailPage() {
  return <Suspense><AuthForm mode="verify" /></Suspense>;
}
