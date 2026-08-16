import type { AuthSession, OnboardingStatus } from "@/types/api";
import { apiRequest } from "./client";

export const authApi = {
  previewInvitation(token: string) {
    return apiRequest<{ businessName: string; contactName: string; email: string; countryCode: string; expiresAt: string }>(
      "/public/pilot-invitations/preview",
      { headers: { "X-Sauti-Pilot-Invitation": token } },
    );
  },
  acceptInvitation(token: string, password: string) {
    return apiRequest<{ status: string; message: string; devVerificationCode?: string }>(
      "/public/pilot-invitations/accept",
      { method: "POST", headers: { "X-Sauti-Pilot-Invitation": token }, body: JSON.stringify({ password }) },
    );
  },
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
  forgotPassword(email: string) {
    return apiRequest<{ status: string; message: string }>("/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
  },
  resetPassword(email: string, code: string, newPassword: string) {
    return apiRequest<{ status: string; message: string }>("/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ email, code, newPassword }),
    });
  },
  changePassword(currentPassword: string, newPassword: string) {
    return apiRequest<{ status: string; message: string }>("/auth/password", {
      method: "PUT",
      body: JSON.stringify({ currentPassword, newPassword }),
    });
  },
};

export function getOnboardingStatus() {
  return apiRequest<OnboardingStatus>("/tenant/onboarding-status");
}
