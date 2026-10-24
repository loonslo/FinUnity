/* ============================================================
   原型主程序 · 第 3 段：方案测算、财务建档
   ============================================================ */

/* ---------- 试算条件：界面用 % 与元，引擎用小数与分（I5） ---------- */
function scenFromParams(pr) {
  const s = blankScen();
  if (pr.extraIncome) s.extraIncome = centsInput(pr.extraIncome);
  if (pr.extraExpenses) s.extraExpenses = centsInput(pr.extraExpenses);
  if (pr.reducePct) s.reducePct = String(pr.reducePct);
  if (pr.ret) s.ret = String(pr.ret);
  if (pr.infl) s.infl = String(pr.infl);
  if (pr.extend) s.extend = pr.extend;
  Object.entries(pr.monthly || {}).forEach(([k, v]) => { s.monthly[k] = centsInput(v); });
  return s;
}
function mergeParams(base, patch) {
  return { ...base, ...patch, monthly: { ...(base.monthly || {}), ...(patch.monthly || {}) } };
}
function scenParse(sc) {
  const E = {};
  const money = (k) => {
    const v = String(sc[k] ?? '').trim();
    if (v === '') return 0;
    const c = parseCents(v);
    if (c === null) { E[k] = '请只填数字，最多两位小数（单位：元）'; return 0; }
    return c;
  };
  const pct = (k, lo, hi, label) => {
    const v = String(sc[k] ?? '').trim().replace('%', '');
    if (v === '') return 0;
    const n = Number(v);
    if (!isFinite(n)) { E[k] = '请填数字，例如 3 就是 3%'; return 0; }
    const pc = (x) => `${x < 0 ? '−' : ''}${Math.abs(x)}%`;
    if (n < lo || n > hi) { E[k] = `${label}需要在 ${pc(lo)} 到 ${pc(hi)} 之间`; return 0; }
    return n;
  };
  const params = {
    extraIncome: money('extraIncome'), extraExpenses: money('extraExpenses'),
    reducePct: pct('reducePct', 0, 100, '目标下调'), ret: pct('ret', -90, 50, '年化收益假设'), infl: pct('infl', 0, 30, '年通胀假设'),
    extend: Number(sc.extend) || 0, monthly: {},
  };
  S.household.goals.forEach((g) => {
    const v = String(sc.monthly[g.id] ?? '').trim();
    if (v !== '') {
      const c = parseCents(v);
      if (c === null) E['m-' + g.id] = '请只填数字，最多两位小数（单位：元）';
      else params.monthly[g.id] = c;
    }
  });
  return { params, E };
}
const scenTabs = () => ['A', 'B', 'C'].filter((k) => UI.scen[k]);

