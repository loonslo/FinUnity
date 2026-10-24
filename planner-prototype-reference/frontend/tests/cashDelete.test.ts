/* A page's Delete of a transfer or a balance says what the deletion moves
   (brief AP).

   The words come from the backend, from the same function the chat's card
   says them with; here, the question is built from them, and a deletion whose
   effect could not be had still asks, saying why. Every expected value is
   written here; the accounts and figures are invented. */

import assert from "node:assert/strict";
import { test } from "node:test";
import { askBeforeDeleting } from "../src/components/cashDelete.ts";

const MOVES =
  "It goes, and nothing brings it back. Example Bank's cash changes from 2026-09-11 on: today from 1800.00 EUR to 2800.00 EUR.";

test("the question carries what the backend says the deletion moves", async () => {
  const asked = await askBeforeDeleting(
    "Delete this transfer?",
    async () => MOVES,
    () => "unused",
  );
  assert.equal(
    asked,
    "Delete this transfer? It goes, and nothing brings it back. Example Bank's cash changes from 2026-09-11 on: today from 1800.00 EUR to 2800.00 EUR.",
  );
});

test("a deletion whose effect could not be had still asks, and says why", async () => {
  const asked = await askBeforeDeleting(
    "Delete the cash anchor of 2026-09-14?",
    async () => {
      throw new Error("offline");
    },
    () => "The backend did not answer. Is it running on :8000?",
  );
  assert.equal(
    asked,
    "Delete the cash anchor of 2026-09-14? What deleting it moves could not be worked out: The backend did not answer. Is it running on :8000?",
  );
});

test("the failure is read by the function handed in, with the error itself", async () => {
  const seen: unknown[] = [];
  const failure = new Error("Request failed with status code 404");
  await askBeforeDeleting(
    "Delete this transfer?",
    async () => {
      throw failure;
    },
    (err) => {
      seen.push(err);
      return "Not Found";
    },
  );
  assert.deepEqual(seen, [failure]);
});
