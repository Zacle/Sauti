import type {
  Account,
  ActivityEntry,
  AdminChallenge,
  AuthenticatorSetup,
  CreatedApiKey,
  InvitationPreview,
  SoleOwnedWorkspace,
  WorkspaceApiKey,
  OnboardingStatus,
  SessionResponse,
  WorkspaceInvitation,
  WorkspaceMember,
  WorkspaceMembership,
  WorkspaceRole,
} from "@/types/api";
import { apiBlobRequest, apiRequest } from "./client";

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
    return apiRequest<SessionResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  },
  verifyEmail(email: string, code: string) {
    return apiRequest<SessionResponse>("/auth/verify-email", {
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
  /** Ends this browser's session and clears the refresh cookie. */
  logout() {
    return apiRequest<{ status: string; message: string }>("/auth/logout", { method: "POST" });
  },
  logoutEverywhere() {
    return apiRequest<{ status: string; message: string }>("/auth/logout-all", { method: "POST" });
  },
  me() {
    return apiRequest<Account>("/me");
  },
  /** Sets the name colleagues see on your work; an empty name shows your email instead. */
  renameAccount(displayName: string) {
    return apiRequest<Account>("/me/display-name", { method: "PUT", body: JSON.stringify({ displayName }) });
  },
  /** Your recent sign-ins and security changes. */
  accountActivity() {
    return apiRequest<ActivityEntry[]>("/me/activity");
  },
  /** Deletes the signed-in account. `password`, or the confirmation phrase for Google-only accounts. */
  deleteAccount(password: string, confirmation: string, closeOwnedWorkspaces: boolean) {
    return apiRequest<void>("/me", {
      method: "DELETE",
      body: JSON.stringify({ password, confirmation, closeOwnedWorkspaces }),
    });
  },
  /** Workspaces that would close with the account: those where you are the only Owner. */
  deletionImpact() {
    return apiRequest<SoleOwnedWorkspace[]>("/me/deletion-impact");
  },
  adminLogin(email: string, password: string) {
    return apiRequest<AdminChallenge>("/auth/admin/login", { method: "POST", body: JSON.stringify({ email, password }) });
  },
  adminAuthenticatorSetup(challenge: string) {
    return apiRequest<AuthenticatorSetup>("/auth/admin/authenticator", { method: "POST", body: JSON.stringify({ challenge }) });
  },
  adminVerify(challenge: string, code: string) {
    return apiRequest<SessionResponse>("/auth/admin/verify", { method: "POST", body: JSON.stringify({ challenge, code }) });
  },
  rememberWorkspace(workspaceId: string) {
    return apiRequest<void>("/me/last-workspace", { method: "PUT", body: JSON.stringify({ workspaceId }) });
  },
};

export function getOnboardingStatus() {
  return apiRequest<OnboardingStatus>("/tenant/onboarding-status");
}

/** Workspaces, members and invitations. Paths are account-level, so they always name the workspace. */
export const workspacesApi = {
  list() {
    return apiRequest<WorkspaceMembership[]>("/workspaces");
  },
  create(businessName: string, countryCode: string) {
    return apiRequest<WorkspaceMembership>("/workspaces", {
      method: "POST",
      body: JSON.stringify({ businessName, countryCode }),
    });
  },
  members(workspaceId: string) {
    return apiRequest<WorkspaceMember[]>(`/workspaces/${workspaceId}/members`);
  },
  changeRole(workspaceId: string, userId: string, role: WorkspaceRole) {
    return apiRequest<WorkspaceMember>(`/workspaces/${workspaceId}/members/${userId}`, {
      method: "PATCH",
      body: JSON.stringify({ role }),
    });
  },
  removeMember(workspaceId: string, userId: string) {
    return apiRequest<void>(`/workspaces/${workspaceId}/members/${userId}`, { method: "DELETE" });
  },
  transferOwnership(workspaceId: string, userId: string) {
    return apiRequest<void>(`/workspaces/${workspaceId}/transfer-ownership`, {
      method: "POST",
      body: JSON.stringify({ userId }),
    });
  },
  invitations(workspaceId: string) {
    return apiRequest<WorkspaceInvitation[]>(`/workspaces/${workspaceId}/invitations`);
  },
  invite(workspaceId: string, email: string, role: WorkspaceRole) {
    return apiRequest<WorkspaceInvitation>(`/workspaces/${workspaceId}/invitations`, {
      method: "POST",
      body: JSON.stringify({ email, role }),
    });
  },
  resendInvitation(workspaceId: string, invitationId: string) {
    return apiRequest<WorkspaceInvitation>(`/workspaces/${workspaceId}/invitations/${invitationId}/resend`, { method: "POST" });
  },
  revokeInvitation(workspaceId: string, invitationId: string) {
    return apiRequest<void>(`/workspaces/${workspaceId}/invitations/${invitationId}`, { method: "DELETE" });
  },
  previewInvitation(token: string) {
    return apiRequest<InvitationPreview>("/invitations/preview", { headers: { "X-Sauti-Invitation": token } });
  },
  acceptInvitation(token: string) {
    return apiRequest<WorkspaceMembership>("/invitations/accept", { method: "POST", body: JSON.stringify({ token }) });
  },
  acceptInvitationAsNewAccount(token: string, password: string) {
    return apiRequest<SessionResponse>("/invitations/accept-new", { method: "POST", body: JSON.stringify({ token, password }) });
  },
};

/** API keys, the activity log and data export for the active workspace. */
export const workspaceAdminApi = {
  apiKeys(workspaceId: string) {
    return apiRequest<WorkspaceApiKey[]>(`/workspaces/${workspaceId}/api-keys`);
  },
  createApiKey(workspaceId: string, name: string, access: "read_only" | "full") {
    return apiRequest<CreatedApiKey>(`/workspaces/${workspaceId}/api-keys`, {
      method: "POST",
      body: JSON.stringify({ name, access }),
    });
  },
  revokeApiKey(workspaceId: string, keyId: string) {
    return apiRequest<void>(`/workspaces/${workspaceId}/api-keys/${keyId}`, { method: "DELETE" });
  },
  /** Newest first, 50 at a time; pass the oldest `at` already shown to get more. */
  activity(before?: string) {
    return apiRequest<ActivityEntry[]>(before ? `/activity?before=${encodeURIComponent(before)}` : "/activity");
  },
  exportWorkspace(workspaceId: string) {
    return apiBlobRequest(`/workspaces/${workspaceId}/export`);
  },
};