/* ---------- 对比表 ---------- */
function dcell(val, base, better, f = Y) {
  const d = val - base;
  const cls = d === 0 ? 'same' : (better === 'high') === d > 0 ? 'good' : 'bad';
  const txt = d === 0 ? '与当前方案相同' : `${d > 0 ? '＋' : '−'}${f(Math.abs(d))}`;
  return `<td class="${d ? 'chg' : ''}">${f(val)}<span class="delta ${cls}">${txt}</span></td>`;
}
function scenResults() {
  const p = plan();
  const base = RES();
  const cols = scenTabs().map((k) => {
    const { params, E } = scenParse(UI.scen[k]);
    return { k, E, params, res: calc(S.household, S.ledger, params, S.today) };
  });
  const active = cols.find((c) => c.k === UI.scen.tab) || cols[0];
  const head = `<tr><th></th><th class="base">${p ? `当前方案 v${p.version}` : '当前建档'}</th>${cols.map((c) => `<th>试算 ${c.k}${c.k === UI.scen.tab ? ' · 编辑中' : ''}</th>`).join('')}</tr>`;
  const out = [`<tr><td>每月可再分配</td><td class="base"><b>${Y(base.remaining)}</b></td>${cols.map((c) => dcell(c.res.remaining, base.remaining, 'high')).join('')}</tr>`];
  base.goals.forEach((g, gi) => {
    out.push(`<tr class="grp"><td colspan="${2 + cols.length}">${esc(g.name)} <span class="muted small" style="font-weight:400">· 第 ${g.priority} 优先 · 目标 ${Y(g.target)}</span></td></tr>`);
    out.push(`<tr><td>每月投入</td><td class="base"><b>${Y(g.affordable)}</b>${g.floor ? `<span class="delta same">${ic('lock', 12, 2)} 含已有定投 ${Y(g.floor)}（下限）</span>` : ''}</td>${cols.map((c) => dcell(c.res.goals[gi].affordable, g.affordable, 'high')).join('')}</tr>`);
    out.push(`<tr><td>到期日</td><td class="base"><b>${g.due}</b></td>${cols.map((c) => { const d = c.res.goals[gi].due; return `<td class="${d !== g.due ? 'chg' : ''}">${d}${d !== g.due ? '<span class="delta same">已延期</span>' : ''}</td>`; }).join('')}</tr>`);
    out.push(`<tr><td>到期测算</td><td class="base"><b>${Y(g.projected)}</b></td>${cols.map((c) => dcell(c.res.goals[gi].projected, g.projected, 'high')).join('')}</tr>`);
    out.push(`<tr><td>预计缺口</td><td class="base"><b>${g.shortfall ? Y(g.shortfall) : '无 ✓'}</b></td>${cols.map((c) => dcell(c.res.goals[gi].shortfall, g.shortfall, 'low')).join('')}</tr>`);
  });
  const errs = active ? [...Object.values(active.E), ...active.res.errors] : [];
  const bad = !!active && (Object.keys(active.E).length > 0 || active.res.errors.length > 0);
  return `<div class="card" ${fix('I6')}><div class="ledger-wrap"><table class="cmp"><thead>${head}</thead><tbody>${out.join('')}</tbody></table></div></div>
  ${errs.length ? `<div class="summary-err" role="alert" style="margin-top:14px">${ic('alert', 18)}<div>${errs.map((e) => `<div>${esc(e)}</div>`).join('')}${active.res.errors.length ? '<div style="font-weight:400;margin-top:4px">这套条件目前不可执行，不能生成方案草案。</div>' : ''}</div></div>` : ''}
  <div class="row" style="margin-top:16px">
    <button class="btn primary" type="button" data-act="scen-draft" ${bad ? 'disabled' : ''}>把「试算 ${active ? active.k : 'A'}」生成方案草案</button>
    <span class="hint">生成草案不会改变账目或当前方案；确认之后才生效。</span>
  </div>`;
}

