/* ============================================================
   原型主程序 · 第 4 段：记账抽屉、对话框、浮层
   ============================================================ */

/* ---------- 记账草稿 ---------- */
const KINDS = [['contribution', '投入目标'], ['income', '收入'], ['expense', '支出'], ['debt', '还款'], ['transfer', '转账']];
const cashAccts = () => S.household.assets.filter((a) => a.kind === 'cash' && a.liq === 'available');
const moveTargets = (excludeId) => S.household.assets.filter((a) => a.id !== excludeId && ['cash', 'investment'].includes(a.kind) && a.liq === 'available');

function newDraft(kind = 'contribution', patch = {}) {
  const cash = cashAccts()[0];
  const tgt = moveTargets(cash && cash.id)[0];
  return {
    key: uid(), kind, state: 'confirmed', date: S.today, asset_id: cash ? cash.id : '', target_id: tgt ? tgt.id : '',
    goal_id: S.household.goals[0] ? S.household.goals[0].id : '', debt_id: S.household.debts[0] ? S.household.debts[0].id : '',
    amount: '', principal: '', quality: 'verified', source: '手动录入', note: '', action_id: null, ...patch,
  };
}
const isBlank = (d) => !d.amount && !d.note && !d.action_id && d.state === 'confirmed' && d.date === S.today;
function draftFromAction(a) {
  const base = newDraft('contribution');
  const assigned = S.household.allocations.filter((x) => x.goal_id === a.goal_id).map((x) => x.asset_id);
  const cands = moveTargets(base.asset_id);
  const target = cands.find((c) => assigned.includes(c.id)) || cands.find((c) => c.kind === 'investment') || cands[0];
  return { ...base, goal_id: a.goal_id, action_id: a.id, amount: a.remaining > 0 ? centsInput(a.remaining) : '', target_id: target ? target.id : '' };
}
function openRecord(kind, actionId) {
  UI.lastFocus = document.activeElement;
  if (actionId) {
    const a = ACTS().find((x) => x.id === actionId);
    const d = a ? draftFromAction(a) : newDraft('contribution');
    // 沿用原版的「草稿保护」：只替换空白草稿，已经写过的内容不覆盖，而是另加一条
    if (UI.drafts.length === 1 && isBlank(UI.drafts[0])) UI.drafts = [d]; else UI.drafts.push(d);
    UI.cur = UI.drafts.length - 1;
  } else if (!UI.drafts.length) {
    UI.drafts = [newDraft(kind || 'expense')]; UI.cur = 0;
  } else if (isBlank(UI.drafts[UI.cur])) {
    UI.drafts[UI.cur] = { ...newDraft(kind || 'expense'), key: UI.drafts[UI.cur].key };
  }
  UI.showErr = false;
  UI.overlay = { type: 'record' };
  UI.fresh = true;
  persist();
  renderOverlay();
}

