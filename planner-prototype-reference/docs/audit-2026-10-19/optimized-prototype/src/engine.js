/* ============================================================
   测算引擎（示意版）
   忠实移植 backend/app/planner/engine.py 的口径，金额一律用「分」整数；
   只有收益/通胀的复利才用浮点（原型示意，生产必须使用后端 Decimal 计算）。
   ============================================================ */

/* 把用户输入「1,234.5」解析成分；无法解析返回 null */
function parseCents(text) {
  if (text == null) return null;
  const t = String(text).replace(/[,，\s¥￥]/g, '');
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const [w, f = ''] = t.split('.');
  return Number(w) * 100 + Number((f + '00').slice(0, 2));
}

/* 分 → 展示串。dec=false 时省略小数；sign=true 时正数带 + 号 */
const nf0 = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 });
function fmtYuan(c, { dec = true, sign = false } = {}) {
  if (c == null) return '待补充';
  const neg = c < 0;
  const abs = Math.abs(c);
  const whole = nf0.format(Math.floor(abs / 100));
  const frac = String(abs % 100).padStart(2, '0');
  const body = dec ? `${whole}.${frac}` : (abs % 100 ? `${whole}.${frac}` : whole);
  return `${neg ? '−' : sign && c > 0 ? '＋' : ''}¥${body}`;
}

/* 日期平移 n 天（ISO 串，按 UTC 计算，避免本地时区干扰） */
function shiftDay(iso, n) {
  const [y, m, d] = iso.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + n));
  return t.toISOString().slice(0, 10);
}

function monthAfter(iso, count) {
  const [y, m, d] = iso.split('-').map(Number);
  const n = y * 12 + (m - 1) + count;
  const yy = Math.floor(n / 12);
  const mm = (n % 12) + 1;
  const last = new Date(Date.UTC(yy, mm, 0)).getUTCDate();
  return `${yy}-${String(mm).padStart(2, '0')}-${String(Math.min(d, last)).padStart(2, '0')}`;
}

function monthsBetween(asOf, due) {
  if (due <= asOf) return 0;
  const [ay, am] = asOf.split('-').map(Number);
  const [dy, dm] = due.split('-').map(Number);
  let months = (dy - ay) * 12 + dm - am;
  if (monthAfter(asOf, months) > due) months -= 1;
  return Math.max(months, 0);
}

/* 一个目标在给定条件下的结果（与 engine.py 的 goals[] 同口径） */
function goalResult(g, funded, affordable, wanted, scen, asOf) {
  const due = monthAfter(g.due, scen.extend || 0);
  const months = monthsBetween(asOf, due);
  const reduce = scen.reducePct || 0;                       // 百分数 0–100
  const infl = (scen.infl || 0) / 100;                      // 界面用百分数，引擎用小数
  const ret = (scen.ret || 0) / 100;
  const target = Math.round(g.target * (1 - reduce / 100) * Math.pow(1 + infl / 12, months));
  const rate = ret / 12;
  const growth = Math.pow(1 + rate, months);
  const annuity = rate === 0 ? months : (growth - 1) / rate;
  const projected = Math.round(funded * growth + affordable * annuity);
  const needed = annuity ? Math.max(Math.ceil((target - funded * growth) / annuity - 1e-9), 0) : null;   // 向上取整到分：填回去才真的补得上（N9）
  return {
    id: g.id, name: g.name, due, months, target, funded, wanted, affordable,
    needed, projected, shortfall: Math.max(target - projected, 0),
    annuity, growth,
  };
}