function field(label, inner, { hint = '', err = '', id = '' } = {}) {
  const auto = id || ((inner.match(/\sid="([^"]+)"/) || [])[1] || '');   // 自动把标签和第一个带 id 的控件关联起来
  return `<div class="field"><label class="lab" ${auto ? `for="${auto}"` : ''}>${label}</label>${inner}${err ? `<div class="errmsg" role="alert">${ic('alert', 15)}<span>${esc(err)}</span></div>` : ''}${hint ? `<div class="hint">${hint}</div>` : ''}</div>`;
}
function scenInput(k, label, { unit = '元', hint = '', placeholder = '0' } = {}) {
  const sc = UI.scen[UI.scen.tab];
  const { E } = scenParse(sc);
  const base = scenFromParams(planParams());
  const changed = String(sc[k] ?? '') !== String(base[k] ?? '');
  const isMoney = unit === '元';
  return field(label, `<div class="pre ${isMoney ? 'l' : 'r'}">${isMoney ? '<span>¥</span>' : `<span>${unit}</span>`}<input class="in ${E[k] ? 'err' : ''} ${changed ? 'changed' : ''}" id="sc-${k}" inputmode="decimal" autocomplete="off" placeholder="${placeholder}" value="${esc(sc[k])}" data-bind="scen.${k}" data-k="sc-${k}"></div>`, { hint, err: E[k] || '' });
}
function scenControls() {
  const sc = UI.scen[UI.scen.tab];
  const { E } = scenParse(sc);
  const R = RES();
  const base = scenFromParams(planParams());
  const tabs = scenTabs();
  const goalInputs = S.household.goals.map((g, gi) => {
    const rg = R.goals[gi];
    const key = 'm-' + g.id;
    const raw = sc.monthly[g.id] ?? '';
    const typed = raw === '' ? null : parseCents(raw);
    const locked = rg.floor > 0;
    const below = locked && typed != null && typed < rg.floor;
    const placeholder = centsInput(rg.affordable);
    const baseRaw = base.monthly[g.id] ?? '';
    const changed = String(raw) !== String(baseRaw);
    return `<div class="field"><label class="lab" for="sc-${key}">${esc(g.name)} · 每月投入${locked ? ` <span class="chip warn" style="height:22px">${ic('lock', 12, 2)}含定投</span>` : ''}</label>
      <div class="pre l"><span>¥</span><input class="in ${E[key] ? 'err' : ''} ${changed ? 'changed' : ''}" id="sc-${key}" inputmode="decimal" autocomplete="off" placeholder="${placeholder}" value="${esc(raw)}" data-bind="scen.monthly.${g.id}" data-k="sc-${key}"></div>
      ${E[key] ? `<div class="errmsg" role="alert">${ic('alert', 15)}<span>${esc(E[key])}</span></div>` : ''}
      ${locked ? `<div class="lock ${below ? '' : ''}" ${fix('I4')}>${ic('lock', 15, 2)}<span>${below ? `你填的 ${Y(typed)} 低于已有定投 <b>${Y(rg.floor)}</b>/月，已按 ${Y(rg.floor)} 计算。` : `已有定投 <b>${Y(rg.floor)}</b>/月，是这个目标每月投入的下限。`}要试算「暂停定投」，请先到建档里调整定投。</span></div>` : `<div class="hint">留空 = 沿用当前方案（${Y(rg.affordable)}）</div>`}</div>`;
  }).join('');
  return `<aside class="card scen-controls rv" style="--i:3" aria-label="试算条件">
    <div class="scen-tabs" role="tablist" aria-label="试算">
      ${tabs.map((k) => `<button type="button" class="tab" role="tab" aria-selected="${UI.scen.tab === k}" data-act="scen-tab" data-t="${k}">试算 ${k}</button>`).join('')}
      ${tabs.length < 3 ? `<button type="button" class="btn ghost sm" data-act="scen-add">${ic('plus', 15, 2)}新试算</button>` : ''}
    </div>
    <p class="hint">在这里随便算：不改账目，也不改当前方案。所有比例都用 %，填 3 就是 3%。</p>
    <div class="group-title">收支假设</div>
    ${scenInput('extraIncome', '每月额外收入', { hint: '例如年终奖折算到每月；只用于试算，不会写进收支。' })}
    ${scenInput('extraExpenses', '每月额外支出')}
    <div class="group-title">目标与市场假设</div>
    ${scenInput('reducePct', '目标金额下调', { unit: '%', hint: '填 8 表示把每个目标都降低 8%。' })}
    ${scenInput('ret', '年化收益假设', { unit: '%', hint: '默认 0%。这是假设，不是收益保证。' })}
    ${scenInput('infl', '年通胀假设', { unit: '%', hint: '默认 0%。填 2 表示每年物价涨 2%，目标金额会相应变大。' })}
    ${field('延期目标', `<div class="stepper" role="group" aria-label="延期月数"><button type="button" data-act="ext" data-d="-1" aria-label="少 1 个月">−</button><output>${sc.extend || 0} 个月</output><button type="button" data-act="ext" data-d="1" aria-label="多 1 个月">＋</button></div>`, { hint: '所有目标的到期日一起往后推。' })}
    <div class="group-title">每月投入</div>
    ${goalInputs}
    <div class="row"><button class="btn sm" type="button" data-act="scen-reset">还原为当前方案</button>${tabs.length < 3 ? '<button class="btn sm ghost" type="button" data-act="scen-copy">复制为新试算</button>' : ''}</div>
  </aside>`;
}

function draftCard() {
  const d = S.pending;
  if (!d) return '';
  const p = plan();
  const stale = d.rev !== S.rev;
  const base = RES();
  const res = calc(S.household, S.ledger, d.params, S.today);
  const gaps = res.goals.filter((g) => g.shortfall > 0);
  const needConsent = gaps.length > 0 || res.reserveShortfall > 0;
  const next = (p ? p.version : 0) + 1;
  const lines = res.goals.map((g, i) => {
    const b = base.goals[i];
    const ch = (a, c, f = Y) => (a === c ? f(a) : `${f(c)} <span class="muted small">（原 ${f(a)}）</span>`);
    return `<tr><td>${esc(g.name)}</td><td class="r">${ch(b.affordable, g.affordable)}</td><td class="r">${ch(b.projected, g.projected)}</td><td class="r">${ch(b.shortfall, g.shortfall)}</td></tr>`;
  }).join('');
  const blocked = stale || (needConsent && !UI.draftConsent) || !res.executable;
  const why = stale ? '草案已过期，先重新测算。' : !res.executable ? '这份草案不可执行。' : needConsent && !UI.draftConsent ? '勾选上面的确认后才能采用。' : '';
  return `<section class="card draft ${stale ? 'stale' : ''} rv" style="--i:2" ${fix(stale ? 'P5' : 'I9')} aria-label="方案草案">
    <div class="row"><h3>方案草案 v${next} · ${stale ? '已过期' : '待确认'}</h3>${chip(`基于账目修订 ${d.rev}`, stale ? 'warn' : 'plain')}<span class="sp"></span><span class="muted small">${esc(d.created_at)}</span></div>
    ${stale ? `<p style="margin:10px 0 0;color:var(--warn);font-weight:600">${ic('alert', 16)} 生成草案之后，账目又有了新记录（现在是修订 ${S.rev}）。过期的草案不能确认。</p>` : '<p class="muted" style="margin-top:6px">确认前，先对照它和当前方案的差别：</p>'}
    <div class="ledger-wrap" style="margin:12px 0"><table class="cmp"><thead><tr><th>目标</th><th>每月投入</th><th>到期测算</th><th>预计缺口</th></tr></thead><tbody>${lines}</tbody></table></div>
    <p class="hint" style="margin-bottom:12px">确认后：为本月生成新的投入计划；方案 v${p ? p.version : 0} 保留为历史版本；不会自动交易，也不会移动资金。</p>
    ${needConsent && !stale ? `<label class="consent"><input type="checkbox" data-act="draft-consent" ${UI.draftConsent ? 'checked' : ''}><span>我已了解：${gaps.map((g) => `「${esc(g.name)}」仍有 ${Y(g.shortfall)} 缺口`).join('；')}${res.reserveShortfall > 0 ? `；应急预留还差 ${Y(res.reserveShortfall)}` : ''}。</span></label>` : ''}
    <div class="row" style="margin-top:14px">
      ${stale ? `<button class="btn primary" type="button" data-act="plan-retest">基于最新账目重新测算</button>` : `<button class="btn primary" type="button" data-act="plan-confirm" ${blocked ? 'disabled' : ''}>确认采用 v${next}</button>`}
      <button class="btn" type="button" data-act="plan-discard">放弃草案</button>
      ${why && !stale ? `<span class="hint">${why}</span>` : ''}
    </div>
  </section>`;
}

function versionsHTML() {
  const items = [];
  if (S.pending) items.push(`<li><div class="row"><b>草案</b>${chip(S.pending.rev !== S.rev ? '已过期' : '待确认', S.pending.rev !== S.rev ? 'warn' : 'plain')}<span class="muted small">${esc(S.pending.created_at)}</span></div></li>`);
  S.plans.slice().reverse().forEach((p) => items.push(`<li class="${p.status === 'active' ? 'on' : ''}"><div class="row"><b>方案 v${p.version}</b>${p.status === 'active' ? chip('使用中', 'up') : chip('历史版本', 'plain')}<span class="muted small">${esc(p.confirmed_at)} 确认</span></div><p class="muted small" style="margin:2px 0 0">${esc(p.summary)}</p></li>`));
  return `<ul class="vers" ${fix('N4')}>${items.join('') || '<li class="muted">还没有保存过方案。</li>'}</ul>`;
}

function viewScenarios() {
  if (S.rev === 0) return needHousehold('比较不同的安排');
  if (RES().incomplete) return `${pageHead({ eyebrow: '试算 → 方案', title: '比较不同的安排' })}<section class="card empty rv" style="--i:2"><h2>建档还没填完，先不试算</h2><ul class="stack">${RES().missing.map((m) => `<li class="row">${ic('alert', 18)}<span>${esc(m)}</span></li>`).join('')}</ul><div class="row">${btn('去补全建档', { cls: 'primary', href: '#/household' })}</div></section>`;
  return `
  ${pageHead({ eyebrow: '试算 → 方案', title: '比较不同的安排', lede: '先试算，再选一份生成方案草案，最后确认。每一步都不会动你的账目。', meta: planMeta() })}
  ${draftCard()}
  <div class="scen">
    ${scenControls()}
    <div>
      <div class="sec-head rv" style="--i:3;margin-top:0"><h2>结果对比</h2><p>改动过的格子会高亮；绿色=更好，红色=更差</p></div>
      <div id="scenResults" class="rv" style="--i:4">${scenResults()}</div>
      <section class="sec rv" style="--i:5"><div class="sec-head"><h2>方案版本</h2></div><div class="card pad">${versionsHTML()}</div></section>
    </div>
  </div>`;
}

/* ---------- 财务建档 ---------- */
const KIND_NAME = { cash: '现金账户', investment: '投资账户', property: '房产', other: '其他资产' };
const LIQ_NAME = { available: '可动用', restricted: '受限', illiquid: '不易变现' };
const Q_NAME = { verified: '已核实', estimated: '估算', unknown: '未知' };

function inMoney(path, { label, hint = '', ph = '', id }) {
  const v = UI.raw[path] ?? centsInput(getPath(UI.hh, path));
  const changed = getPath(UI.hh, path) !== getPath(S.household, path);
  const err = UI.rawErr[path];
  return field(label, `<div class="pre l"><span>¥</span><input class="in ${err ? 'err' : ''} ${changed ? 'changed' : ''}" id="${id || 'f-' + path}" inputmode="decimal" autocomplete="off" placeholder="${ph}" value="${esc(v)}" data-bind="hh.${path}" data-kind="money" data-k="f-${path}"></div>`, { hint, err: err ? '请只填数字，最多两位小数（单位：元）' : '' });
}
function inText(path, { label, hint = '', maxlength = 100 }) {
  const changed = getPath(UI.hh, path) !== getPath(S.household, path);
  return field(label, `<input class="in ${changed ? 'changed' : ''}" id="f-${path}" value="${esc(getPath(UI.hh, path))}" maxlength="${maxlength}" data-bind="hh.${path}" data-kind="text" data-k="f-${path}">`, { hint });
}
function inDate(path, { label, hint = '', max }) {
  const changed = getPath(UI.hh, path) !== getPath(S.household, path);
  return field(label, `<input type="date" class="in ${changed ? 'changed' : ''}" id="f-${path}" value="${esc(getPath(UI.hh, path))}" ${max ? `max="${max}"` : ''} data-bind="hh.${path}" data-kind="date" data-k="f-${path}">`, { hint });
}
function inSelect(path, opts, { label, hint = '' }) {
  const changed = getPath(UI.hh, path) !== getPath(S.household, path);
  return field(label, `<select class="in ${changed ? 'changed' : ''}" id="f-${path}" data-bind="hh.${path}" data-kind="select" data-k="f-${path}">${Object.entries(opts).map(([k, n]) => `<option value="${k}" ${getPath(UI.hh, path) === k ? 'selected' : ''}>${n}</option>`).join('')}</select>`, { hint });
}

function completeness() {
  const h = UI.hh;
  const items = [
    ['资产', h.assets.length > 0 && h.assets.every((a) => a.amount != null), '至少一个账户，且余额都已填写'],
    ['月收入', h.income != null, '填写每月税后收入'],
    ['月支出', h.expenses != null && h.essential != null, '总支出与其中的必要支出'],
    ['负债', h.debts.length > 0 || h.no_debt, '没有负债也请勾选确认'],
    ['目标', h.goals.length > 0, '至少一个有金额和期限的目标'],
  ];
  return items;
}

function allocMatrix() {
  const h = UI.hh;
  const accts = h.assets.filter((a) => (a.kind === 'cash' || a.kind === 'investment') && a.liq === 'available');
  if (!accts.length || !h.goals.length) return `<p class="muted">先添加至少一个可动用账户和一个目标，才能分配资金。</p>`;
  const reserve = h.essential == null ? 0 : h.essential * h.reserve_months;
  const cell = (a, g) => {
    const idx = h.allocations.findIndex((x) => x.asset_id === a.id && x.goal_id === g.id);
    const path = `alloc.${a.id}.${g.id}`;
    const v = UI.raw[path] ?? (idx >= 0 ? centsInput(h.allocations[idx].amount) : '');
    return `<td><input class="in" aria-label="${esc(a.name)} 分给 ${esc(g.name)}" inputmode="decimal" placeholder="0" value="${esc(v)}" data-bind="alloc" data-asset="${a.id}" data-goal="${g.id}" data-k="${path}"></td>`;
  };
  const sum = (a) => h.allocations.filter((x) => x.asset_id === a.id).reduce((s, x) => s + x.amount, 0);
  const rows = accts.map((a) => {
    const free = (a.amount || 0) - (a.kind === 'cash' ? reserve : 0) - sum(a);
    return `<tr><td><b>${esc(a.name)}</b><div class="faint small">余额 ${Y(a.amount)}${a.kind === 'cash' ? ` · 先扣应急预留 ${Y(reserve)}` : ''}</div></td>${h.goals.map((g) => cell(a, g)).join('')}<td class="${free < 0 ? 'over' : 'left'}" id="free-${a.id}"><b>${Y(free)}</b>${free < 0 ? '<div class="small">超额分配</div>' : '<div class="small">未分配</div>'}</td></tr>`;
  }).join('');
  return `<div class="ledger-wrap"><table class="matrix"><thead><tr><th>账户</th>${h.goals.map((g) => `<th>${esc(g.name)}</th>`).join('')}<th>可再分配</th></tr></thead><tbody>${rows}</tbody></table></div>`;
}

function viewHousehold() {
  const h = UI.hh;
  const yday = shiftDay(S.today, -1);
  const dirty = hhChanges();
  const empty = S.rev === 0;
  const comp = completeness();
  return `
  ${pageHead({ eyebrow: empty ? '第 1 步' : `已保存 · 修订 ${S.hhRev}`, title: '先把现状说清楚', lede: '余额是所选日期的日终记录；月收支用于预测，不代表已经到账。', meta: empty ? btn('用示例体验', { cls: 'sm', act: 'hh-example' }) : '' })}
  <div class="dirtybar ${dirty ? 'dirty' : ''} rv" style="--i:2" id="dirtybar" ${fix('I1')} role="status" aria-live="polite">${dirtyBarInner()}</div>

  <section class="h-sec rv" style="--i:3">
    ${secHead('01', '基本信息与月收支')}
    <div class="card pad"><div class="h-grid">
      ${inText('name', { label: '名称' })}
      ${inDate('as_of', { label: '余额截至（日终）', max: yday, hint: `选<b>已经结束</b>的一天（最晚 ${md(yday)}）。今天的收支请到「执行」里记——选今天会让今天的账记不进去。` })}
      ${inSelect('base_currency', { CNY: 'CNY · 人民币' }, { label: '基础币种' })}
      ${inMoney('income', { label: '月收入（税后）', ph: '例如 16000' })}
      ${inMoney('expenses', { label: '月总支出', ph: '例如 7000' })}
      ${inMoney('essential', { label: '其中必要支出', hint: '应急预留 = 必要支出 × 月数。' })}
      ${field('应急预留月数', `<div class="stepper" role="group" aria-label="应急预留月数"><button type="button" data-act="rm" data-d="-1" aria-label="少 1 个月">−</button><output id="rm-out">${h.reserve_months} 个月</output><button type="button" data-act="rm" data-d="1" aria-label="多 1 个月">＋</button></div>`, { hint: '默认 6 个月，可调整。' })}
      ${inSelect('risk', { '尚未明确': '尚未明确', '谨慎': '谨慎', '平衡': '平衡', '能接受较大波动': '能接受较大波动' }, { label: '风险偏好' })}
      ${inText('source', { label: '收支来源' })}
    </div>
    <div class="row" style="margin-top:20px;gap:8px" aria-label="资料完整度">${comp.map(([n, ok, t]) => `<span class="chip ${ok ? 'up' : 'warn'}" title="${esc(t)}">${ok ? ic('check', 14, 2) : ic('alert', 14, 2)}${n}${ok ? '' : ' · ' + esc(t)}</span>`).join('')}</div></div>
  </section>

  <section class="h-sec rv" style="--i:4">
    ${secHead('02', '账户与资产', '每个账户的余额是「所选日期日终」的数')}
    <div class="card pad">
      ${h.assets.map((a, i) => `<div class="acct">
        ${inText(`assets.${i}.name`, { label: '名称' })}
        ${inSelect(`assets.${i}.kind`, KIND_NAME, { label: '类型' })}
        ${inSelect(`assets.${i}.liq`, LIQ_NAME, { label: '流动性' })}
        ${inMoney(`assets.${i}.amount`, { label: '余额', ph: '未知可留空' })}
        ${inDate(`assets.${i}.observed_on`, { label: '日终日期', max: yday })}
        ${inSelect(`assets.${i}.quality`, Q_NAME, { label: '数值质量' })}
        <div class="more"><span>来源：${esc(a.source)} · 币种 ${a.cur}</span><button type="button" class="linkbtn" data-act="hh-del" data-kind="assets" data-i="${i}">移除这个账户</button></div>
      </div>`).join('') || '<p class="muted" style="padding:6px 0">还没有账户。点下面添加。</p>'}
      <div class="row" style="margin-top:16px"><button class="btn sm" type="button" data-act="hh-add" data-kind="assets">${ic('plus', 15, 2)}添加账户</button></div>
    </div>
  </section>

  <section class="h-sec rv" style="--i:5">
    ${secHead('03', '负债与最低还款')}
    <div class="card pad">
      ${h.debts.map((d, i) => `<div class="acct" style="grid-template-columns:minmax(0,1.5fr) minmax(0,1fr) minmax(0,1fr) minmax(0,1.4fr)">
        ${inText(`debts.${i}.name`, { label: '名称' })}${inMoney(`debts.${i}.balance`, { label: '余额' })}${inMoney(`debts.${i}.minimum`, { label: '每月最低还款' })}
        <label class="field" style="align-self:end;min-height:44px;display:flex;gap:10px;align-items:center"><input type="checkbox" style="width:20px;height:20px;accent-color:var(--olive)" ${d.in_expenses ? 'checked' : ''} data-bind="hh.debts.${i}.in_expenses" data-kind="check" data-k="f-debt-${i}"><span>已包含在月总支出里</span></label>
        <div class="more"><button type="button" class="linkbtn" data-act="hh-del" data-kind="debts" data-i="${i}">移除</button></div>
      </div>`).join('')}
      ${!h.debts.length ? `<label class="row" style="gap:10px"><input type="checkbox" style="width:20px;height:20px;accent-color:var(--olive)" ${h.no_debt ? 'checked' : ''} data-bind="hh.no_debt" data-kind="check" data-k="f-nodebt"><span>我确认目前没有负债</span></label>` : ''}
      <div class="row" style="margin-top:14px"><button class="btn sm" type="button" data-act="hh-add" data-kind="debts">${ic('plus', 15, 2)}添加负债</button></div>
    </div>
  </section>

  <section class="h-sec rv" style="--i:6">
    ${secHead('04', '目标与优先级', '排在上面的目标，资金不够时先拿到每月预算')}
    <div class="card pad" ${fix('I10')}>
      ${h.goals.map((g, i) => `<div class="goal-row">
        <div class="rank"><button type="button" data-act="goal-move" data-i="${i}" data-d="-1" aria-label="上移" ${i === 0 ? 'disabled' : ''}>${ic('up', 14, 2)}</button><b>${i + 1}</b><button type="button" data-act="goal-move" data-i="${i}" data-d="1" aria-label="下移" ${i === h.goals.length - 1 ? 'disabled' : ''}>${ic('down', 14, 2)}</button></div>
        <div>${inText(`goals.${i}.name`, { label: '目标' })}<button type="button" class="linkbtn small" style="margin-top:6px" data-act="hh-del" data-kind="goals" data-i="${i}">移除</button></div>
        ${inMoney(`goals.${i}.target`, { label: '目标金额' })}
        ${inDate(`goals.${i}.due`, { label: '到期日期' })}
        ${inMoney(`goals.${i}.monthly`, { label: '每月投入' })}
      </div>`).join('') || '<p class="muted">还没有目标。</p>'}
      <div class="row" style="margin-top:16px"><button class="btn sm" type="button" data-act="hh-add" data-kind="goals">${ic('plus', 15, 2)}添加目标</button></div>
    </div>
  </section>

  <section class="h-sec rv" style="--i:7">
    ${secHead('05', '现有资金分配', '只是用途标记，不会移动资金')}
    <div class="card pad" id="alloc">${allocMatrix()}<p class="hint" style="margin-top:12px">应急预留先扣，不能再分给目标；同一笔钱不能重复分配。</p></div>
  </section>

  <section class="h-sec rv" style="--i:8">
    ${secHead('06', '已有定投与资金承诺')}
    <div class="card pad">
      ${h.commitments.map((c, i) => `<div class="acct" style="grid-template-columns:minmax(0,1.4fr) minmax(0,1fr) minmax(0,1.4fr)">
        ${inText(`commitments.${i}.name`, { label: '名称' })}${inMoney(`commitments.${i}.monthly`, { label: '每月金额' })}
        ${inSelect(`commitments.${i}.goal_id`, Object.assign({ '': '独立承诺（不属于任何目标）' }, Object.fromEntries(h.goals.map((g) => [g.id, g.name]))), { label: '对应目标', hint: '关联后，它计入该目标每月投入的<b>下限</b>，不会重复扣减。' })}
        <div class="more"><button type="button" class="linkbtn" data-act="hh-del" data-kind="commitments" data-i="${i}">移除</button></div>
      </div>`).join('') || '<p class="muted">没有定投。</p>'}
      <div class="row" style="margin-top:14px"><button class="btn sm" type="button" data-act="hh-add" data-kind="commitments">${ic('plus', 15, 2)}添加定投</button></div>
    </div>
  </section>

  <section class="h-sec rv" style="--i:9">
    <div class="card pad"><details><summary style="cursor:pointer;font-weight:600;color:var(--olive)">外币汇率 · 当前没有外币账户，无需填写</summary><p class="hint" style="margin-top:10px">有外币账户时，在这里填 1 单位外币折合多少 CNY，并写明来源和日期；没有汇率的资产不会被当成零。</p></details></div>
    <div class="card pad" style="margin-top:14px"><details><summary style="cursor:pointer;font-weight:600;color:var(--olive)">高级：导入或核对建档 JSON</summary><p class="hint" style="margin-top:10px">给开发者用的入口，普通使用不需要。</p></details></div>
  </section>`;
}
function dirtyBarInner() {
  const n = hhChanges();
  return n
    ? `<span class="t"><b>有 ${n} 处未保存的修改</b><span class="muted small"> · 测算、记账、刷新都不会清掉它们；离开页面前会再问你一次。</span></span><button class="btn sm" type="button" data-act="hh-discard">放弃修改</button><button class="btn primary sm" type="button" data-act="hh-save">保存建档</button>`
    : `<span class="t muted">${S.rev === 0 ? '填好账户、收支和目标后保存。' : `已保存 · 修订 ${S.hhRev}。没有修改时不需要保存。`}</span><button class="btn primary sm" type="button" disabled>保存建档</button>`;
}