/* 校验：文案是人话，并告诉你下一步怎么做（I3、P2） */
function balanceOf(id) { const r = RES().rows.find((x) => x.id === id); return r ? r.balance : null; }
function validate(d) {
  const E = [];
  const src = asset(d.asset_id);
  const amt = parseCents(d.amount);
  const actual = d.state === 'confirmed';
  if (!src) E.push({ f: 'asset', m: '请选择账户' });
  if (!String(d.amount).trim()) E.push({ f: 'amount', m: '请填写金额（只填正数）' });
  else if (amt === null) E.push({ f: 'amount', m: '金额格式不对：只填正数，最多两位小数' });
  else if (actual && amt === 0) E.push({ f: 'amount', m: '已发生的记录金额要大于零' });
  if (!d.date) E.push({ f: 'date', m: '请选择日期' });
  else if (actual && d.date > S.today) E.push({ f: 'date', m: '这是未来的日期。预计会发生的收支请选「预计」，它不会改动余额。' });
  else if (actual && src && d.date <= src.observed_on) E.push({ f: 'date', m: `早于「${src.name}」的日终基准（${md(src.observed_on)}）。已发生的记录要晚于这一天；如果是余额本身不对，请先去校准余额。`, link: src.id });
  if (actual && src && amt != null && d.kind !== 'income') {
    const b = balanceOf(src.id);
    if (b != null && b < amt) E.push({ f: 'amount', m: `会让「${src.name}」余额变成负数（${Y(b - amt)}）。` });
  }
  if (d.kind === 'contribution' || d.kind === 'transfer') {
    const t = asset(d.target_id);
    if (!t || t.id === d.asset_id) E.push({ f: 'target', m: '请选择不同的转入账户' });
    if (d.kind === 'contribution' && !d.goal_id) E.push({ f: 'goal', m: '请选择这笔钱投给哪个目标' });
    if (t && actual && d.date <= t.observed_on) E.push({ f: 'date', m: `早于「${t.name}」的日终基准（${md(t.observed_on)}）。`, link: t.id });
  }
  if (d.kind === 'debt') {
    const pr = parseCents(d.principal);
    if (!d.debt_id) E.push({ f: 'debt', m: '请选择所还的负债' });
    if (pr === null) E.push({ f: 'principal', m: '请填写其中的本金；纯利息填 0' });
    else if (amt != null && pr > amt) E.push({ f: 'principal', m: '本金不能超过本次还款总额' });
  }
  if (d.action_id && amt != null) {
    const a = ACTS().find((x) => x.id === d.action_id);
    if (a) {
      const others = UI.drafts.filter((x) => x.key !== d.key && x.action_id === d.action_id && x.state === 'confirmed').reduce((s, x) => s + (parseCents(x.amount) || 0), 0);
      if (others + amt > a.remaining && a.remaining > 0) E.push({ f: 'amount', m: `和同批里其它关联「${goal(a.goal_id).name}」的草稿合计 ${Y(others + amt)}，超过还需投入的 ${Y(a.remaining)}。如果确实多投了，可以保存，进度会显示「超出」。`, soft: true });
    }
  }
  return E;
}
const hardErrors = (d) => validate(d).filter((e) => !e.soft);

function previewHTML(d) {
  const R = RES();
  const amt = parseCents(d.amount);
  const forecast = d.state === 'forecast';
  const bal = (id) => { const r = R.rows.find((x) => x.id === id); return r ? r.balance : null; };
  const src = asset(d.asset_id);
  const tgt = d.kind === 'contribution' || d.kind === 'transfer' ? asset(d.target_id) : null;
  const rows = [];
  const line = (a, delta) => {
    if (!a) return;
    const b = bal(a.id);
    const after = amt == null || b == null ? null : b + delta;
    rows.push(`<div class="pv-row"><span>${esc(a.name)}</span><span class="range"><span class="from">${Y(b)}</span> <span class="arrow">→</span> <b>${after == null ? '待填写金额' : Y(after)}</b></span><span class="d ${delta < 0 ? 'minus' : 'plus'}">${amt == null ? '' : Y(delta, { sign: true })}</span></div>`);
  };
  const a = amt == null ? 0 : amt;
  if (d.kind === 'income') line(src, a);
  else if (d.kind === 'expense' || d.kind === 'debt') line(src, -a);
  else { line(src, -a); line(tgt, a); }
  let prog = '';
  if (d.kind === 'contribution' && d.goal_id) {
    const a = ACTS().find((x) => x.id === d.action_id);
    const g = goal(d.goal_id);
    if (a && amt != null && !forecast) prog = `<div class="pv-row"><span>本月进度 · ${esc(g.name)}</span><span class="range"><span class="from">${Y(a.actual)}</span> <span class="arrow">→</span> <b>${Y(a.actual + amt)} / ${Y(a.amount)}</b></span><span class="d plus">${a.actual + amt >= a.amount ? '达成' : ''}</span></div>`;
    else if (!a) prog = `<p class="hint" style="margin-top:6px">没有关联本月计划：只会记账，不计入本月进度。</p>`;
  }
  return `<div class="preview ${forecast ? 'forecast' : ''}" id="pv" aria-live="polite"><h4>${forecast ? '预计变化 · 不入账' : '保存后的余额变化'}</h4>${rows.join('')}${prog}</div>`;
}

