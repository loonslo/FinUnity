/* Why the look-through could not open a position, in words.

   The backend's reason is every source's own refusal, joined by " · ":
   "Nothing can decompose 'EUNL.DE': Issuer: IssuerError: iShares' product
   screener did not answer (ConnectError) · justETF: ConnectionError:
   HTTPSConnectionPool(...) ...". It was a tooltip on the position's name;
   shown as it was, under the name, it ran to 517 to 543 characters per fund
   offline, a phone's screen each (brief AU). The warning stays in view, in
   words: which sources were asked, which had nothing and which did not
   answer, and the remedy when the backend gives one. The sources' own text is
   one press away.

   "Nobody answered" and "there is nothing there" are different claims, so
   the words keep the two apart: a source that could not be reached is never
   said to have had nothing. And the backend's remedy, "Add the fund's ISIN",
   is not said of a coin or a commodity, which have no fund's ISIN to give and
   nothing inside for one to unlock (the one form offers them no ISIN either,
   situationForm.ts).

   Pure, so `tests/lookThroughReason.test.ts` runs it under Node. */

/** The sources as the look-through names them (composition.py), in words. */
const SOURCES: Record<string, string> = {
  Issuer: "the issuer's own file",
  justETF: "justETF",
  "justETF catalogue": "justETF",
  Trackinsight: "Trackinsight",
  Yahoo: "Yahoo",
};

/** How a refusal reads when nothing answered: the issuer adapters' own words
    (issuers.IssuerUnreachable) and the network errors' names. */
const SILENT =
  /did not answer|moved its product screener|ConnectError|ConnectionError|ConnectTimeout|ReadTimeout|TimeoutException|Timeout|DNSError|NameResolution|Max retries|RemoteDisconnected|SSLError|ProxyError/;

export type Reason = {
  /** What is shown under the name. */
  words: string;
  /** The backend's own text, behind a press; null when the words are all of it. */
  detail: string | null;
};

function list(names: string[]): string {
  if (names.length === 1) return names[0];
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

/** The classes told nothing about an ISIN. */
const NO_ISIN_TO_GIVE = ["crypto", "commodity"];

/** The words for a reason the look-through returned for a position of class
    `assetClass`. */
export function readReason(raw: string, assetClass: string | null = null): Reason {
  const text = raw.trim();
  if (text === "no ticker or ISIN") return { words: "No ticker and no ISIN to look it up by.", detail: null };
  const all = /^Nothing can decompose '[^']*': ([\s\S]*)$/.exec(text);
  if (!all) return { words: "Nothing could look inside it.", detail: text || null };
  const silent: string[] = [];
  const answered: string[] = [];
  const remedies: string[] = [];
  for (const piece of all[1].split(" · ")) {
    const part = piece.trim();
    if (part.startsWith("No ISIN found for")) {
      if (!NO_ISIN_TO_GIVE.includes(assetClass ?? "")) remedies.push(part);
      continue;
    }
    const cut = part.indexOf(": ");
    const name = cut > 0 ? SOURCES[part.slice(0, cut)] : undefined;
    if (!name || silent.includes(name) || answered.includes(name)) continue;
    (SILENT.test(part) ? silent : answered).push(name);
  }
  const clauses = [
    answered.length ? `${list(answered)} had nothing` : null,
    silent.length ? `${list(silent)} did not answer` : null,
  ].filter((c): c is string => c !== null);
  const words =
    (clauses.length ? `Nothing could look inside it: ${clauses.join(", and ")}.` : "Nothing could look inside it.") +
    remedies.map((r) => ` ${r}`).join("");
  return { words, detail: text };
}
