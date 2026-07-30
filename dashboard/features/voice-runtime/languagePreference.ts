export function configuredLanguageHint(
  supportedLanguages: string[],
  defaultLanguage: string,
) {
  const supported = supportedLanguages
    .map(normalizeLanguage)
    .filter(Boolean);
  const configured = normalizeLanguage(defaultLanguage);
  return supported.includes(configured)
    ? configured
    : supported[0] ?? configured ?? "en";
}

export function displayLanguage(code: string, locale = "en") {
  try {
    return new Intl.DisplayNames([locale], { type: "language" }).of(code) ?? code.toUpperCase();
  } catch {
    return code.toUpperCase();
  }
}

function normalizeLanguage(value: string) {
  return value.trim().toLowerCase().replaceAll("_", "-");
}
