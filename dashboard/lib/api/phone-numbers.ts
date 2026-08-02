import { apiRequest } from "./client";

export type CountryCallingCode = {
  region: string;
  name: string;
  dialingCode: string;
};

export function listCountryCallingCodes() {
  return apiRequest<CountryCallingCode[]>("/phone-numbers/countries");
}
