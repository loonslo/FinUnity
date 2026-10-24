import type { Action, Household, Occurrence } from "./api";
import { day } from "./sample.ts";

export const kinds = {
  income: "收入 ＋",
  expense: "支出 −",
  transfer: "转账 ⇄",
  debt_payment: "还款 −",
  adjustment: "对账差额调整",
};

export function emptyOccurrence(h: Household): Occurrence {
  const accounts = h.assets.filter(
    (a) => a.kind === "cash" && a.liquidity === "available",
  );
  return {
    client_key: crypto.randomUUID(),
    kind: "income",
    adjustment_direction: "increase",
    state: "confirmed",
    quality: "verified",
    date: day(),
    asset_id: accounts.length === 1 ? accounts[0].id : "",
    amount: null,
    target_asset_id: null,
    target_amount: null,
    goal_id: null,
    debt_id: null,
    principal: null,
    allocation_mode: "new_funding",
    action_id: null,
    settles_id: null,
    source: "手动录入",
    note: "",
  };
}

export function isEmptyDraft(h: Household, entry: Occurrence): boolean {
  const defaults = emptyOccurrence(h);
  return (Object.keys(defaults) as (keyof Occurrence)[]).every(
    (key) => key === "client_key" || entry[key] === defaults[key],
  );
}

export function forAction(h: Household, action: Action): Occurrence {
  const entry = emptyOccurrence(h);
  const candidates = h.assets.filter(
    (a) =>
      a.id !== entry.asset_id &&
      ["cash", "investment"].includes(a.kind) &&
      a.liquidity === "available",
  );
  const assigned = candidates.filter((a) =>
    h.allocations.some(
      (p) => p.goal_id === action.goal_id && p.asset_id === a.id,
    ),
  );
  const investments = candidates.filter((a) => a.kind === "investment");
  const target =
    assigned.length === 1
      ? assigned[0]
      : investments.length === 1
        ? investments[0]
        : candidates.length === 1
          ? candidates[0]
          : null;
  return {
    ...entry,
    kind: "transfer",
    goal_id: action.goal_id,
    action_id: action.id,
    amount:
      action.remaining_amount && atoms(action.remaining_amount) !== 0n
        ? action.remaining_amount
        : null,
    target_asset_id: target?.id ?? null,
  };
}

export function changeKind(
  event: Occurrence,
  kind: Occurrence["kind"],
): Occurrence {
  return {
    ...event,
    kind,
    target_asset_id: null,
    target_amount: null,
    goal_id: null,
    debt_id: null,
    principal: null,
    allocation_mode: "new_funding",
    action_id: null,
    settles_id: null,
  };
}

// Input validation uses fixed-scale integers. Number is only used for display.
export function atoms(value: string | null | undefined): bigint | null {
  if (!value || !/^\d+(\.\d{1,8})?$/.test(value)) return null;
  const [whole, part = ""] = value.split(".");
  return BigInt(whole) * 100000000n + BigInt(part.padEnd(8, "0"));
}

export function draftErrors(
  h: Household,
  event: Occurrence,
  actions: Action[],
): string[] {
  const errors: string[] = [];
  const actual = event.state === "confirmed";
  const amount = atoms(event.amount);
  const source = h.assets.find((a) => a.id === event.asset_id);
  const target = h.assets.find((a) => a.id === event.target_asset_id);
  if (!source || source.kind !== "cash")
    errors.push("请选择本条记录的现金账户");
  if (actual && !event.amount) errors.push("请填写金额；已发生的记录不能留空");
  else if (event.amount && amount === null)
    errors.push("金额只填写正数，增加或减少由记录类型决定，最多 8 位小数");
  else if (actual && amount === 0n) errors.push("已发生的记录金额必须大于零");
  if (!event.date || !/^\d{4}-\d{2}-\d{2}$/.test(event.date))
    errors.push("请选择日期");
  if (actual && event.date > day())
    errors.push("未来日期请使用预测，不能记为已发生");
  if (actual && source && event.date <= source.observed_on)
    errors.push(
      `日期必须晚于「${source.name}」的日终余额日期 ${source.observed_on}`,
    );
  if (actual && event.quality === "unknown")
    errors.push("已发生的记录需要选择已核实或估算的金额");
  if (!event.source.trim()) errors.push("请填写记录来源");
  if (event.kind === "transfer") {
    if (
      !target ||
      !["cash", "investment"].includes(target.kind) ||
      target.id === source?.id
    )
      errors.push("请选择不同的转入账户");
    if (target && actual && event.date <= target.observed_on)
      errors.push(
        `日期必须晚于「${target.name}」的日终余额日期 ${target.observed_on}`,
      );
    const arrival = atoms(event.target_amount);
    if (
      target &&
      source &&
      target.currency !== source.currency &&
      arrival === null
    )
      errors.push("跨币种转账需要填写转入账户实际收到的金额");
    if (event.target_amount && arrival === null)
      errors.push("转入金额格式无效，请填写正数");
    if (actual && arrival === 0n) errors.push("转入金额必须大于零");
    if (
      target &&
      source &&
      target.currency === source.currency &&
      arrival !== null &&
      amount !== arrival
    )
      errors.push("同币种转账的两端金额应相同，手续费请另加一条支出");
    if (event.allocation_mode === "move_existing" && !event.goal_id)
      errors.push("移动已分配资金时需要选择目标");
  }
  if (event.kind === "debt_payment") {
    const principal = atoms(event.principal);
    if (!event.debt_id) errors.push("请选择本次偿还的负债");
    if (principal === null) errors.push("请填写其中偿还的本金，纯利息可填 0");
    else if (amount !== null && principal > amount)
      errors.push("偿还本金不能大于本次还款总额");
    if (actual && event.date <= h.as_of)
      errors.push(`还款日期必须晚于负债建档日期 ${h.as_of}`);
  }
  if (event.action_id) {
    const action = actions.find(
      (a) => a.id === event.action_id && a.can_record,
    );
    if (!action) errors.push("投入计划已变化，请刷新后重新选择");
    else if (
      event.kind !== "transfer" ||
      event.goal_id !== action.goal_id ||
      event.allocation_mode !== "new_funding" ||
      !event.date.startsWith(action.month)
    )
      errors.push("关联投入计划需要相同的目标、月份，并使用新增目标资金");
  }
  return errors;
}

export function shownAmount(
  amount: string | null | undefined,
  currency: string,
): string {
  return amount == null || amount === ""
    ? "待填写金额"
    : new Intl.NumberFormat("zh-CN", {
        style: "currency",
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
        .format(Number(amount))
        .replaceAll("-", "−");
}

export function effects(h: Household, event: Occurrence): string[] {
  const source = h.assets.find((a) => a.id === event.asset_id);
  const target = h.assets.find((a) => a.id === event.target_asset_id);
  const amount = shownAmount(event.amount, source?.currency ?? h.base_currency);
  if (
    event.kind === "income" ||
    (event.kind === "adjustment" && event.adjustment_direction === "increase")
  )
    return [`${source?.name ?? "收款账户"} ＋ ${amount}`];
  const result = [`${source?.name ?? "付款账户"} − ${amount}`];
  if (event.kind === "transfer")
    result.push(
      `${target?.name ?? "转入账户"} ＋ ${shownAmount(event.target_amount ?? event.amount, target?.currency ?? h.base_currency)}`,
    );
  if (event.kind === "debt_payment")
    result.push(
      `${h.debts.find((d) => d.id === event.debt_id)?.name ?? "负债"} 本金 − ${shownAmount(event.principal, source?.currency ?? h.base_currency)}`,
    );
  return result;
}