function sheetRecord() {
  const d = UI.drafts[UI.cur];
  const all = UI.drafts.map(validate);
  const errs = UI.showErr ? all[UI.cur] : [];
  const E = (f) => errs.filter((e) => e.f === f);
  const errHtml = (f) => E(f).map((e) => `<div class="errmsg" role="alert">${ic('alert', 15)}<span>${esc(e.m)}${e.link ? ` <button type="button" class="linkbtn" data-act="recon-open" data-asset="${e.link}">去校准余额</button>` : ''}</span></div>`).join('');
  const bad = (f) => (E(f).some((e) => !e.soft) ? 'err' : '');
  const yday = shiftDay(S.today, -1);
  const src = asset(d.asset_id);
  const anchor = src ? src.observed_on : '';
  const dates = [[S.today, `今天 ${md(S.today)}`], [yday, `昨天 ${md(yday)}`]];
  const customDate = !dates.some(([v]) => v === d.date);
  const selAcct = (id, list, k, label) => field(label, `<select class="in ${bad(k)}" id="dr-${k}" data-bind="dr.${id}" data-k="dr-${k}">${list.map((a) => `<option value="${a.id}" ${d[id] === a.id ? 'selected' : ''}>${esc(a.name)} · ${Y(balanceOf(a.id), { dec: false })}</option>`).join('')}</select>`);
  const amountLabel = { contribution: '投入金额', income: '收到多少', expense: '花了多少', debt: '还款总额', transfer: '转出金额' }[d.kind];
  const goalOpt = S.household.goals.map((g) => `<option value="${g.id}" ${d.goal_id === g.id ? 'selected' : ''}>${esc(g.name)}</option>`).join('');
  const fieldsByKind = {
    contribution: `${field('投给哪个目标', `<select class="in ${bad('goal')}" id="dr-goal" data-bind="dr.goal_id" data-k="dr-goal">${goalOpt}</select>`, { hint: d.action_id ? '已关联本月投入计划，保存后自动计入进度。' : '没有关联本月计划时，只记账、不计入进度。' })}${errHtml('goal')}
      <div class="h-grid" style="grid-template-columns:1fr 1fr">${selAcct('asset_id', cashAccts(), 'asset', '从哪个账户转出')}${selAcct('target_id', moveTargets(d.asset_id), 'target', '转入哪个账户')}</div>${errHtml('asset')}${errHtml('target')}`,
    income: `${selAcct('asset_id', cashAccts(), 'asset', '收款账户')}${errHtml('asset')}`,
    expense: `${selAcct('asset_id', cashAccts(), 'asset', '付款账户')}${errHtml('asset')}`,
    debt: `<div class="h-grid" style="grid-template-columns:1fr 1fr">${selAcct('asset_id', cashAccts(), 'asset', '付款账户')}${field('所还负债', `<select class="in ${bad('debt')}" id="dr-debt" data-bind="dr.debt_id" data-k="dr-debt">${S.household.debts.map((x) => `<option value="${x.id}" ${d.debt_id === x.id ? 'selected' : ''}>${esc(x.name)}</option>`).join('')}</select>`)}</div>${errHtml('asset')}${errHtml('debt')}`,
    transfer: `<div class="h-grid" style="grid-template-columns:1fr 1fr">${selAcct('asset_id', cashAccts(), 'asset', '从哪个账户转出')}${selAcct('target_id', moveTargets(d.asset_id), 'target', '转入哪个账户')}</div>${errHtml('asset')}${errHtml('target')}<p class="hint">内部转账不算收入，也不算支出。</p>`,
  }[d.kind];
  const bar = UI.showErr && all.some((e) => e.some((x) => !x.soft))
    ? `<div class="summary-err" role="alert">${ic('alert', 18)}<span>有 ${all.filter((e) => e.some((x) => !x.soft)).length} 条记录需要修改，已为你定位到第 ${all.findIndex((e) => e.some((x) => !x.soft)) + 1} 条。</span></div>` : '';
  const title = d.action_id ? '记录本次投入' : '记一笔';
  return `<div class="scrim" data-act="close-overlay"></div>
  <aside class="sheet" role="dialog" aria-modal="true" aria-labelledby="sh-title" ${UI.fresh ? 'data-fresh' : ''}>
    <div class="sheet-head"><div style="flex:1"><h2 id="sh-title">${title}</h2><p class="muted small" style="margin-top:6px">只记真实发生的事。预计会发生的请选「预计」——它们不会改动余额。</p></div><button class="iconbtn sq" type="button" data-act="close-overlay" aria-label="关闭">${ic('x', 18)}</button></div>
    <div class="sheet-body" id="sheetBody">
      <div class="chips-row" ${fix('I2')} role="tablist" aria-label="本批草稿">
        ${UI.drafts.map((x, i) => `<span class="draft-chip ${all[i].some((e) => !e.soft) && UI.showErr ? 'bad' : ''}" aria-current="${i === UI.cur}"><button type="button" role="tab" data-act="pick-draft" data-i="${i}">第 ${i + 1} 条${all[i].some((e) => !e.soft) && UI.showErr ? '<span class="bd"></span>' : ''}</button>${UI.drafts.length > 1 ? `<button class="x" type="button" data-act="del-draft" data-i="${i}" aria-label="移除第 ${i + 1} 条草稿">×</button>` : ''}</span>`).join('')}
        <button class="btn sm ghost" type="button" data-act="add-draft" ${UI.drafts.length >= 50 ? 'disabled' : ''}>${ic('plus', 15, 2)}再加一条</button>
        <span class="muted small">${UI.drafts.length} / 50</span>
        ${UI.drafts.length === 1 ? '<button class="linkbtn small" type="button" data-act="clear-draft" style="margin-left:auto">清空这条</button>' : ''}
      </div>
      ${bar}
      <div class="field"><span class="lab" id="lab-kind">这是一笔…</span><div class="seg-ctl" role="radiogroup" aria-labelledby="lab-kind">${KINDS.map(([k, n]) => `<button type="button" role="radio" aria-checked="${d.kind === k}" data-act="kind" data-k2="${k}">${n}</button>`).join('')}</div></div>
      ${fieldsByKind}
      ${field(amountLabel, `<div class="pre l"><span>¥</span><input class="in ${bad('amount')}" id="dr-amount" inputmode="decimal" autocomplete="off" placeholder="只填正数" value="${esc(d.amount)}" data-bind="dr.amount" data-k="dr-amount"></div>`, { hint: d.action_id ? `已带入还需投入的金额；按真实转账的金额改就行。` : '' })}${errHtml('amount')}
      ${d.kind === 'debt' ? field('其中本金', `<div class="pre l"><span>¥</span><input class="in ${bad('principal')}" id="dr-principal" inputmode="decimal" autocomplete="off" placeholder="纯利息填 0" value="${esc(d.principal)}" data-bind="dr.principal" data-k="dr-principal"></div>`, { hint: '本金减少负债，利息算成本。' }) + errHtml('principal') : ''}
      <div class="field" ${fix('P2')}><span class="lab">日期</span>
        <div class="date-chips">${dates.map(([v, n]) => `<button type="button" data-act="date-pick" data-d="${v}" aria-pressed="${d.date === v}" ${d.state === 'confirmed' && v <= anchor ? 'disabled' : ''}>${n}</button>`).join('')}
          <input type="date" class="in ${bad('date')}" style="width:auto;min-width:150px" value="${esc(d.date)}" data-bind="dr.date" data-k="dr-date" aria-label="其他日期" ${customDate ? 'aria-pressed="true"' : ''}></div>
        ${errHtml('date')}
        ${anchor ? `<div class="hint">「${esc(src.name)}」的日终基准是 ${md(anchor)}：已发生的记录要晚于这一天。</div>` : ''}</div>
      <label class="switch-row forecast-switch"><span><b>这笔还没发生</b><span class="muted">（预计，不入账）</span></span><input type="checkbox" data-act="fstate" ${d.state === 'forecast' ? 'checked' : ''}></label>
      <details class="more"><summary>${ic('chev', 16)}更多选项${d.quality !== 'verified' ? ` · ${d.quality === 'estimated' ? '估算金额' : '金额未知'}` : ''}</summary>
        <div class="stack" style="margin-top:12px">
          ${field('数值质量', `<select class="in" data-bind="dr.quality" data-k="dr-quality"><option value="verified" ${d.quality === 'verified' ? 'selected' : ''}>已核实</option><option value="estimated" ${d.quality === 'estimated' ? 'selected' : ''}>估算</option></select>`)}
          ${field('记录来源', `<input class="in" value="${esc(d.source)}" maxlength="200" data-bind="dr.source" data-k="dr-source">`)}
          ${field('备注', `<input class="in" value="${esc(d.note)}" maxlength="1000" data-bind="dr.note" data-k="dr-note">`)}
        </div>
      </details>
    </div>
    <div class="sheet-pv">${previewHTML(d)}</div>
    <div class="sheet-foot">
      <span class="autosave" id="autosave" title="草稿保存在这台电脑的浏览器里">${'<i></i>'}草稿已自动保存</span><span class="grow"></span>
      <button class="btn" type="button" data-act="close-overlay">先放着</button>
      <button class="btn primary" type="button" data-act="save-drafts" id="saveBtn">保存 ${UI.drafts.length} 条记录</button>
    </div>
  </aside>`;
}

