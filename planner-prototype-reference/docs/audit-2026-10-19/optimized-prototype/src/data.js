/* ============================================================
   示例数据：与 8011 上虚构家庭一致（金额单位：分）
   目标的数组顺序即优先级（第 1 位最高）——对应 I10 的排序式优先级。
   ============================================================ */
function baseHousehold() {
  return {
    name: '我的家庭财务', as_of: '2026-10-17', base_currency: 'CNY',
    income: 1600000, expenses: 700000, essential: 500000, reserve_months: 6,
    risk: '谨慎', source: '虚构示例 / 手动录入',
    assets: [
      { id: 'cash', name: '日常与储蓄账户', kind: 'cash', liq: 'available', cur: 'CNY', amount: 10000000, observed_on: '2026-10-17', quality: 'verified', source: '虚构示例' },
      { id: 'investment', name: '目标投资账户', kind: 'investment', liq: 'available', cur: 'CNY', amount: 2000000, observed_on: '2026-10-17', quality: 'estimated', source: '虚构示例 / 人工估值' },
      { id: 'home', name: '自住房', kind: 'property', liq: 'illiquid', cur: 'CNY', amount: 120000000, observed_on: '2026-10-17', quality: 'estimated', source: '虚构示例 / 人工估值' },
    ],
    debts: [{ id: 'mortgage', name: '住房贷款', balance: 50000000, minimum: 300000, in_expenses: false }],
    goals: [
      { id: 'study', name: '学习进修', target: 5000000, due: '2027-10-31', monthly: 200000 },
      { id: 'family', name: '家庭长期储蓄', target: 20000000, due: '2031-10-31', monthly: 300000 },
    ],
    allocations: [
      { asset_id: 'cash', goal_id: 'study', amount: 2000000 },
      { asset_id: 'investment', goal_id: 'family', amount: 2000000 },
    ],
    commitments: [{ id: 'pac', name: '长期储蓄定投', monthly: 300000, goal_id: 'family' }],
  };
}

function emptyHousehold(today) {
  return {
    name: '我的家庭财务', as_of: shiftDay(today, -1),
    base_currency: 'CNY', income: null, expenses: null, essential: null, reserve_months: 6,
    risk: '尚未明确', source: '手动录入',
    assets: [], debts: [], goals: [], allocations: [], commitments: [],
  };
}

const EV_STUDY = {
  id: 'e1', kind: 'transfer', mode: 'new_funding', state: 'confirmed', quality: 'verified',
  date: '2026-10-19', asset_id: 'cash', target_asset_id: 'investment', amount: 200000, target_amount: null,
  goal_id: 'study', source: '手动录入', note: '', voided: false,
};
const EV_FAMILY = {
  id: 'e2', kind: 'transfer', mode: 'new_funding', state: 'confirmed', quality: 'verified',
  date: '2026-10-28', asset_id: 'cash', target_asset_id: 'investment', amount: 300000, target_amount: null,
  goal_id: 'family', source: '手动录入', note: '', voided: false,
};

/* 方案 v1：确认时冻结的每月计划（与后端 confirm 生成的 action 同口径） */
function planV1() {
  return {
    id: 'v1', version: 1, status: 'active', rev: 1, confirmed_at: '2026-10-19 18:51', as_of: '2026-10-17',
    summary: '学习 ¥2,000 / 家庭 ¥3,000 · 缺口 ¥6,000（已确认知晓）',
    actions: [
      { id: 'act-study', goal_id: 'study', month: '2026-10', amount: 200000 },
      { id: 'act-family', goal_id: 'family', month: '2026-10', amount: 300000 },
    ],
  };
}

/* 评审视图（演示状态）：每个状态只改动变量，页面逻辑一致 */
const DEMOS = {
  oct19: { label: '10 月 19 日 · 方案使用中，投入进行中', today: '2026-10-19' },
  empty: { label: '新用户 · 还没有建档（空状态）', today: '2026-10-19' },
  stale: { label: '方案草案已过期（草案后又记了一笔账）', today: '2026-10-19' },
  nov02: { label: '11 月 2 日 · 跨月（方案仍使用中，本月无行动）', today: '2026-11-02' },
  oct31: { label: '10 月 31 日 · 月末（可以生成复盘）', today: '2026-10-31' },
};

function loadDemo(name) {
  const d = DEMOS[name];
  const today = d.today;
  const S = {
    demo: name, today, rev: 2, household: baseHousehold(), ledger: [{ ...EV_STUDY }],
    plans: [planV1()], pending: null, reviewedAt: '2026-10-19',
    reviews: [],
  };
  if (name === 'empty') {
    S.rev = 0; S.household = emptyHousehold(today); S.ledger = []; S.plans = []; S.reviewedAt = null;
  }
  if (name === 'stale') {
    S.pending = { id: 'draft2', rev: 1, scen: { monthly: { study: 233334 } }, created_at: '2026-10-19 19:05' };
  }
  if (name === 'nov02') {
    S.rev = 2; S.reviewedAt = '2026-10-19';
  }
  if (name === 'oct31') {
    S.ledger.push({ ...EV_FAMILY });
    S.rev = 3; S.reviewedAt = null;
  }
  return S;
}

if (typeof module !== 'undefined') module.exports = { baseHousehold, loadDemo, DEMOS, planV1 };
