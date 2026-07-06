import { Suspense } from "react";
import { OAuthCallback } from "@/features/auth/OAuthCallback/OAuthCallback";

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={null}>
      <OAuthCallback />
    </Suspense>
  );
}
