import type { BusinessProfile, BusinessProfileRequest } from "@/types/api";

/** Form rows keep what the owner typed as text, so a half-typed price is not lost. */
export type ServiceRow = { name: string; price: string; currency: string; duration: string; description: string };
export type FaqRow = { question: string; answer: string };

export type BusinessProfileForm = {
  displayName: string;
  description: string;
  address: string;
  phone: string;
  email: string;
  website: string;
  cancellationPolicy: string;
  services: ServiceRow[];
  faqs: FaqRow[];
};

export const EMPTY_SERVICE: ServiceRow = { name: "", price: "", currency: "", duration: "", description: "" };
export const EMPTY_FAQ: FaqRow = { question: "", answer: "" };

export function formFromProfile(profile: BusinessProfile): BusinessProfileForm {
  return {
    displayName: profile.displayName ?? "",
    description: profile.description ?? "",
    address: profile.address ?? "",
    phone: profile.phone ?? "",
    email: profile.email ?? "",
    website: profile.website ?? "",
    cancellationPolicy: profile.cancellationPolicy ?? "",
    services: profile.services.map((service) => ({
      name: service.name,
      price: service.priceAmount === null ? "" : String(service.priceAmount),
      currency: service.currency ?? "",
      duration: service.durationMinutes === null ? "" : String(service.durationMinutes),
      description: service.description ?? "",
    })),
    faqs: profile.faqs.map((faq) => ({ ...faq })),
  };
}

export type FormResult = { request: BusinessProfileRequest } | { error: string };

/**
 * Turns the form into an API request. Blank rows are dropped; anything that can't be read
 * returns a message naming the row, so the owner knows what to fix before anything is sent.
 */
export function requestFromForm(form: BusinessProfileForm, defaultCurrency = ""): FormResult {
  const services: BusinessProfileRequest["services"] = [];
  for (const row of form.services) {
    const blank = !row.name.trim() && !row.price.trim() && !row.duration.trim() && !row.description.trim();
    if (blank) continue;
    const name = row.name.trim();
    if (!name) return { error: "Every service needs a name." };
    const price = parseAmount(row.price);
    if (price === undefined) return { error: `Enter the price for ${name} as a number, such as 3500.` };
    const duration = row.duration.trim() ? Number(row.duration.trim()) : null;
    if (duration !== null && (!Number.isInteger(duration) || duration < 5 || duration > 480)) {
      return { error: `The duration for ${name} must be between 5 and 480 minutes.` };
    }
    const currency = (row.currency.trim() || defaultCurrency).toUpperCase();
    if (price !== null && !/^[A-Z]{3}$/.test(currency)) {
      return { error: `Choose a 3-letter currency, such as KES or EUR, for ${name}.` };
    }
    services.push({
      name,
      priceAmount: price,
      currency: price === null ? null : currency,
      durationMinutes: duration,
      description: row.description.trim() || null,
    });
  }
  const faqs: BusinessProfileRequest["faqs"] = [];
  for (const row of form.faqs) {
    const question = row.question.trim();
    const answer = row.answer.trim();
    if (!question && !answer) continue;
    if (!question || !answer) return { error: "Each FAQ needs both a question and an answer." };
    faqs.push({ question, answer });
  }
  const text = (value: string) => value.trim() || null;
  return {
    request: {
      displayName: text(form.displayName),
      description: text(form.description),
      address: text(form.address),
      phone: text(form.phone),
      email: text(form.email),
      website: text(form.website),
      cancellationPolicy: text(form.cancellationPolicy),
      services,
      faqs,
    },
  };
}

/** "3,500" and "3500.50" are read as numbers; empty means no price; anything else is undefined. */
export function parseAmount(value: string): number | null | undefined {
  const cleaned = value.trim().replace(/[\s,]/g, "");
  if (!cleaned) return null;
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return undefined;
  return Number(cleaned);
}
