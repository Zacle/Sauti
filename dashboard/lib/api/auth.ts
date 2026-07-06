import type { AuthSession, OnboardingStatus } from "@/types/api";
import { apiRequest } from "./client";

export const authApi = {
  register(payload: { businessName: string; email: string; countryCode: string; password: string }) {
    return apiRequest<{ status: string; message: string; devVerificationCode?: string }>(
      "/auth/register",
      { method: "POST", body: JSON.stringify(payload) },
    );
  },
  login(email: string, password: string) {
    return apiRequest<AuthSession>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  },
  verifyEmail(email: string, code: string) {
    return apiRequest<{ status: string; message: string }>("/auth/verify-email", {
      method: "POST",
      body: JSON.stringify({ email, code }),
    });
  },
  resendVerification(email: string) {
    return apiRequest<{ status: string; message: string }>("/auth/resend-verification", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
  },
};

export function getOnboardingStatus() {
  return apiRequest<OnboardingStatus>("/tenant/onboarding-status");
}
