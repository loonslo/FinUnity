/* ============================================================
   原型主程序 · 第 5 段：渲染、路由、事件、启动
   ============================================================ */

function render({ top = false } = {}) {
  const ae = document.activeElement;
  const key = ae && ae.dataset && ae.dataset.k;
  const sel = ae && typeof ae.selectionStart === 'number' ? [ae.selectionStart, ae.selectionEnd] : null;
  const y = window.scrollY;
  const views = { overview: viewOverview, household: viewHousehold, scenarios: viewScenarios, execution: viewExecution };
  const app = $('#app');
  app.innerHTML = shell(views[UI.route]());
  app.classList.toggle('play', !!UI.play);
  UI.play = false;
  document.documentElement.dataset.theme = UI.theme;
  document.body.classList.toggle('notes', UI.notes);
  document.title = `${{ overview: '我的规划', household: '财务建档', scenarios: '方案测算', execution: '执行与复盘' }[UI.route]} · FinUnity 优化原型`;
  window.scrollTo(0, top ? 0 : y);
  if (key && !UI.overlay) {
    const el = $(`#app [data-k="${key}"]`);
    if (el) { el.focus({ preventScroll: true }); if (sel) { try { el.setSelectionRange(sel[0], sel[1]); } catch (e) { /* 非文本输入 */ } } }
  }
  renderOverlay();
}

/* ---------- 路由 ---------- */
function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [name, qs = ''] = raw.split('?');
  return { name: ROUTES.includes(name) ? name : 'overview', params: new URLSearchParams(qs) };
}
function applyParams(q) {
  if (q.get('still')) document.documentElement.classList.add('still');
  if (q.get('demo') && DEMOS[q.get('demo')] && q.get('demo') !== S.demo) resetDemo(q.get('demo'));
  if (q.get('theme')) UI.theme = q.get('theme') === 'dark' ? 'dark' : 'light';
  if (q.get('notes')) UI.notes = q.get('notes') === '1';
  if (q.get('filter')) UI.filter = q.get('filter');
  if (q.get('hh') === 'dirty') {
    UI.hh.name = '陈家的家庭财务';
    UI.hh.income = 1650000;
    UI.hh.assets[0].amount = 9760000;
    UI.hh.assets[0].observed_on = shiftDay(S.today, -1);
  }
  if (q.get('lever')) {
    const [gid, kind] = q.get('lever').split(':');
    const R = RES();
    const g = R.goals.find((x) => x.id === gid);
    const lv = g && levers(g, R, S.household, planParams(), S.today);
    if (lv && lv[kind]) UI.scen.A = scenFromParams(mergeParams(planParams(), lv[kind].patch));
  }
  if (q.get('scenB') === '1') {
    UI.scen.A = scenFromParams(mergeParams(planParams(), { monthly: { study: 233334 } }));
    UI.scen.B = scenFromParams(mergeParams(planParams(), { extend: 2 }));
    UI.scen.tab = 'A';
  }
  if (q.get('scenerr') === '1') { UI.scen.A.ret = '80'; }
  if (q.get('lock') === '1') { UI.scen.A.monthly.family = '1000'; }
  if (q.get('draftplan') === '1') {
    const { params } = scenParse(UI.scen.A);
    S.pending = { id: 'draft2', rev: S.rev, params, created_at: `${S.today} 19:05` };
  }
  if (q.get('consent') === '1') UI.draftConsent = true;
  if (q.get('sheet')) {
    const kind = q.get('kind') || 'expense';
    if (q.get('sheet') === 'record') {
      if (q.get('action')) {
        const a = ACTS().find((x) => x.id === q.get('action')) || ACTS().find((x) => x.status !== 'completed');
        UI.drafts = a ? [draftFromAction(a)] : [newDraft(kind)];
      } else UI.drafts = [newDraft(kind)];
      if (q.get('drafts') === '2') {
        const a2 = ACTS().find((x) => x.status !== 'completed');
        UI.drafts = [a2 ? draftFromAction(a2) : newDraft('contribution'), newDraft('expense', { amount: '128.50', note: '超市' })];
        UI.cur = 0;
      }
      if (q.get('err') === '1') {
        UI.drafts[UI.cur].date = S.household.assets[0].observed_on;
        UI.drafts[UI.cur].amount = UI.drafts[UI.cur].amount || '3000';
        UI.showErr = true;
      }
      if (q.get('err') === '2') {
        UI.drafts[0].amount = '3000';
        UI.drafts[1].amount = '';
        UI.cur = 1;
        UI.showErr = true;
      }
      UI.overlay = { type: 'record' };
    } else if (q.get('sheet') === 'assist') UI.overlay = { type: 'assist' };
    else if (q.get('sheet') === 'legend') UI.overlay = { type: 'legend' };
    UI.fresh = true;
  }
  if (q.get('dialog')) {
    const n = q.get('dialog');
    if (n === 'void') UI.overlay = { type: 'dialog', name: 'void', id: S.ledger[0].id };
    if (n === 'leave') UI.overlay = { type: 'dialog', name: 'leave', to: '#/execution' };
    if (n === 'recon') { UI.overlay = { type: 'dialog', name: 'recon', asset: 'cash' }; UI.reconVal = q.get('val') || '97650'; }
    if (n === 'example') UI.overlay = { type: 'dialog', name: 'example' };
    UI.fresh = true;
  }
  if (q.get('pop') === '1') UI.pop = true;
}
function onRoute(initial) {
  const { name, params } = parseHash();
  const changed = name !== UI.route;
  UI.route = name;
  if (changed || initial) UI.play = true;
  if (changed && UI.overlay && UI.overlay.type !== 'dialog') { UI.overlay = null; UI.pop = false; }
  applyParams(params);
  render({ top: changed || initial });
}

