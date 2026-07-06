import type { VoiceCatalog } from "@/types/api";
import { apiRequest } from "./client";

export function listVoices() {
  return apiRequest<VoiceCatalog>("/voices");
}
