import type { Agent, CompleteOnboardingRequest } from "@/types/api";
import { apiRequest } from "./client";

export function completeOnboarding(request: CompleteOnboardingRequest) {
  return apiRequest<Agent>("/tenant/onboarding", {
    method: "POST",
    body: JSON.stringify(request),
  });
}