/* 主测算。h=建档，events=流水，scen=试算条件，today=测算日 */
function calc(h, events, scen = {}, today) {
  const S = { extraIncome: 0, extraExpenses: 0, reducePct: 0, ret: 0, infl: 0, extend: 0, monthly: {}, ...scen };
  const assets = Object.fromEntries(h.assets.map(a => [a.id, a]));
  const bal = Object.fromEntries(h.assets.map(a => [a.id, a.quality === 'unknown' ? null : a.amount]));
  const debts = Object.fromEntries(h.debts.map(d => [d.id, d.balance]));
  const allocations = h.allocations.map(a => ({ ...a }));
  const used = [];                                          // 已计入余额的流水（供「数字从哪来」使用）

  const live = events
    .filter(e => !e.voided && e.state === 'confirmed' && e.date <= today)
    .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  for (const e of live) {
    const a = assets[e.asset_id];
    if (!a) continue;
    if (e.date > a.observed_on && bal[a.id] != null) {
      bal[a.id] += e.kind === 'income' ? e.amount : -e.amount;
      used.push({ id: e.id, asset_id: a.id, delta: e.kind === 'income' ? e.amount : -e.amount, date: e.date });
    }
    if (e.kind === 'transfer') {
      const t = assets[e.target_asset_id];
      if (!t) continue;
      const arrive = e.target_amount ?? e.amount;
      if (e.date > t.observed_on && bal[t.id] != null) {
        bal[t.id] += arrive;
        used.push({ id: e.id, asset_id: t.id, delta: arrive, date: e.date });
      }
      if (e.goal_id && e.date > h.as_of && e.mode !== 'move_existing') {
        allocations.push({ asset_id: t.id, goal_id: e.goal_id, amount: arrive });
      }
    }
    if (e.kind === 'debt_payment' && e.date > h.as_of && debts[e.debt_id] != null) {
      debts[e.debt_id] -= e.principal || 0;
    }
  }

  const warnings = [];
  const missing = [];
  const incomplete = h.income == null || h.expenses == null || h.essential == null;
  if (h.income == null) missing.push('月收入未填');
  if (h.expenses == null) missing.push('月支出未填');
  if (h.essential == null) missing.push('必要支出未填，无法计算应急预留');
  let total = 0, cash = 0, liquid = 0, debtTotal = 0, known = true;
  const rows = h.assets.map(a => {
    const v = bal[a.id];
    if (v == null) { known = false; missing.push(`${a.name}的金额未知`); }
    else {
      total += v;
      if (a.liq === 'available' && (a.kind === 'cash' || a.kind === 'investment')) liquid += v;
      if (a.kind === 'cash' && a.liq === 'available') cash += v;
    }
    if (a.quality === 'estimated') warnings.push(`${a.name}使用估算金额`);
    const moves = used.filter(u => u.asset_id === a.id);
    return { ...a, balance: v, anchor: a.amount, moves, moved: moves.reduce((s, m) => s + m.delta, 0) };
  });
  let minimum = 0;
  for (const d of h.debts) {
    debtTotal += debts[d.id] || 0;
    if (!d.in_expenses) minimum += d.minimum || 0;
  }

  const reserve = h.essential == null ? null : h.essential * h.reserve_months;
  const available = reserve == null ? null : Math.max(cash - reserve, 0);
  const funded = Object.fromEntries(h.goals.map(g => [g.id, 0]));
  let cashAllocated = 0;
  for (const a of allocations) {
    const asset = assets[a.asset_id];
    if (!asset || funded[a.goal_id] == null) continue;
    funded[a.goal_id] += a.amount;
    if (asset.kind === 'cash') cashAllocated += a.amount;
  }
  const errors = [];
  if (available != null && cashAllocated > available) errors.push('目标现金分配占用了应急预留');

  const linked = {};
  let unlinked = 0;
  for (const c of h.commitments) {
    if (c.goal_id) linked[c.goal_id] = (linked[c.goal_id] || 0) + c.monthly;
    else unlinked += c.monthly;
  }
  const surplus = incomplete ? 0 : h.income + S.extraIncome - h.expenses - S.extraExpenses - minimum;
  const pool = Math.max(surplus - unlinked, 0);
  let remaining = pool;
  const goals = [];
  h.goals.forEach((g, idx) => {                             // 顺序即优先级
    const asked = S.monthly[g.id] != null ? S.monthly[g.id] : g.monthly;
    const floor = linked[g.id] || 0;
    const wanted = Math.max(asked, floor);
    const affordable = Math.min(wanted, remaining);
    remaining -= affordable;
    const r = goalResult(g, funded[g.id], affordable, wanted, S, today);
    goals.push({ ...r, priority: idx + 1, asked, floor, floorBinds: floor > asked });
  });
  const committed = goals.reduce((s, g) => s + g.wanted, 0) + unlinked;
  if (goals.reduce((s, g) => s + g.wanted, 0) > pool) errors.push('月度投入超过可分配结余');

  return {
    today, base: h.base_currency,
    netWorth: known ? total - debtTotal : null, assetsTotal: total, debtTotal,
    cash, liquid, reserve, reserveShortfall: reserve == null ? null : Math.max(reserve - cash, 0),
    available, cashAllocated,
    free: available == null ? null : Math.max(available - cashAllocated, 0),
    incomplete, income: incomplete ? null : h.income + S.extraIncome, expenses: incomplete ? null : h.expenses + S.extraExpenses,
    minimum, surplus, unlinked, pool, committed, remaining,
    goals, rows, warnings: [...new Set(warnings)], missing, errors,
    executable: !missing.length && !errors.length,
  };
}