/* ---------- 动作 ---------- */
function newHouseholdRow(kind) {
  const yday = shiftDay(S.today, -1);
  const id = kind[0] + uid();
  if (kind === 'assets') return { id, name: '新账户', kind: 'cash', liq: 'available', cur: 'CNY', amount: null, observed_on: yday, quality: 'verified', source: '手动录入' };
  if (kind === 'debts') return { id, name: '新负债', balance: null, minimum: null, in_expenses: false };
  if (kind === 'goals') return { id, name: '新目标', target: 1000000, due: `${+S.today.slice(0, 4) + 1}-12-31`, monthly: 0 };
  return { id, name: '定投', monthly: 0, goal_id: '' };
}
function saveHousehold() {
  const wasEmpty = S.rev === 0;
  S.household = clone(UI.hh);
  S.rev += 1;
  S.hhRev = S.rev;
  UI.raw = {}; UI.rawErr = {};
  if (wasEmpty) S.today = S.today;
  return wasEmpty;
}
const ACT = {
  'banner-x'() { UI.banner = false; render(); },
  skip() { const m = $('#main'); if (m) m.focus(); },
  'open-record'(el) { openRecord(el.dataset.kind, el.dataset.action); },
  'close-overlay'() { closeOverlay(); },
  'dlg-close'() { closeOverlay(); },
  pop() { UI.pop = !UI.pop; renderOverlay(); },
  assist() { UI.lastFocus = document.activeElement; UI.overlay = { type: 'assist' }; UI.fresh = true; renderOverlay(); },
  legend() { UI.lastFocus = document.activeElement; UI.pop = false; UI.overlay = { type: 'legend' }; UI.fresh = true; renderOverlay(); },
  refresh() { render(); toast('已刷新资料。你<b>未保存的建档修改</b>和<b>记账草稿</b>都还在。'); },
  theme(el) { UI.theme = el.checked ? 'dark' : 'light'; persist(); document.documentElement.dataset.theme = UI.theme; },
  notes(el) { UI.notes = el.checked; persist(); document.body.classList.toggle('notes', UI.notes); },
  demo(el) { resetDemo(el.value); persist(); UI.pop = true; UI.play = true; if (UI.route !== 'overview') { location.hash = '#/overview'; } render({ top: true }); },
  filter(el) { UI.filter = el.dataset.f; render(); },

  /* —— 记账抽屉 —— */
  'pick-draft'(el) { UI.cur = +el.dataset.i; renderOverlay(); },
  'add-draft'() { UI.drafts.push(newDraft('expense')); UI.cur = UI.drafts.length - 1; UI.showErr = false; persist(); renderOverlay(); },
  'del-draft'(el) { UI.drafts.splice(+el.dataset.i, 1); UI.cur = Math.max(0, Math.min(UI.cur, UI.drafts.length - 1)); persist(); renderOverlay(); },
  'clear-draft'() { UI.drafts = [newDraft('expense')]; UI.cur = 0; UI.showErr = false; persist(); renderOverlay(); },
  kind(el) {
    const d = UI.drafts[UI.cur];
    const k = el.dataset.k2;
    const cash = cashAccts()[0];
    d.kind = k; d.action_id = null; d.principal = '';
    if (k === 'contribution' || k === 'transfer') d.target_id = (moveTargets(d.asset_id)[0] || {}).id || '';
    if (!d.asset_id && cash) d.asset_id = cash.id;
    persist(); renderOverlay();
  },
  fstate(el) { UI.drafts[UI.cur].state = el.checked ? 'forecast' : 'confirmed'; persist(); renderOverlay(); },
  'date-pick'(el) { UI.drafts[UI.cur].date = el.dataset.d; persist(); renderOverlay(); },
  'save-drafts'() {
    UI.showErr = true;
    const errs = UI.drafts.map(hardErrors);
    const first = errs.findIndex((e) => e.length);
    if (first >= 0) {
      UI.cur = first;
      renderOverlay();
      const bad = $('#sheetBody .in.err');
      if (bad) { bad.scrollIntoView({ block: 'center', behavior: 'smooth' }); bad.focus({ preventScroll: true }); }
      return;
    }
    const made = UI.drafts.map((d) => {
      const kind = d.kind === 'contribution' || d.kind === 'transfer' ? 'transfer' : d.kind === 'debt' ? 'debt_payment' : d.kind;
      return { id: uid(), kind, mode: 'new_funding', state: d.state, quality: d.quality, date: d.date, asset_id: d.asset_id,
        target_asset_id: kind === 'transfer' ? d.target_id : null, amount: parseCents(d.amount) || 0, target_amount: null,
        goal_id: d.kind === 'contribution' ? d.goal_id : null, debt_id: d.kind === 'debt' ? d.debt_id : null,
        principal: d.kind === 'debt' ? parseCents(d.principal) || 0 : null, source: d.source, note: d.note, voided: false, action_id: d.action_id };
    });
    const touched = made.filter((e) => e.goal_id && e.state === 'confirmed').map((e) => ACTS().find((a) => a.goal_id === e.goal_id)).filter(Boolean);
    S.ledger.push(...made);
    S.rev += made.length;
    const n = made.length;
    UI.drafts = []; UI.cur = 0; UI.showErr = false; UI.overlay = null; UI.pulse = touched[0] ? touched[0].id : null;
    persist();
    render();
    renderOverlay();
    let msg = `已保存 ${n} 条记录。`;
    if (touched[0]) {
      const a = ACTS().find((x) => x.id === touched[0].id);
      msg += `「${esc(goal(a.goal_id).name)}」本月进度 <b class="num">${Y(a.actual)} / ${Y(a.amount)}</b>${a.status === 'completed' ? ' · 已完成' : ''}。`;
      requestAnimationFrame(() => { const c = $(`#act-${a.id}`); if (c) c.scrollIntoView({ block: 'center', behavior: 'smooth' }); });
      setTimeout(() => { UI.pulse = null; }, 2200);
    }
    toast(msg, n === 1 ? { label: '作废这笔', act: 'void-open', data: made[0].id } : {});
  },

  /* —— 作废 / 对账 —— */
  'void-open'(el) { UI.lastFocus = document.activeElement; UI.overlay = { type: 'dialog', name: 'void', id: el.dataset.id }; UI.fresh = true; renderOverlay(); },
  'void-confirm'(el) {
    const e = S.ledger.find((x) => x.id === el.dataset.id);
    e.voided = true; S.rev += 1;
    UI.overlay = null; render(); renderOverlay();
    toast('已作废。余额和本月进度已重新计算；这条记录留在历史里，标为「已作废」。');
  },
  'void-redo'(el) {
    const e = S.ledger.find((x) => x.id === el.dataset.id);
    e.voided = true; S.rev += 1;
    const d = newDraft(e.goal_id ? 'contribution' : e.kind === 'debt_payment' ? 'debt' : e.kind, {
      date: e.date, asset_id: e.asset_id, target_id: e.target_asset_id || '', goal_id: e.goal_id || '', amount: centsInput(e.amount),
      note: e.note, source: e.source, action_id: e.goal_id ? (ACTS().find((a) => a.goal_id === e.goal_id) || {}).id || null : null,
    });
    UI.drafts = [d]; UI.cur = 0; UI.showErr = false;
    render(); UI.overlay = { type: 'record' }; UI.fresh = true; persist(); renderOverlay();
    toast('原记录已作废，内容已带回草稿，改好再保存。');
  },
  'recon-open'(el) { UI.lastFocus = document.activeElement; UI.reconVal = null; UI.overlay = { type: 'dialog', name: 'recon', asset: el.dataset.asset }; UI.fresh = true; renderOverlay(); },
  'recon-save'(el) {
    const a = asset(el.dataset.asset);
    const row = RES().rows.find((r) => r.id === a.id);
    const diff = parseCents(UI.reconVal) - row.balance;
    S.ledger.push({ id: uid(), kind: diff > 0 ? 'income' : 'expense', adjust: true, mode: 'new_funding', state: 'confirmed', quality: 'verified', date: S.today, asset_id: a.id, amount: Math.abs(diff), goal_id: null, source: '对账', note: `银行实际余额 ${Y(parseCents(UI.reconVal))}`, voided: false });
    S.rev += 1; UI.overlay = null; UI.reconVal = null; render(); renderOverlay();
    toast(`已生成对账调整 ${Y(diff, { sign: true })}。建档基准日期没动，本月投入进度也不受影响。`);
  },

  /* —— 复盘、续期 —— */
  'review-make'(el) {
    S.reviewRev = S.rev; S.reviewedAt = S.today; render();
    toast(`已更新 ${monthLabel(el.dataset.m)}复盘。只汇总你确认过的记录，没有改动建档和流水。`);
  },
  'renew-consent'(el) { UI.renewConsent = el.checked; render(); },
  renew() {
    const p = plan();
    const R = RES();
    R.goals.filter((g) => g.affordable > 0).forEach((g) => p.actions.push({ id: `act-${g.id}-${month()}`, goal_id: g.id, month: month(), amount: g.affordable }));
    UI.renewConsent = false; UI.play = false; render();
    toast(`已沿用方案 v${p.version}，生成 ${monthLabel(month())}的投入计划。`);
  },

  /* —— 缺口杠杆、试算、方案草案 —— */
  lever(el) {
    const R = RES();
    const g = R.goals.find((x) => x.id === el.dataset.goal);
    const lv = levers(g, R, S.household, planParams(), S.today)[el.dataset.kind];
    UI.scen.A = scenFromParams(mergeParams(planParams(), lv.patch));
    UI.scen.tab = 'A';
    location.hash = '#/scenarios';
    toast(`已带入「试算 A」：${{ monthly: `「${esc(g.name)}」每月 ${Y(lv.newMonthly)}`, ext: `延期 ${lv.months} 个月`, reduce: `目标下调 ${lv.pct}%` }[el.dataset.kind] || ''}。改过的格子会高亮。`);
  },
  'scen-tab'(el) { UI.scen.tab = el.dataset.t; render(); },
  'scen-add'() { const k = ['A', 'B', 'C'].find((x) => !UI.scen[x]); UI.scen[k] = scenFromParams(planParams()); UI.scen.tab = k; render(); },
  'scen-copy'() { const k = ['A', 'B', 'C'].find((x) => !UI.scen[x]); UI.scen[k] = clone(UI.scen[UI.scen.tab]); UI.scen.tab = k; render(); },
  'scen-reset'() { UI.scen[UI.scen.tab] = scenFromParams(planParams()); render(); },
  ext(el) { const sc = UI.scen[UI.scen.tab]; sc.extend = Math.max(0, Math.min(120, (sc.extend || 0) + +el.dataset.d)); render(); },
  'scen-draft'() {
    const { params, E } = scenParse(UI.scen[UI.scen.tab]);
    if (Object.keys(E).length) return;
    S.pending = { id: 'draft', rev: S.rev, params, created_at: `${S.today} 19:05` };
    UI.draftConsent = false;
    render({ top: true });
    toast('已生成方案草案。先看和当前方案的差别，再决定确认。');
  },
  'draft-consent'(el) { UI.draftConsent = el.checked; render(); },
  'plan-retest'() { S.pending.rev = S.rev; S.pending.created_at = `${S.today} 19:12`; UI.draftConsent = false; render(); toast('已基于最新账目重新测算，试算条件没变。'); },
  'plan-discard'() { S.pending = null; UI.draftConsent = false; render(); toast('草案已放弃。当前方案没有任何变化。'); },
  'plan-confirm'() {
    const p = plan();
    const res = calc(S.household, S.ledger, S.pending.params, S.today);
    const v = S.plans.length + 1;
    p.status = 'superseded';
    S.plans.push({
      id: 'v' + v, version: v, status: 'active', rev: S.rev, confirmed_at: `${S.today.slice(5)} 19:20`, params: S.pending.params,
      summary: res.goals.map((g) => `${g.name} ${Y(g.affordable, { dec: false })}`).join(' / ') + ` · 缺口 ${Y(res.goals.reduce((s, g) => s + g.shortfall, 0), { dec: false })}`,
      actions: res.goals.filter((g) => g.affordable > 0).map((g) => ({ id: `act-${g.id}-${month()}-v${v}`, goal_id: g.id, month: month(), amount: g.affordable })),
    });
    S.pending = null; UI.draftConsent = false;
    UI.scen = { tab: 'A', A: blankScen(), B: null, C: null };
    render({ top: true });
    toast(`方案 v${v} 已启用，本月投入计划已更新；v${v - 1} 保留为历史版本。已记录的投入不会丢。`);
  },

  /* —— 建档 —— */
  'hh-save'() {
    const first = saveHousehold();
    render();
    toast(first ? '建档已保存。下一步：去试算。' : `建档已保存（修订 ${S.rev}）。${plan() ? `方案 v${plan().version} 保留；建议重新测算一次。` : ''}`);
  },
  'hh-discard'() { UI.hh = clone(S.household); UI.raw = {}; UI.rawErr = {}; render(); toast('已放弃未保存的修改。'); },
  'hh-example'() {
    UI.lastFocus = document.activeElement;
    if (isDirty() && hhChanges() > 0 && S.rev > 0) { UI.overlay = { type: 'dialog', name: 'example' }; UI.fresh = true; renderOverlay(); return; }
    ACT['example-go']();
  },
  'example-go'() {
    UI.hh = baseHousehold(); UI.raw = {}; UI.rawErr = {};
    UI.overlay = null; UI.lastFocus = null;
    if (UI.route !== 'household') location.hash = '#/household'; else render({ top: true });
    renderOverlay();
    toast('已填入虚构示例。检查一下，再点「保存建档」。');
  },
  'hh-add'(el) { UI.hh[el.dataset.kind].push(newHouseholdRow(el.dataset.kind)); render(); },
  'hh-del'(el) {
    const k = el.dataset.kind;
    const [row] = UI.hh[k].splice(+el.dataset.i, 1);
    if (k === 'assets') UI.hh.allocations = UI.hh.allocations.filter((a) => a.asset_id !== row.id);
    if (k === 'goals') { UI.hh.allocations = UI.hh.allocations.filter((a) => a.goal_id !== row.id); UI.hh.commitments.forEach((c) => { if (c.goal_id === row.id) c.goal_id = ''; }); }
    render();
  },
  'goal-move'(el) {
    const i = +el.dataset.i;
    const j = i + +el.dataset.d;
    const g = UI.hh.goals;
    if (j < 0 || j >= g.length) return;
    [g[i], g[j]] = [g[j], g[i]];
    render();
  },
  rm(el) { UI.hh.reserve_months = Math.max(0, Math.min(60, UI.hh.reserve_months + +el.dataset.d)); render(); },
  'leave-discard'(el) { UI.hh = clone(S.household); UI.raw = {}; UI.rawErr = {}; UI.overlay = null; renderOverlay(); location.hash = el.dataset.to; },
  'leave-save'(el) { saveHousehold(); UI.overlay = null; renderOverlay(); location.hash = el.dataset.to; toast('建档已保存。'); },
  'toast-close'(el) { UI.toasts = UI.toasts.filter((t) => t.id !== el.dataset.id); renderToasts(); },
  'toast-act'(el) { const t = UI.toasts.find((x) => x.id === el.dataset.id); UI.toasts = UI.toasts.filter((x) => x.id !== el.dataset.id); renderToasts(); if (t && ACT[t.act]) ACT[t.act]({ dataset: { id: t.data } }); },
};

