/**
 * Dev-only console preview: lets you browse the console with sample data and
 * no login. Requires NEXT_PUBLIC_SAUTI_PREVIEW=true AND a non-production build,
 * so it can never be switched on in production.
 */
export function isPreviewMode() {
  return process.env.NODE_ENV !== "production" && process.env.NEXT_PUBLIC_SAUTI_PREVIEW === "true";
}
