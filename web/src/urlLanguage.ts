import type { Locale } from "./types";

/**
 * A link such as `/app?lang=en` chooses the interface language for the visit, so a visitor who clicks
 * through from an English page lands on an English page. Anything else (no parameter, an unknown
 * value) leaves the stored or default language alone.
 */
export function languageFromSearch(search: string): Locale | null {
  const value = new URLSearchParams(search).get("lang");
  if (value === "en") return "en-US";
  if (value === "zh") return "zh-CN";
  return null;
}