/* ---------- 助手、改动清单 ---------- */
function sheetAssist() {
  return `<div class="scrim" data-act="close-overlay"></div>
  <aside class="sheet" role="dialog" aria-modal="true" aria-labelledby="sh-title" ${UI.fresh ? 'data-fresh' : ''}>
    <div class="sheet-head"><div style="flex:1"><h2 id="sh-title">规划助手</h2><p class="muted small" style="margin-top:6px">理解目标 · 调用测算 · 提议调整。只会给出方案草案，从不直接改账目。</p></div><button class="iconbtn sq" type="button" data-act="close-overlay" aria-label="关闭">${ic('x', 18)}</button></div>
    <div class="sheet-body">
      <div class="assist-state"><b>${ic('info', 16)} 还没有启用</b><p class="muted">手动建档、试算、记账和复盘都不需要它。想启用时：</p>
        <ol><li>把 <code>backend/.env.example</code> 复制为 <code>backend/.env</code></li><li>填写 <code>OPENROUTER_API_KEY</code> 和支持工具调用的 <code>OPENROUTER_MODEL</code></li><li>重启服务</li></ol></div>
      <div class="field"><label class="lab" for="ask">问点什么</label><textarea class="in" id="ask" disabled placeholder="启用之后，在这里说说你的目标或疑问…"></textarea></div>
      <details open><summary style="cursor:pointer;font-weight:600;color:var(--olive)">启用后，发送前你会看到</summary>
        <ul class="stack small muted" style="margin-top:10px;list-style:disc;padding-left:20px" ${fix('P6')}>
          <li><b>会发送什么</b>：你的建档和当前测算摘要，经 OpenRouter 发给你选的模型服务。首次发送前要你点一次同意。</li>
          <li><b>额度</b>：每天最多 10 个任务（北京时间每天 08:00 重置），每个任务最多 4 次请求；费用以供应商为准。</li>
          <li><b>生成期间</b>请先别改建档或记账——账目一变，这次结果就作废（而这次调用可能已经产生费用）。</li>
        </ul></details>
    </div>
    <div class="sheet-foot"><span class="grow"></span><button class="btn" type="button" data-act="close-overlay">关闭</button></div>
  </aside>`;
}
function sheetLegend() {
  const sevName = { S0: '阻断', S1: '高', S2: '中', S3: '低' };
  return `<div class="scrim" data-act="close-overlay"></div>
  <aside class="sheet" style="width:min(940px,100vw)" role="dialog" aria-modal="true" aria-labelledby="sh-title" ${UI.fresh ? 'data-fresh' : ''}>
    <div class="sheet-head"><div style="flex:1"><h2 id="sh-title">改动清单</h2><p class="muted small" style="margin-top:6px">${FIXES.length} 项。每项对应审核报告里的同名编号；「后端」列说明这条改动是否需要后端配合。</p></div><button class="iconbtn sq" type="button" data-act="close-overlay" aria-label="关闭">${ic('x', 18)}</button></div>
    <div class="sheet-body"><div class="ledger-wrap"><table class="legend-table"><thead><tr><th>编号</th><th>级别</th><th>原来的问题</th><th>本稿怎么处理</th><th>后端</th></tr></thead><tbody>
      ${FIXES.map((f) => `<tr><td class="id">${f.id}</td><td>${chip(sevName[f.sev], f.sev === 'S0' ? 'down' : f.sev === 'S1' ? 'warn' : 'plain')}</td><td><b>${esc(f.title)}</b><div class="muted small" style="margin-top:4px">${esc(f.was)}</div></td><td>${esc(f.now)}</td><td class="be">${f.be === '需要' ? chip('需要', 'warn') : f.be === '小改' ? chip('小改', 'olive') : chip('否', 'plain')}</td></tr>`).join('')}
    </tbody></table></div></div>
  </aside>`;
}

