import { decimalString } from "../decimal.ts";

/**
 * Exact decimal arithmetic for the in-browser demo.
 *
 * The real server values holdings with Python `Decimal` and rounds to minor units
 * with ROUND_HALF_UP. Floating point would disagree with it on exact half-cent
 * cases, so the demo does the same arithmetic on BigInt-scaled decimals.
 */
export type Decimal = { digits: bigint; scale: number };

export function parseDecimal(value: number | string): Decimal {
  const text = typeof value === "number" ? decimalString(value) : value.trim();
  const match = /^(-?)(\d*)(?:\.(\d*))?$/.exec(text);
  if (!match || (!match[2] && !match[3])) throw new Error(`Invalid decimal: ${text}`);
  const fraction = match[3] ?? "";
  return { digits: BigInt(`${match[1]}${match[2] || "0"}${fraction}`), scale: fraction.length };
}

function divideHalfUp(numerator: bigint, divisor: bigint): bigint {
  const negative = numerator < 0n;
  const magnitude = negative ? -numerator : numerator;
  const quotient = magnitude / divisor;
  const rounded = (magnitude % divisor) * 2n >= divisor ? quotient + 1n : quotient;
  return negative ? -rounded : rounded;
}

/** `round_half_up(a * b * 100)`, i.e. the product expressed in minor units. */
export function productToMinor(a: number | string, b: number | string): number {
  const left = parseDecimal(a);
  const right = parseDecimal(b);
  return Number(divideHalfUp(left.digits * right.digits * 100n, 10n ** BigInt(left.scale + right.scale)));
}

/** `money_minor(value)` from the server: round_half_up(value * 100). */
export const toMinor = (value: number | string): number => productToMinor(value, 1);

/** Integer target percentages, rounded to minor units exactly like Decimal ROUND_HALF_UP. */
export const percentageOfMinor = (value: number, percent: number): number =>
  Number(divideHalfUp(BigInt(value) * BigInt(percent), 100n));

/** Python's `round()` (banker's rounding), used by the server for the alignment score. */
export function roundHalfEven(value: number): number {
  const floor = Math.floor(value);
  const diff = value - floor;
  if (diff < 0.5) return floor;
  if (diff > 0.5) return floor + 1;
  return floor % 2 === 0 ? floor : floor + 1;
}
