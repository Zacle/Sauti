import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/features/auth/AuthForm/AuthForm";

export const metadata: Metadata = {
  title: "Create workspace | Sauti",
};

export default function RegisterPage() {
  return <Suspense><AuthForm mode="register" /></Suspense>;
}
