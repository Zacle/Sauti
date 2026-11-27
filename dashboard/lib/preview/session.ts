import type { AuthSession, Tenant } from "@/types/api";

const previewWorkspace: Tenant = {
  id: "tenant-preview",
  businessName: "Bright Smile Dental",
  email: "owner@brightsmile.example",
  countryCode: "KE",
  plan: "growth",
  status: "active",
  monthlyMinutesLimit: 750,
  minutesUsedThisCycle: 512,
};

/** Demo workspace session used only by the dev console preview. */
export const previewSession: AuthSession = {
  accessToken: "preview",
  role: "OWNER",
  tenant: previewWorkspace,
  platformAdminSession: false,
  account: {
    id: "user-preview",
    email: "owner@brightsmile.example",
    displayName: "Achieng Otieno",
    emailVerified: true,
    hasPassword: true,
    platformAdmin: false,
    lastWorkspaceId: previewWorkspace.id,
    workspaces: [
      {
        workspace: previewWorkspace,
        role: "OWNER",
        permissions: ["VIEW", "HANDLE_CONVERSATIONS", "CONFIGURE", "MANAGE_MEMBERS", "MANAGE_BILLING", "OWN_WORKSPACE"],
      },
      {
        workspace: { ...previewWorkspace, id: "tenant-preview-2", businessName: "Coast Smiles Mombasa", plan: "starter" },
        role: "ADMIN",
        permissions: ["VIEW", "HANDLE_CONVERSATIONS", "CONFIGURE", "MANAGE_MEMBERS"],
      },
    ],
  },
};