/* ---------- 对话框 ---------- */
function dialogHTML(o) {
  const wrap = (title, body, foot) => `<div class="scrim" data-act="dlg-close"></div><div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-t" ${UI.fresh ? 'data-fresh' : ''}><h2 id="dlg-t">${title}</h2>${body}<div class="foot">${foot}</div></div>`;
  if (o.name === 'void') {
    const e = S.ledger.find((x) => x.id === o.id);
    const before = RES();
    const after = calc(S.household, S.ledger.filter((x) => x.id !== e.id), planParams(), S.today);
    const lines = [];
    ['asset_id', 'target_asset_id'].forEach((k) => {
      const id = e[k];
      if (!id) return;
      const b = before.rows.find((r) => r.id === id);
      const a = after.rows.find((r) => r.id === id);
      lines.push(`<div>${esc(b.name)}：${Y(b.balance)} → <b>${Y(a.balance)}</b></div>`);
    });
    const act = ACTS().find((x) => x.goal_id === e.goal_id);
    if (act && e.goal_id) {
      const na = actionsFor(plan(), S.ledger.filter((x) => x.id !== e.id), month(), S.today).find((x) => x.goal_id === e.goal_id);
      lines.push(`<div>「${esc(goal(e.goal_id).name)}」本月进度：${Y(act.actual)} → <b>${Y(na.actual)}</b>（计划 ${Y(act.amount)}）</div>`);
    }
    return wrap('作废这条记录？', `<p>${e.date} · ${e.goal_id ? '投入目标 · ' + esc(goal(e.goal_id).name) : '记录'} · ${Y(e.amount)}</p>
      <div class="impact"><b>作废之后</b>${lines.join('')}<div class="muted small">记录会留在历史里（标为已作废），不能恢复；需要的话请重新录入。</div></div>`,
      `<button class="btn" type="button" data-act="dlg-close">取消</button><button class="btn" type="button" data-act="void-redo" data-id="${e.id}">作废并重新录入</button><button class="btn danger" type="button" data-act="void-confirm" data-id="${e.id}">确认作废</button>`);
  }
  if (o.name === 'leave') {
    return wrap('有未保存的修改', `<p>建档里有 <b>${hhChanges()}</b> 处修改还没保存。离开之前想怎么处理？</p>`,
      `<button class="btn" type="button" data-act="dlg-close">继续编辑</button><button class="btn" type="button" data-act="leave-discard" data-to="${esc(o.to)}">放弃修改并离开</button><button class="btn primary" type="button" data-act="leave-save" data-to="${esc(o.to)}">保存并离开</button>`);
  }
  if (o.name === 'example') {
    return wrap('用示例覆盖当前内容？', `<p>这会把表单替换成虚构家庭的数据，你已经填写但还没保存的内容会丢失。已保存的建档不受影响，直到你再点「保存建档」。</p>`,
      `<button class="btn" type="button" data-act="dlg-close">取消</button><button class="btn danger" type="button" data-act="example-go">覆盖</button>`);
  }
  if (o.name === 'recon') {
    const a = asset(o.asset);
    const row = RES().rows.find((r) => r.id === a.id);
    const typed = UI.reconVal != null ? parseCents(UI.reconVal) : null;
    const diff = typed == null ? null : typed - row.balance;
    return wrap('对账：和银行里的数字对一下', `<p class="muted">系统算出「${esc(a.name)}」截至 ${md(S.today)} 是 <b class="num">${Y(row.balance)}</b>。打开银行 App，把真实余额填在下面。</p>
      ${field('银行里的实际余额', `<div class="pre l"><span>¥</span><input class="in" id="recon-in" inputmode="decimal" autocomplete="off" placeholder="例如 ${centsInput(row.balance)}" value="${esc(UI.reconVal ?? '')}" data-bind="recon" data-k="recon-in"></div>`)}
      <div class="impact" id="recon-impact">${reconImpact(diff)}</div>`,
      `<button class="btn" type="button" data-act="dlg-close">取消</button><button class="btn primary" type="button" data-act="recon-save" data-asset="${a.id}" id="recon-save" ${diff == null || diff === 0 ? 'disabled' : ''}>生成差额调整</button>`);
  }
  return '';
}
function reconImpact(diff) {
  if (diff == null) return '<span class="muted">填写后会显示差额。</span>';
  if (diff === 0) return '<b>一致</b>，不需要调整。';
  return `差额 <b class="num">${Y(diff, { sign: true })}</b>。保存后生成一条<b>「对账调整」</b>记录（可作废），建档的基准日期不变，当月已记的投入进度也不受影响。`;
}

