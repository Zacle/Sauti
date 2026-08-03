import { apiRequest } from "./client";

export type DemoRequestPayload = {
  businessName: string;
  contactName: string;
  email: string;
  countryCode: string;
  phone: string;
  industry: string;
  monthlyCallVolume: string;
  channels: string[];
  primaryUseCase: string;
  notes: string;
  website: string;
};

export type DemoRequestResponse = {
  status: "received";
  message: string;
};

export function createDemoRequest(payload: DemoRequestPayload): Promise<DemoRequestResponse> {
  return apiRequest<DemoRequestResponse>("/public/demo-requests", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
