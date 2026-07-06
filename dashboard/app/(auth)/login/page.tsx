import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/features/auth/AuthForm/AuthForm";

export const metadata: Metadata = {
  title: "Log in | Sauti",
};

export default function LoginPage() {
  return <Suspense><AuthForm mode="login" /></Suspense>;
}