/* ---------- 评审视图（浮层）与提示条 ---------- */
function popHTML() {
  return `<div class="pop" role="dialog" aria-label="评审视图">
    <h3>评审视图</h3>
    <div role="radiogroup" aria-label="演示状态">${Object.entries(DEMOS).map(([k, v]) => `<label class="radio"><input type="radio" name="demo" value="${k}" data-act="demo" ${S.demo === k ? 'checked' : ''}><span>${v.label}</span></label>`).join('')}</div>
    <label class="switch-row"><span>显示评审标注（对应报告编号）</span><input type="checkbox" data-act="notes" ${UI.notes ? 'checked' : ''}></label>
    <label class="switch-row"><span>暗色主题</span><input type="checkbox" data-act="theme" ${UI.theme === 'dark' ? 'checked' : ''}></label>
    <div class="row"><button class="btn sm" type="button" data-act="legend">${ic('list', 15)}改动清单</button><button class="btn sm ghost" type="button" data-act="pop">关闭</button></div>
  </div>`;
}
function renderToasts() {
  $('#toasts').innerHTML = UI.toasts.map((t) => `<div class="toast" role="status"><p>${t.text}</p>${t.label ? `<button type="button" data-act="toast-act" data-id="${t.id}">${t.label}</button>` : ''}<button type="button" data-act="toast-close" data-id="${t.id}" aria-label="关闭提示">${ic('x', 16, 2)}</button></div>`).join('');
}
function toast(text, opt = {}) {
  const t = { id: uid(), text, ...opt };
  UI.toasts.push(t);
  if (UI.toasts.length > 2) UI.toasts.shift();
  renderToasts();
  if (!document.documentElement.classList.contains('still')) setTimeout(() => { UI.toasts = UI.toasts.filter((x) => x.id !== t.id); renderToasts(); }, opt.ms || 7000);
}

