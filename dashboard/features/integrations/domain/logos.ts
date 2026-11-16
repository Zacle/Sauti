/**
 * Provider logos shared by the Integrations page and Agent Studio.
 *
 * Brand-colour versions (in /logos/color) are used wherever they exist; the
 * rest are single-colour black marks, which the caller should invert in dark
 * mode (see `INTEGRATION_LOGO_MONO_CLASS`).
 */
const COLOR_LOGOS: Record<string, string> = {
  google_calendar: "/logos/color/google-calendar.svg",
  calendly: "/logos/color/calendly.svg",
  whatsapp: "/logos/color/whatsapp.svg",
  slack: "/logos/color/slack.svg",
  google_sheets: "/logos/color/google-sheets.svg",
  hubspot: "/logos/color/hubspot.svg",
  salesforce: "/logos/color/salesforce.svg",
  mpesa: "/logos/color/mpesa.svg",
};

const MONO_LOGOS: Record<string, string> = {
  telnyx_sms: "/logos/telnyx.svg",
  custom_webhook: "/logos/webhook.svg",
  email: "/logos/email.svg",
};

export function integrationLogo(provider: string): { src: string; mono: boolean } | null {
  if (COLOR_LOGOS[provider]) return { src: COLOR_LOGOS[provider], mono: false };
  if (MONO_LOGOS[provider]) return { src: MONO_LOGOS[provider], mono: true };
  return null;
}

/** Makes a black single-colour logo readable on the dark console theme. */
export const INTEGRATION_LOGO_MONO_CLASS = "[.console-dark_&]:invert";
