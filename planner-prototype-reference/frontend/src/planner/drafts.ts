import type { Household, Occurrence } from "./api";

export const HOUSEHOLD_DRAFT_KEY = "finunity-household-draft-v1";
export const RECORD_DRAFT_KEY = "finunity-record-draft-v1";
export type HouseholdDraft = { base: Household; value: Household };
export type RecordDraft = { items: Occurrence[]; batchKey: string };
export function readLocal<T>(
  key: string,
  validate: (value: unknown) => boolean,
): T | null {
  try {
    const v: unknown = JSON.parse(localStorage.getItem(key) ?? "null");
    return validate(v) ? (v as T) : null;
  } catch {
    return null;
  }
}
export function storeLocal(key: string, value: unknown): boolean {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}
export function householdDraft(value: unknown): boolean {
  const v = value as HouseholdDraft | null;
  return (
    !!v?.base &&
    !!v?.value &&
    Array.isArray(v.base.assets) &&
    Array.isArray(v.value.assets) &&
    Array.isArray(v.value.debts) &&
    Array.isArray(v.value.commitments) &&
    Array.isArray(v.value.allocations) &&
    Array.isArray(v.value.rates) &&
    typeof v.value.name === "string" &&
    typeof v.value.source === "string" &&
    typeof v.value.base_currency === "string" &&
    Array.isArray(v.value.goals) &&
    typeof v.value.as_of === "string"
  );
}
export function recordDraft(value: unknown): boolean {
  const v = value as RecordDraft | null;
  return (
    !!v &&
    typeof v.batchKey === "string" &&
    Array.isArray(v.items) &&
    v.items.length > 0 &&
    v.items.length <= 50 &&
    v.items.every(
      (x) =>
        !!x &&
        typeof x.client_key === "string" &&
        typeof x.kind === "string" &&
        typeof x.date === "string",
    )
  );
}
export function diffCount(a: unknown, b: unknown): number {
  if (a === b) return 0;
  if (a == null || b == null || typeof a !== "object" || typeof b !== "object")
    return 1;
  const left = a as Record<string, unknown>,
    right = b as Record<string, unknown>;
  return [...new Set([...Object.keys(left), ...Object.keys(right)])].reduce(
    (n, k) => n + diffCount(left[k], right[k]),
    0,
  );
}
export function mergeHouseholdDraft(
  old: HouseholdDraft | null,
  saved: Household,
): HouseholdDraft {
  return old && diffCount(old.base, old.value) > 0
    ? old
    : { base: saved, value: saved };
}
