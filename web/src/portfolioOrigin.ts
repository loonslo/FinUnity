const KEY = "finunity-from-portfolio";

/** Where the author's portfolio lives; the build rewrites this host for the second domain. */
export const PORTFOLIO_URL = "https://halfopen.dev/";

/**
 * A link such as `/app?from=portfolio` marks a visitor who came from the
 * portfolio site. The mark only decides whether to show a "back to portfolio"
 * strip: it lives in sessionStorage for this tab, is never sent anywhere and
 * carries no personal information. Search visitors never see the strip.
 */
export function cameFromPortfolio(): boolean {
  try {
    if (new URLSearchParams(window.location.search).get("from") === "portfolio") {
      window.sessionStorage.setItem(KEY, "1");
    }
    return window.sessionStorage.getItem(KEY) === "1";
  } catch {
    return false; // storage disabled: simply no strip
  }
}
