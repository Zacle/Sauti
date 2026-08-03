import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthForm } from "@/features/auth/AuthForm/AuthForm";

export const metadata: Metadata = {
  title: "Activate your Sauti workspace",
};

export default function AcceptInvitePage() {
  return <Suspense><AuthForm mode="invite" /></Suspense>;
}
