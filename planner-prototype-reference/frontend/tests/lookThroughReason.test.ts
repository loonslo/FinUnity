/* The look-through's reason for a position it could not open, in words.

   Shown as the backend writes it, each reason ran to 517 to 543 characters
   per fund offline (brief AU). The first reason below is one of those,
   measured on a scratch copy with every outside host refused; the others are
   built from the phrasings composition.py and issuers.py use. Every expected
   value is written here. */

import assert from "node:assert/strict";
import { test } from "node:test";
import { readReason } from "../src/components/lookThroughReason.ts";

const OFFLINE =
  "Nothing can decompose 'EUNL.DE': Issuer: IssuerError: iShares' product screener did not answer (ConnectError) · " +
  "justETF: ConnectionError: HTTPSConnectionPool(host='www.justetf.com', port=443): Max retries exceeded with url: " +
  "/en/etf-profile.html?isin=IE00B4L5Y983 (Caused by NameResolutionError(\"HTTPSConnection(host='www.justetf.com', " +
  "port=443): Failed to resolve 'www.justetf.com' ([Errno 11001] refused by au_serve)\")) · " +
  "Trackinsight: ConnectError: [Errno 11001] refused by au_serve · Yahoo: DNSError: refused by au_serve";

test("nothing answered: every source named, none said to have had nothing", () => {
  assert.deepEqual(readReason(OFFLINE), {
    words: "Nothing could look inside it: the issuer's own file, justETF, Trackinsight and Yahoo did not answer.",
    detail: OFFLINE,
  });
});

test("every source answered and had nothing, and the backend's remedy is kept", () => {
  const raw =
    "Nothing can decompose 'XYZ.MI': No ISIN found for 'XYZ'. Add the fund's ISIN to this holding to unlock the country breakdown. · " +
    "Trackinsight: CompositionError: Trackinsight has no composition for 'XYZ' · " +
    "Yahoo: CompositionError: Yahoo has no fund data for 'XYZ.MI'";
  assert.deepEqual(readReason(raw), {
    words:
      "Nothing could look inside it: Trackinsight and Yahoo had nothing. No ISIN found for 'XYZ'. Add the fund's ISIN to this holding to unlock the country breakdown.",
    detail: raw,
  });
});

test("some had nothing and one did not answer: both said, apart", () => {
  const raw =
    "Nothing can decompose 'VUSA.MI': Issuer: IssuerError: No issuer adapter covers IE00B3XXRP09 · " +
    "justETF: ConnectionError: Max retries exceeded · " +
    "Trackinsight: CompositionError: Trackinsight has no composition for 'VUSA' · " +
    "Yahoo: CompositionError: Yahoo has no fund data for 'VUSA.MI'";
  assert.equal(
    readReason(raw).words,
    "Nothing could look inside it: the issuer's own file, Trackinsight and Yahoo had nothing, and justETF did not answer.",
  );
});

test("justETF's catalogue and its profile page are one source to the reader", () => {
  const raw =
    "Nothing can decompose 'ABC.DE': justETF catalogue: ConnectionError: refused · justETF: ConnectionError: refused";
  assert.equal(readReason(raw).words, "Nothing could look inside it: justETF did not answer.");
});

test("a coin or a commodity is not told to add a fund's ISIN; anything else is", () => {
  const raw =
    "Nothing can decompose 'BTC-EUR': No ISIN found for 'BTC-EUR'. Add the fund's ISIN to this holding to unlock the country breakdown. · " +
    "Trackinsight: CompositionError: Trackinsight has no composition for 'BTC-EUR' · " +
    "Yahoo: CompositionError: Yahoo has no fund data for 'BTC-EUR'";
  assert.equal(readReason(raw, "crypto").words, "Nothing could look inside it: Trackinsight and Yahoo had nothing.");
  assert.equal(readReason(raw, "commodity").words, "Nothing could look inside it: Trackinsight and Yahoo had nothing.");
  assert.equal(
    readReason(raw, "equity").words,
    "Nothing could look inside it: Trackinsight and Yahoo had nothing. No ISIN found for 'BTC-EUR'. Add the fund's ISIN to this holding to unlock the country breakdown.",
  );
});

test("no ticker and no ISIN is said plainly, with nothing behind it", () => {
  assert.deepEqual(readReason("no ticker or ISIN"), {
    words: "No ticker and no ISIN to look it up by.",
    detail: null,
  });
});

test("a reason in no shape it knows keeps its text one press away", () => {
  assert.deepEqual(readReason("ValueError: boom"), {
    words: "Nothing could look inside it.",
    detail: "ValueError: boom",
  });
  assert.deepEqual(readReason("Nothing can decompose 'X': no reason was reported"), {
    words: "Nothing could look inside it.",
    detail: "Nothing can decompose 'X': no reason was reported",
  });
});
