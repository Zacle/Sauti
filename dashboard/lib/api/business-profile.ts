import type { BusinessProfile, BusinessProfileRequest } from "@/types/api";
import { apiRequest } from "./client";

export function getBusinessProfile() {
  return apiRequest<BusinessProfile>("/business-profile");
}

export function saveBusinessProfile(request: BusinessProfileRequest) {
  return apiRequest<BusinessProfile>("/business-profile", {
    method: "PUT",
    body: JSON.stringify(request),
  });
}
