export function browserLanguageHint(
  supportedLanguages: string[],
  defaultLanguage: string,
  browserLanguages?: readonly string[],
) {
  const supported = supportedLanguages
    .map(normalizeLanguage)
    .filter(Boolean);
  const fallback = supported.includes(normalizeLanguage(defaultLanguage))
    ? normalizeLanguage(defaultLanguage)
    : supported[0] ?? normalizeLanguage(defaultLanguage) ?? "en";
  const preferences = browserLanguages
    ?? (typeof navigator === "undefined" ? [] : navigator.languages);

  for (const preference of preferences) {
    const normalized = normalizeLanguage(preference);
    const exact = supported.find((language) => language === normalized);
    if (exact) return exact;
    const primary = normalized.split("-")[0];
    const primaryMatch = supported.find((language) => language.split("-")[0] === primary);
    if (primaryMatch) return primaryMatch;
  }
  return fallback;
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
