import type { Household } from "./api";
export function day(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
export function example(): Household {
  const current = day();
  const d = new Date(current + "T12:00:00Z");
  d.setUTCDate(d.getUTCDate() - 1);
  const anchor = d.toISOString().slice(0, 10);
  const year = Number(current.slice(0, 4));
  return {
    name: "我的家庭财务",
    as_of: anchor,
    base_currency: "CNY",
    language: "zh-CN",
    timezone: "Asia/Shanghai",
    income: "16000",
    expenses: "7000",
    essential_expenses: "5000",
    reserve_months: 6,
    income_complete: true,
    expenses_complete: true,
    debts_complete: true,
    assets_complete: true,
    risk_tolerance: "cautious",
    constraints:
      "以下均为虚构数据，可修改后保存。近期目标优先，不假设投资收益。",
    source: "虚构示例 / 手动录入",
    assets: [
      {
        id: "cash",
        name: "日常与储蓄账户",
        kind: "cash",
        liquidity: "available",
        currency: "CNY",
        amount: "100000",
        observed_on: anchor,
        quality: "verified",
        source: "虚构示例",
      },
      {
        id: "investment",
        name: "目标投资账户",
        kind: "investment",
        liquidity: "available",
        currency: "CNY",
        amount: "20000",
        observed_on: anchor,
        quality: "estimated",
        source: "虚构示例 / 人工估值",
      },
      {
        id: "home",
        name: "自住房",
        kind: "property",
        liquidity: "illiquid",
        currency: "CNY",
        amount: "1200000",
        observed_on: anchor,
        quality: "estimated",
        source: "虚构示例 / 人工估值",
      },
    ],
    debts: [
      {
        id: "mortgage",
        name: "住房贷款",
        balance: "500000",
        currency: "CNY",
        minimum_payment: "3000",
        payment_in_expenses: false,
        source: "虚构示例",
      },
    ],
    goals: [
      {
        id: "study",
        name: "学习进修",
        target: "50000",
        target_date: `${year + 1}-10-31`,
        monthly: "2000",
        priority: 1,
      },
      {
        id: "family",
        name: "家庭长期储蓄",
        target: "200000",
        target_date: `${year + 5}-10-31`,
        monthly: "3000",
        priority: 2,
      },
    ],
    allocations: [
      { asset_id: "cash", goal_id: "study", amount: "20000" },
      { asset_id: "investment", goal_id: "family", amount: "20000" },
    ],
    commitments: [
      { id: "pac", name: "长期储蓄定投", monthly: "3000", goal_id: "family" },
    ],
    rates: [],
  };
}

export function yesterday(): string {
  const date = new Date(day() + "T12:00:00Z");
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}
