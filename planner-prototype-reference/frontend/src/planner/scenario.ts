import type { Household, PlanRequest } from "./api";
import { atoms } from "./execution.ts";
import { fmt } from "./format.ts";
export const fraction = (value: string) => {
  const m = /^(-?)(\d+)(?:\.(\d*))?$/.exec(value);
  if (!m) return value;
  const digits = m[2].padStart(3, "0"),
    last = digits.slice(-2) + (m[3] ?? "");
  return `${m[1]}${digits.slice(0, -2)}.${last}`;
};
export const percent = (value: string | number | undefined) => {
  if (value == null) return "0";
  const match = /^(-?)(\d+)(?:\.(\d*))?$/.exec(String(value));
  if (!match) return String(value);
  const fraction = (match[3] ?? "").padEnd(2, "0");
  const whole = (match[2] + fraction.slice(0, 2)).replace(/^0+(?=\d)/, "");
  const rest = fraction.slice(2).replace(/0+$/, "");
  return `${match[1]}${whole}${rest ? "." + rest : ""}`;
};
export function requestErrors(
  request: PlanRequest,
  h: Household,
): Record<string, string> {
  const s = request.scenario ?? {},
    errors: Record<string, string> = {};
  for (const [key, lo, hi, title] of [
    ["annual_return", -0.9, 0.5, "年化收益假设"],
    ["annual_inflation", 0, 0.3, "年通胀假设"],
    ["target_reduction_percent", 0, 100, "目标下调"],
  ] as const) {
    const value = String(s[key] ?? "0");
    if (
      !/^-?\d+(\.\d+)?$/.test(value) ||
      !Number.isFinite(Number(value)) ||
      Number(value) < lo ||
      Number(value) > hi
    )
      errors[key] =
        `${title}需要在 ${key === "target_reduction_percent" ? lo : lo * 100}% 到 ${key === "target_reduction_percent" ? hi : hi * 100}% 之间`;
  }
  for (const key of ["extra_income", "extra_expenses"] as const)
    if (atoms(String(s[key] ?? "0")) == null)
      errors[key] = "请填写不小于零的金额，最多 8 位小数";
  if (
    !Number.isInteger(s.extension_months ?? 0) ||
    (s.extension_months ?? 0) < 0 ||
    (s.extension_months ?? 0) > 120
  )
    errors.extension_months = "延期月数需要为 0–120 的整数";
  for (const g of h.goals) {
    const value = String(request.monthly?.[g.id] ?? g.monthly),
      amount = atoms(value);
    const floor = h.commitments
      .filter((c) => c.goal_id === g.id)
      .reduce((n, c) => n + (atoms(c.monthly) ?? 0n), 0n);
    if (amount == null) errors[g.id] = "请填写不小于零的金额";
    else if (amount < floor)
      errors[g.id] =
        `含已有定投 ${fmt(Number(floor) / 1e8, h.base_currency)}/月（下限）。如要试算暂停定投，请先到建档调整承诺。`;
  }
  return errors;
}