/* ---------- 输入绑定 ---------- */
function updateDirtyUI() {
  const bar = $('#dirtybar');
  if (bar) { bar.classList.toggle('dirty', isDirty()); bar.innerHTML = dirtyBarInner(); }
  const sub = $$('.flow a')[0];
  if (sub) { const s = sub.querySelector('.s'); if (s) s.textContent = `${S.rev === 0 ? '还没有建档' : `已保存 · 修订 ${S.hhRev}`}${isDirty() ? ' · 有未保存修改' : ''}`; }
}
function onBind(el, committed) {
  const b = el.dataset.bind;
  const kind = el.dataset.kind;
  if (b.startsWith('hh.')) {
    const path = b.slice(3);
    if (kind === 'money') {
      UI.raw[path] = el.value;
      const t = el.value.trim();
      const c = t === '' ? null : parseCents(t);
      UI.rawErr[path] = t !== '' && c === null;
      if (!UI.rawErr[path]) setPath(UI.hh, path, c);
      el.classList.toggle('err', !!UI.rawErr[path]);
    } else if (kind === 'check') setPath(UI.hh, path, el.checked);
    else setPath(UI.hh, path, el.value);
    if (/\.kind$/.test(path) && el.value === 'property') { const base = path.replace(/kind$/, 'liq'); setPath(UI.hh, base, 'illiquid'); }
    el.classList.toggle('changed', getPath(UI.hh, path) !== getPath(S.household, path));
    updateDirtyUI();
    if (kind === 'select' || kind === 'check' || (committed && /assets\.\d+\.amount|essential|reserve/.test(path))) { render(); }
    else if (/assets\.\d+\.amount|essential/.test(path)) { const m = $('#alloc'); if (m) { const hint = m.querySelector('.hint'); const html = allocMatrix(); const tmp = document.createElement('div'); tmp.innerHTML = html; const old = m.querySelector('.ledger-wrap'); if (old && tmp.firstElementChild) old.replaceWith(tmp.firstElementChild); } }
    return;
  }
  if (b === 'alloc') {
    const asset_id = el.dataset.asset;
    const goal_id = el.dataset.goal;
    UI.raw[`alloc.${asset_id}.${goal_id}`] = el.value;
    const t = el.value.trim();
    const c = t === '' ? 0 : parseCents(t);
    el.classList.toggle('err', c === null);
    if (c !== null) {
      const arr = UI.hh.allocations;
      const i = arr.findIndex((x) => x.asset_id === asset_id && x.goal_id === goal_id);
      if (c === 0 && i >= 0) arr.splice(i, 1);
      else if (c > 0 && i >= 0) arr[i].amount = c;
      else if (c > 0) arr.push({ asset_id, goal_id, amount: c });
    }
    const reserve = UI.hh.essential == null ? 0 : UI.hh.essential * UI.hh.reserve_months;
    UI.hh.assets.filter((a) => (a.kind === 'cash' || a.kind === 'investment') && a.liq === 'available').forEach((a) => {
      const sum = UI.hh.allocations.filter((x) => x.asset_id === a.id).reduce((s, x) => s + x.amount, 0);
      const free = (a.amount || 0) - (a.kind === 'cash' ? reserve : 0) - sum;
      const cell = $(`#free-${a.id}`);
      if (cell) { cell.className = free < 0 ? 'over' : 'left'; cell.innerHTML = `<b>${Y(free)}</b><div class="small">${free < 0 ? '超额分配' : '未分配'}</div>`; }
    });
    updateDirtyUI();
    return;
  }
  if (b.startsWith('scen.')) {
    const path = b.slice(5);
    const sc = UI.scen[UI.scen.tab];
    if (path.startsWith('monthly.')) sc.monthly[path.slice(8)] = el.value; else sc[path] = el.value;
    const host = $('.scen-controls');
    const key = el.dataset.k;
    const sel = [el.selectionStart, el.selectionEnd];
    host.outerHTML = scenControls();
    const nel = $(`[data-k="${key}"]`);
    if (nel) { nel.focus({ preventScroll: true }); try { nel.setSelectionRange(sel[0], sel[1]); } catch (e) { /* 忽略 */ } }
    $('#scenResults').innerHTML = scenResults();
    return;
  }
  if (b.startsWith('dr.')) {
    const f = b.slice(3);
    const d = UI.drafts[UI.cur];
    d[f] = el.value;
    if (f === 'asset_id') { const t = moveTargets(d.asset_id); if (d.target_id === d.asset_id) d.target_id = (t[0] || {}).id || ''; }
    persist();
    if (el.tagName === 'SELECT' || el.type === 'date') { renderOverlay(); return; }
    const pv = $('#pv');
    if (pv) pv.outerHTML = previewHTML(d);
    if (UI.showErr) { renderOverlay(); }
    const as = $('#autosave');
    if (as) { as.innerHTML = '<i></i>草稿已自动保存'; }
    return;
  }
  if (b === 'recon') {
    UI.reconVal = el.value;
    const a = asset($('#recon-save') ? $('#recon-save').dataset.asset : 'cash');
    const row = RES().rows.find((r) => r.id === a.id);
    const typed = parseCents(el.value);
    const diff = typed == null ? null : typed - row.balance;
    $('#recon-impact').innerHTML = reconImpact(diff);
    $('#recon-save').disabled = diff == null || diff === 0;
  }
}
document.addEventListener('input', (ev) => { const el = ev.target; if (el.dataset && el.dataset.bind && !['SELECT'].includes(el.tagName) && el.type !== 'checkbox' && el.type !== 'date') onBind(el, false); });
document.addEventListener('change', (ev) => { const el = ev.target; if (el.dataset && el.dataset.bind) onBind(el, true); });
document.addEventListener('click', (ev) => {
  const nav = ev.target.closest('a[data-nav]');
  if (nav && UI.route === 'household' && isDirty() && nav.getAttribute('href') !== '#/household') {
    ev.preventDefault();
    UI.lastFocus = nav; UI.overlay = { type: 'dialog', name: 'leave', to: nav.getAttribute('href') }; UI.fresh = true; renderOverlay();
    return;
  }
  const t = ev.target.closest('[data-act]');
  if (!t) {
    if (UI.pop && !ev.target.closest('.pop')) { UI.pop = false; renderOverlay(); }
    return;
  }
  const fn = ACT[t.dataset.act];
  if (fn) fn(t, ev);
}, true);
document.addEventListener('keydown', (ev) => {
  if (ev.key === 'Escape') {
    if (UI.overlay) { ev.preventDefault(); closeOverlay(); } else if (UI.pop) { UI.pop = false; renderOverlay(); }
    return;
  }
  if (ev.key === 'Tab' && UI.overlay) {
    const box = $('.sheet') || $('.dialog');
    if (!box) return;
    const f = $$('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, a[href]', box).filter((e) => e.offsetParent !== null);
    if (!f.length) return;
    const first = f[0];
    const last = f[f.length - 1];
    if (ev.shiftKey && document.activeElement === first) { ev.preventDefault(); last.focus(); }
    else if (!ev.shiftKey && document.activeElement === last) { ev.preventDefault(); first.focus(); }
  }
});
window.addEventListener('hashchange', () => onRoute(false));
window.addEventListener('beforeunload', (ev) => { if (isDirty()) { ev.preventDefault(); ev.returnValue = ''; } });

/* ---------- 启动 ---------- */
restore();
if (!location.hash) history.replaceState(null, '', '#/overview');
onRoute(true);