/* 缺口怎么补：给出三个互相独立的杠杆（与后端 needed_monthly 同口径，生产应由后端返回） */
function levers(g, res, h, scen, today) {
  if (!g.shortfall) return null;
  const base = { ...scen, monthly: { ...(scen.monthly || {}) } };
  // ① 每月多投
  const need = g.needed;
  const extra = need != null ? Math.max(need - g.affordable, 0) : null;
  const monthly = extra == null ? null : {
    extra, newMonthly: g.affordable + extra,
    fits: extra <= res.remaining,
    patch: { monthly: { [g.id]: g.affordable + extra } },
  };
  // ② 延期：逐月试到缺口为 0
  let ext = null;
  for (let k = 1; k <= 120; k++) {
    const r = goalResult({ ...h.goals.find(x => x.id === g.id) }, g.funded, g.affordable, g.wanted, { ...base, extend: (base.extend || 0) + k }, today);
    if (r.shortfall === 0) { ext = { months: k, due: r.due, patch: { extend: (base.extend || 0) + k } }; break; }
  }
  // ③ 下调目标：整数运算向上取整到 0.1%
  const pm = Math.ceil((g.shortfall * 1000) / g.target);
  const reduce = pm <= 1000 ? { pct: pm / 10, newTarget: g.target - g.shortfall, patch: { reducePct: (base.reducePct || 0) + pm / 10 } } : null;
  return { monthly, ext, reduce };
}

/* 本月行动：与 service.action_view 同口径，但「进度」只看本月已发生的新增投入，
   不再因为重设基准日期而清零（对应 P3） */
function actionsFor(plan, events, month, today) {
  if (!plan || plan.status !== 'active') return [];
  return plan.actions.filter(a => a.month === month).map(a => {
    const recs = events.filter(e => !e.voided && e.state === 'confirmed' && e.kind === 'transfer'
      && e.mode !== 'move_existing' && e.goal_id === a.goal_id && e.date.startsWith(month) && e.date <= today);
    const actual = recs.reduce((s, e) => s + (e.target_amount ?? e.amount), 0);
    const status = actual >= a.amount ? 'completed' : actual > 0 ? 'in_progress' : 'pending';
    return { ...a, actual, remaining: Math.max(a.amount - actual, 0), excess: Math.max(actual - a.amount, 0), status, records: recs };
  });
}

if (typeof module !== 'undefined') module.exports = { parseCents, fmtYuan, shiftDay, monthAfter, monthsBetween, calc, levers, actionsFor, goalResult };