function renderOverlay() {
  const o = UI.overlay;
  let html = '';
  if (o) {
    if (o.type === 'record') html = sheetRecord();
    else if (o.type === 'assist') html = sheetAssist();
    else if (o.type === 'legend') html = sheetLegend();
    else if (o.type === 'dialog') html = dialogHTML(o);
  }
  if (UI.pop) html += popHTML();
  const body = $('#sheetBody');
  const bodyTop = body ? body.scrollTop : 0;
  const ae = document.activeElement;
  const key = ae && ae.dataset && ae.dataset.k;
  const sel = ae && typeof ae.selectionStart === 'number' ? [ae.selectionStart, ae.selectionEnd] : null;
  $('#overlay').innerHTML = html;
  document.body.style.overflow = o ? 'hidden' : '';
  const nb = $('#sheetBody');
  if (nb && !UI.fresh) nb.scrollTop = bodyTop;
  if (UI.fresh && o) {
    const first = $('.sheet .sheet-body input, .sheet .sheet-body select, .dialog button.primary, .dialog button.danger, .dialog button, .sheet button');
    const target = o.type === 'record' ? $('#dr-amount') || first : first;
    if (target) target.focus({ preventScroll: true });
  } else if (key) {
    const el = $(`#overlay [data-k="${key}"]`);
    if (el) { el.focus({ preventScroll: true }); if (sel) { try { el.setSelectionRange(sel[0], sel[1]); } catch (e) { /* 非文本输入 */ } } }
  }
  UI.fresh = false;
}
function closeOverlay() {
  UI.overlay = null; UI.pop = false; UI.reconVal = null;
  renderOverlay();
  if (UI.lastFocus && document.contains(UI.lastFocus)) UI.lastFocus.focus({ preventScroll: true });
}
