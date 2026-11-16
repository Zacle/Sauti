import type { AuthSession } from "@/types/api";

/** Demo workspace session used only by the dev console preview. */
export const previewSession: AuthSession = {
  accessToken: "preview",
  refreshToken: "preview",
  role: "OWNER",
  tenant: {
    id: "tenant-preview",
    businessName: "Bright Smile Dental",
    email: "owner@brightsmile.example",
    countryCode: "KE",
    plan: "growth",
    status: "active",
    monthlyMinutesLimit: 750,
    minutesUsedThisCycle: 512,
  },
};
