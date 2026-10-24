/* ============================================================
   原型主程序 · 第 2 段：外壳与四个页面
   ============================================================ */

/* ---------- 「下一步」条：每页顶部只给一件事 ---------- */
function nextStep() {
  const info = monthInfo();
  const p = plan();
  const acts = ACTS();
  if (S.rev === 0) {
    return { cls: '', fix: '', text: '还没有建档。先花几分钟说清账户、收支和目标。', small: '想先看看效果？示例是一户虚构家庭，不会混进你的真实记录。', acts: [btn('开始建档', { cls: 'primary', href: '#/household' }), btn('用示例体验', { act: 'hh-example' })] };
  }
  const R0 = RES();
  if (R0.incomplete) {
    return { cls: 'warn', fix: '', text: `建档还差一点：${R0.missing.join('；')}。`, small: '资料不完整时不会替你当作 0 来算——补上之后才能试算。', acts: [btn('去补全建档', { cls: 'primary', href: '#/household' })] };
  }
  if (!p && !S.pending) {
    return { cls: '', fix: '', text: '建档已保存。下一步：试算一下，看目标够不够。', small: '试算不会改动你的账目；满意了再生成方案草案、确认。', acts: [btn('去试算', { cls: 'primary', href: '#/scenarios' })] };
  }
  if (S.pending) {
    if (S.pending.rev !== S.rev) {
      return { cls: 'warn', fix: 'P5', text: '方案草案已过期：生成草案之后，账目又有了新记录。', small: '过期的草案不能确认。基于最新账目重新测算一次就好，试算条件会保留。', acts: [btn('基于最新账目重新测算', { cls: 'primary', act: 'plan-retest' }), btn('查看草案', { href: '#/scenarios' })] };
    }
    return { cls: '', fix: '', text: '有一份方案草案等你确认。', small: '确认前先看它和当前方案的差别。', acts: [btn('查看并确认', { cls: 'primary', href: '#/scenarios' })] };
  }
  if (p && !acts.length) {
    return { cls: 'warn', fix: 'P1', text: `进入 ${info.label}：方案 v${p.version} 仍在使用，但这个月还没有投入计划。`, small: '沿用方案需要你确认一次，不会自动改动任何金额。', acts: [btn('沿用方案，生成本月计划', { cls: 'primary', href: '#/execution' }), btn('先去试算调整', { href: '#/scenarios' })] };
  }
  const open = acts.filter((a) => a.status !== 'completed');
  if (open.length) {
    const a = open[0];
    const g = goal(a.goal_id);
    return { cls: '', fix: 'I3', text: `本月还差 <b class="num">${Y(open.reduce((s, x) => s + x.remaining, 0))}</b> 没有投入${open.length > 1 ? '' : `「${esc(g.name)}」`}。`, small: '投入后在这里记一笔，进度会自动累计；FinUnity 不会替你认定“已经交易”。', acts: [btn('记录本次投入', { cls: 'primary', act: 'open-record', data: `data-kind="contribution" data-action="${a.id}"` })] };
  }
  if (info.day >= info.zoneStart && !S.reviewRev) {
    return { cls: '', fix: 'P1', text: `${info.label}快结束了：生成本月复盘，对照计划与实际。`, small: '复盘只汇总你确认过的记录，不改动建档和流水。', acts: [btn('生成本月复盘', { cls: 'primary', act: 'review-make', data: `data-m="${info.m}"` })] };
  }
  return { cls: 'ok', fix: '', text: '本月的计划投入都已完成。', small: `月底前（${info.zoneStart}–${info.n} 日）生成复盘即可。`, acts: [btn('去看复盘', { href: '#/execution' })] };
}
function nextStrip() {
  const n = nextStep();
  return `<section class="next ${n.cls} rv" ${n.fix ? fix(n.fix) : ''} style="--i:0" aria-label="下一步">
    <span class="k">下一步</span>
    <p>${n.text}<small>${n.small}</small></p>
    <div class="acts">${n.acts.join('')}</div>
  </section>`;
}

/* ---------- 外壳 ---------- */
function shell(content) {
  const p = plan();
  const acts = ACTS();
  const done = acts.filter((a) => a.status === 'completed').length;
  const hhSub = S.rev === 0 ? '还没有建档' : `已保存 · 修订 ${S.hhRev}`;
  const scSub = S.pending ? (S.pending.rev !== S.rev ? '草案已过期' : '有 1 份草案待确认') : p ? `方案 v${p.version} 使用中` : '先试算，再生成方案';
  const exSub = !p ? '确认方案后开始' : acts.length ? `本月 ${done}/${acts.length} 项已投入` : '本月计划待生成';
  const cur = UI.route;
  const link = (r, no, t, s, isDone) => `<a href="#/${r}" ${cur === r ? 'aria-current="page"' : ''} class="${isDone ? 'done' : ''}" data-nav><span class="dot"></span><span><span class="t">${t}</span><span class="s">${s}</span></span></a>`;
  const dirty = isDirty();
  return `
  <div class="shell">
    <aside class="rail" aria-label="导航" ${fix('I11')}>
      <a class="brand" href="#/overview" data-nav><span class="brand-mark">F</span><span><b>FinUnity</b><small>个人财务规划 · 本机</small></span></a>
      <nav aria-label="主要导航" style="display:grid;gap:14px">
        <a class="rail-home" href="#/overview" data-nav ${cur === 'overview' ? 'aria-current="page"' : ''}>${ic('home')}我的规划</a>
        <div>
          <p class="rail-label">每月一圈</p>
          <div class="flow">
            ${link('household', 1, '财务建档', hhSub + (dirty ? ' · 有未保存修改' : ''), S.rev > 0)}
            ${link('scenarios', 2, '方案测算', scSub, !!p)}
            ${link('execution', 3, '执行与复盘', exSub, !!p && acts.length > 0 && done === acts.length)}
          </div>
        </div>
      </nav>
      <div class="rail-foot">
        <p>数据只保存在这台电脑上。<br>地址 127.0.0.1，不连接银行，不自动交易。</p>
        <button class="btn sm" type="button" data-act="pop" aria-haspopup="dialog" aria-expanded="${UI.pop}">${ic('list', 16)}评审视图</button>
      </div>
    </aside>
    <div class="main" id="main" tabindex="-1">
      <div class="wrap">
        ${UI.banner && !document.documentElement.classList.contains('still') ? `<div class="protobar" role="note"><span><b>评审用原型</b>：数据是虚构的，操作只发生在这个页面里。左下「评审视图」可以切换状态、显示评审标注、查看改动清单。</span><button type="button" class="linkbtn" data-act="banner-x">知道了</button></div>` : ''}
        <header class="topbar">
          <div class="who"><b>${esc(S.household.name)}</b><span class="extra"> · ${S.household.base_currency} · 上海时间 · ${S.today} ${dow(S.today)}</span></div>
          <span class="sp"></span>
          <button class="iconbtn" type="button" data-act="assist" ${fix('I12')}>${ic('spark', 18)}助手<span class="dotmark" title="未配置"></span></button>
          <button class="iconbtn sq" type="button" data-act="refresh" aria-label="刷新资料" title="刷新资料（不会清掉你未保存的修改）" ${fix('I1')}>${ic('refresh', 18)}</button>
          <button class="iconbtn sq mobile-only" type="button" data-act="pop" aria-label="评审视图">${ic('list', 18)}</button>
        </header>
        ${S.rev === 0 && UI.route !== 'overview' && UI.route !== 'household' ? '' : nextStrip()}
        ${content}
      </div>
    </div>
  </div>
  <nav class="tabbar" aria-label="底部导航">
    <a href="#/overview" data-nav ${cur === 'overview' ? 'aria-current="page"' : ''}>${ic('home')}规划</a>
    <a href="#/household" data-nav ${cur === 'household' ? 'aria-current="page"' : ''}>${ic('book')}建档</a>
    <a href="#/scenarios" data-nav ${cur === 'scenarios' ? 'aria-current="page"' : ''}>${ic('sliders')}测算</a>
    <a href="#/execution" data-nav ${cur === 'execution' ? 'aria-current="page"' : ''}>${ic('check')}执行</a>
  </nav>
  ${S.rev > 0 ? `<button class="fab" type="button" data-act="open-record" data-kind="expense">${ic('plus', 20, 2)}记一笔</button>` : ''}`;
}

function pageHead({ eyebrow, title, lede, meta = '' }) {
  return `<header class="page-head rv" style="--i:1">
    <div><p class="eyebrow">${eyebrow}</p><h1 class="title" ${fix('I15')}>${title}</h1>${lede ? `<p class="lede">${lede}</p>` : ''}</div>
    ${meta ? `<div class="meta">${meta}</div>` : ''}
  </header>`;
}
function planMeta() {
  const p = plan();
  if (!p) return chip('还没有使用中的方案', 'dashed');
  const changed = S.hhRev > p.rev;
  const n = newRecords();
  return `<span ${fix('N7')}>${changed ? chip(`${ic('alert', 14, 2)}建档已变更 · 建议重新测算`, 'warn') : chip(`方案 v${p.version} · 使用中 · 与建档同步`, 'up')}</span>
    <span>${changed ? '建档在方案确认之后改过。' : n ? `已有 ${n} 笔新记录，不影响方案条件。` : '账目与方案一致。'}</span>`;
}
function needHousehold(title) {
  return `${pageHead({ eyebrow: '先做第一步', title })}
  <section class="card empty rv" style="--i:2"><h2>这一页要先有建档</h2><p class="muted">账户、收支和目标是后面所有测算的起点。</p><div class="row">${btn('开始建档', { cls: 'primary', href: '#/household' })}${btn('用示例体验', { act: 'hh-example' })}</div></section>`;
}

/* ---------- 我的规划 ---------- */
function emptyOverview() {
  return `${pageHead({ eyebrow: '第一次使用', title: '让每一笔钱，都有下一步。', lede: '先说清现状，再谈目标。每个月只做四件事。' })}
  <section class="card empty rv" style="--i:2">
    <h2>从建档开始</h2>
    <ol>
      <li><b>1</b><span><strong>建档</strong>：账户、收支、目标（约 5 分钟）</span></li>
      <li><b>2</b><span><strong>试算</strong>：看目标够不够，缺口怎么补</span></li>
      <li><b>3</b><span><strong>执行</strong>：每月记下真实投入</span></li>
      <li><b>4</b><span><strong>复盘</strong>：月底对照计划，再调整</span></li>
    </ol>
    <div class="row">${btn('开始建档', { cls: 'primary', href: '#/household' })}${btn('用示例体验', { act: 'hh-example' })}</div>
    <p class="hint">所有数据只保存在这台电脑上；示例是虚构家庭，不会混进你的真实记录。</p>
  </section>`;
}

function goalCard(g, R, i) {
  const a = Math.min(g.funded, g.target);
  const future = Math.max(Math.min(g.projected - g.funded, g.target - a), 0);
  const gap = g.shortfall;
  const pct = (x) => `${((x / Math.max(g.target, 1)) * 100).toFixed(2)}%`;
  const lv = levers(g, R, S.household, planParams(), S.today);
  const over = Math.max(g.projected - g.target, 0);
  const extra = (planParams().ret || 0) ? '（含收益假设）' : '';
  return `<article class="card goal rv" style="--i:${4 + i}" aria-label="${esc(g.name)}">
    <div class="goal-head">
      <h3>${esc(g.name)}</h3>${chip(`第 ${g.priority} 优先`, 'plain')}
      ${gap ? chip(`缺口 ${Y(gap)}`, 'warn') : chip(`${ic('check', 14, 2)}按计划可达成`, 'up')}
      <span class="due">${g.due} 到期 · 还有 ${g.months} 个月</span>
    </div>
    <div class="track" role="img" aria-label="已分配 ${Y(g.funded)}，未来投入 ${Y(future)}，缺口 ${Y(gap)}，目标 ${Y(g.target)}">
      <span class="a" style="width:${pct(a)}"></span><span class="f" style="width:${pct(future)}"></span>${gap ? `<span class="g" style="width:${pct(gap)}"></span>` : ''}
      <span class="flag">目标 ${Y(g.target, { dec: false })}</span>
    </div>
    <div class="legend3">
      <div><span class="k"><i class="k-a"></i>已分配</span><b>${Y(g.funded)}</b><small>已经分给这个目标的钱</small></div>
      <div><span class="k"><i class="k-f"></i>未来投入</span><b>${Y(Math.max(g.projected - g.funded, 0))}</b><small>每月 ${Y(g.affordable)} × ${g.months} 个月${extra}${g.floorBinds ? ' · 含定投下限' : ''}</small></div>
      <div><span class="k"><i class="k-g"></i>${gap || !over ? '预计缺口' : '预计超出'}</span><b>${gap ? Y(gap) : over ? Y(over) : '无缺口'}</b><small>${gap ? `每月需要 ${Y(g.needed)} 才补得上` : over ? '到期时超出目标' : '到期时刚好达成目标'}</small></div>
    </div>
    ${lv ? `<div class="levers" ${fix('N8')}>
      <p>怎么补上 ${Y(gap)}？选一条，带到试算里看看：</p>
      <div class="lever-grid">
        ${lv.monthly ? `<button type="button" class="lever" data-act="lever" data-goal="${g.id}" data-kind="monthly"><b>每月多投 ${Y(lv.monthly.extra)}</b><small>每月 ${Y(g.affordable)} → ${Y(lv.monthly.newMonthly)}。${lv.monthly.fits ? `<span class="ok">在月度剩余额度 ${Y(R.remaining)} 之内</span>` : `<span class="no">超出月度剩余额度 ${Y(R.remaining)}，需同时增加收入或下调其它目标</span>`}</small></button>` : ''}
        ${lv.ext ? `<button type="button" class="lever" data-act="lever" data-goal="${g.id}" data-kind="ext"><b>延期 ${lv.ext.months} 个月</b><small>到期日 ${g.due} → ${lv.ext.due}。每月不用多出钱，目标晚一点实现。</small></button>` : ''}
        ${lv.reduce ? `<button type="button" class="lever" data-act="lever" data-goal="${g.id}" data-kind="reduce"><b>目标下调 ${lv.reduce.pct}%</b><small>${Y(g.target)} → ${Y(lv.reduce.newTarget)}。每月投入不变。</small></button>` : ''}
      </div></div>` : ''}
  </article>`;
}

function actCard(a, i = 0) {
  const g = goal(a.goal_id);
  const pct = a.amount ? Math.min(100, Math.round((a.actual * 100) / a.amount)) : 0;
  const recs = a.records.map((e) => `${md(e.date)} · ${esc(asset(e.asset_id)?.name || '')} → ${esc(asset(e.target_asset_id)?.name || '')} · ${Y(e.target_amount ?? e.amount)}`);
  const recAttr = `data-kind="contribution" data-action="${a.id}"`;
  return `<article class="card act rv ${UI.pulse === a.id ? 'pulse' : ''}" style="--i:${i}" id="act-${a.id}" ${i === 3 ? fix('I3') : ''}>
    <div>
      <div class="row"><h3>${esc(g.name)}</h3>${statusChip(a.status)}${a.excess ? chip(`超出 ${Y(a.excess)}`, 'warn') : ''}</div>
      <p class="muted small">${monthLabel(a.month)}投入计划</p>
      <div class="figs"><div><small>本月计划</small><b>${Y(a.amount)}</b></div><div><small>已投入</small><b>${Y(a.actual)}</b></div><div><small>还需投入</small><b>${Y(a.remaining)}</b></div></div>
    </div>
    <div class="side">${a.status === 'completed' ? btn('再记一笔', { cls: 'ghost sm', act: 'open-record', data: recAttr }) : btn('记录本次投入', { cls: 'primary', act: 'open-record', data: recAttr })}</div>
    <div class="bar ${a.status === 'completed' ? 'done' : ''}" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100" aria-label="${esc(g.name)}本月投入进度"><i style="width:${pct}%"></i></div>
    <div class="recs">${recs.length ? recs.join('<br>') : '本月还没有投入记录。'}</div>
  </article>`;
}

function viewOverview() {
  if (S.rev === 0) return emptyOverview();
  const R = RES();
  if (R.incomplete) {
    return `${pageHead({ eyebrow: '建档还没填完', title: '我的规划', lede: '资料不完整时，数字不会被当成 0。' })}
    <section class="card empty rv" style="--i:2"><h2>先把这几项补上</h2><ul class="stack">${R.missing.map((m) => `<li class="row">${ic('alert', 18)}<span>${esc(m)}</span></li>`).join('')}</ul><div class="row">${btn('去补全建档', { cls: 'primary', href: '#/household' })}</div></section>`;
  }
  const acts = ACTS();
  const p = plan();
  const info = monthInfo();
  const est = R.rows.filter((r) => r.quality === 'estimated');
  const reserveOk = R.reserveShortfall === 0;
  const goalsTotal = R.goals.reduce((s, g) => s + g.affordable, 0);
  const incomeBase = Math.max(R.income, R.expenses + R.minimum + goalsTotal + R.unlinked);
  const w = (x) => `${((x / incomeBase) * 100).toFixed(2)}%`;
  const strata = [['reserve', '应急预留', R.reserve], ['alloc', '已分给目标', R.cashAllocated], ['free', '可自由安排', R.free]];
  const assetRowName = (r) => `${esc(r.name)}${r.quality === 'estimated' ? '（估算）' : ''}`;
  return `
  ${pageHead({ eyebrow: `${info.label} · 第 ${info.day} 天，共 ${info.n} 天`, title: '我的规划', lede: '让每一笔钱，都有下一步。', meta: planMeta() })}

  <section class="hero rv" style="--i:2">
    <div class="hero-main" ${fix('N1')}>
      <p class="eyebrow">可自由安排的现金</p>
      <p class="hero-num" aria-label="${Y(R.free)}">${bigMoney(R.free)}</p>
      <p class="hero-sub">账户现金共 <b class="num">${Y(R.cash)}</b>。其中 <b class="num">${Y(R.reserve)}</b> 是应急预留（必要支出 ${Y(S.household.essential, { dec: false })} × ${S.household.reserve_months} 个月），<b class="num">${Y(R.cashAllocated)}</b> 已经分给了目标——剩下的，才是能自由安排的钱。</p>
      <div class="strata">
        <div class="strata-bar" role="img" aria-label="现金构成：应急预留 ${Y(R.reserve)}，已分给目标 ${Y(R.cashAllocated)}，可自由安排 ${Y(R.free)}">
          ${strata.map(([k, n, v]) => `<div class="seg ${k}" style="flex:${Math.max(v, 1)}"><span>${n}</span></div>`).join('')}
        </div>
        <div class="strata-legend">
          ${ld({ sw: 'reserve', label: `应急预留 ${reserveOk ? chip('已备足', 'up') : chip(`缺口 ${Y(R.reserveShortfall)}`, 'warn')}`, sub: '先留足，不参与分配', amount: Y(R.reserve) })}
          ${ld({ sw: 'alloc', label: '已分给目标', sub: R.goals.filter((g) => S.household.allocations.some((a) => a.goal_id === g.id && asset(a.asset_id).kind === 'cash')).map((g) => g.name).join('、'), amount: Y(R.cashAllocated) })}
          ${ld({ sw: 'free', label: '可自由安排', sub: '没有用途标记的现金', amount: Y(R.free) })}
          ${ld({ cls: 'total', label: '账户现金合计', amount: Y(R.cash) })}
        </div>
      </div>
    </div>
    <aside class="card nw">
      <p class="eyebrow">净资产 ${est.length ? chip(`含 ${est.length} 项估算`, 'warn') : ''}</p>
      <p class="nw-num">${bigMoney(R.netWorth)}</p>
      <div class="nw-bars" role="list" aria-label="净资产构成">
        ${R.rows.slice().sort((a, b) => b.balance - a.balance).map((r) => `<div class="nwb" role="listitem"><div class="nwb-top"><span>${esc(r.name)}${r.liq !== 'available' ? ' <span class="sub">不易变现</span>' : ''}</span><b class="num">${Y(r.balance, { dec: false })}</b></div><div class="nwb-bar ${r.liq !== 'available' ? 'ill' : ''}"><i style="width:${Math.max((r.balance / Math.max(R.assetsTotal, 1)) * 100, 1.2).toFixed(2)}%"></i></div></div>`).join('')}
        ${R.debtTotal ? `<div class="nwb" role="listitem"><div class="nwb-top"><span>负债 <span class="sub">${S.household.debts.map((d) => esc(d.name)).join('、')}</span></span><b class="num neg">−${Y(R.debtTotal, { dec: false })}</b></div><div class="nwb-bar debt"><i style="width:${Math.max((R.debtTotal / Math.max(R.assetsTotal, 1)) * 100, 1.2).toFixed(2)}%"></i></div></div>` : ''}
      </div>
      <p class="hint" style="margin-top:14px">房产与受限资产计入净资产，但不会算进可自由安排的现金。</p>
      ${R.warnings.length ? `<details><summary>${ic('chev', 16)}哪些是估算？</summary><p class="hint">${R.warnings.map(esc).join('；')}。估算值已在建档里标注，换成真实数字后这里会更准。</p></details>` : ''}
    </aside>
  </section>

  <div class="two sec" style="margin-top:36px">
    <section class="rv" style="--i:3" aria-label="本月行动">
      ${secHead('01', '本月行动', acts.length ? `${acts.filter((a) => a.status === 'completed').length} / ${acts.length} 已完成` : '')}
      ${acts.length ? acts.map((a, i) => actCard(a, i + 3)).join('') : `<div class="card pad"><p class="muted">${p ? '这个月还没有投入计划。' : '确认方案后，这里会出现本月要做的事。'}</p><div class="row" style="margin-top:12px">${btn(p ? '去生成本月计划' : '去试算', { cls: 'primary', href: p ? '#/execution' : '#/scenarios' })}</div></div>`}
    </section>
    <section class="rv" style="--i:4" aria-label="本月预算">
      ${secHead('02', '本月预算', `每月 ${Y(R.income, { dec: false })} 的去向`)}
      <div class="card pad" ${fix('N5')}>
        ${ld({ sign: '＋', label: '月收入', amount: Y(R.income) })}
        ${ld({ sign: '−', sw: 'c-exp', label: '月支出', note: `其中必要支出 ${Y(S.household.essential, { dec: false })}`, amount: `−${Y(R.expenses)}` })}
        ${ld({ sign: '−', sw: 'c-debt', label: '最低还款', note: S.household.debts.filter((d) => !d.in_expenses).map((d) => esc(d.name)).join('、'), amount: `−${Y(R.minimum)}` })}
        ${ld({ sign: '−', sw: 'c-goal', label: '目标投入', note: R.goals.map((g) => `${esc(g.name)} ${Y(g.affordable, { dec: false })}`).join(' + '), amount: `−${Y(goalsTotal + R.unlinked)}` })}
        ${ld({ cls: 'total', sign: '＝', sw: 'c-left', label: '本月可再分配', amount: Y(R.remaining) })}
        <div class="tape-bar" role="img" aria-label="收入的去向：支出、最低还款、目标投入、剩余">
          <span class="c-exp" style="width:${w(R.expenses)}"></span><span class="c-debt" style="width:${w(R.minimum)}"></span><span class="c-goal" style="width:${w(goalsTotal + R.unlinked)}"></span><span class="c-left" style="width:${w(Math.max(R.remaining, 0))}"></span>
        </div>
        <p class="hint">${R.goals.some((g) => g.floorBinds || g.floor) ? `已有定投 ${Y(R.goals.reduce((s, g) => s + g.floor, 0))} 已包含在目标投入里，不会重复扣减。` : ''}${R.errors.length ? R.errors.map(esc).join('；') : ''}</p>
      </div>
    </section>
  </div>

  <section class="sec" aria-label="目标进度">
    ${secHead('03', '目标进度', '按优先级排：资金不够时，靠前的目标先拿到预算')}
    ${R.goals.map((g, i) => goalCard(g, R, i)).join('')}
  </section>

  <section class="sec rv" style="--i:7" aria-label="数字从哪来">
    ${secHead('04', '这些数字从哪来', `测算日 ${S.today} · 账目修订 ${S.rev}`)}
    <div class="card prov" ${fix('N3')}>
      <details open><summary>${ic('chev', 16)}账户余额 = 基准 + 已记流水</summary>
        ${R.rows.map((r) => {
          const n = r.moves.length;
          const eq = n
            ? `基准 <b>${Y(r.anchor)}</b>（${md(r.observed_on)} 日终 · ${r.quality === 'estimated' ? '估算' : '已核实'}）${r.moved < 0 ? '−' : '＋'} 已记流水 <b>${Y(Math.abs(r.moved))}</b>（${n} 笔）＝ <b>${Y(r.balance)}</b>（截至 ${md(S.today)}）`
            : `基准 <b>${Y(r.anchor)}</b>（${md(r.observed_on)} 日终 · ${r.quality === 'estimated' ? '估算' : '已核实'}），之后没有流水`;
          return `<div class="item"><div class="row"><b>${assetRowName(r)}</b><span class="sp"></span>${r.kind === 'cash' ? `<button class="btn ghost sm" type="button" data-act="recon-open" data-asset="${r.id}" ${fix('P4')}>对账</button>` : ''}</div><p class="eq">${eq}</p></div>`;
        }).join('')}
        <p class="hint" style="margin-top:12px">只记一部分流水时，这里的余额会逐渐偏离银行里的数字——用「对账」填入银行实际余额，系统会生成一条可作废的差额调整，基准日不变。</p>
      </details>
    </div>
  </section>`;
}

/* ---------- 执行与复盘 ---------- */
function ribbon() {
  const i = monthInfo();
  const past = ((i.day / i.n) * 100).toFixed(2);
  const zl = (((i.zoneStart - 1) / i.n) * 100).toFixed(2);
  const zw = (((i.n - i.zoneStart + 1) / i.n) * 100).toFixed(2);
  return `<section class="card ribbon rv" style="--i:2" ${fix('P1')} aria-label="本月进度">
    <div class="ribbon-top"><b>${i.label}</b><span class="muted">第 ${i.day} 天 / 共 ${i.n} 天 · ${i.left ? `距月末还有 ${i.left} 天` : '今天是月末最后一天'}</span><span class="sp"></span>${chip(`复盘建议期 ${i.zoneStart}–${i.n} 日`, 'warn')}</div>
    <div class="ribbon-track"><span class="zone" style="left:${zl}%;width:${zw}%"></span><span class="past" style="width:${past}%"></span><span class="now" style="left:${past}%"></span></div>
    <div class="ribbon-scale"><span>1 日</span><span>边做边记 · 月底复盘</span><span>${i.n} 日</span></div>
  </section>`;
}

function renewCard() {
  const p = plan();
  const R = RES();
  const info = monthInfo();
  const gaps = R.goals.filter((g) => g.shortfall > 0);
  return `<section class="card renew rv" style="--i:3" ${fix('P1')}>
    <h3>${info.label}还没有投入计划</h3>
    <p>方案 v${p.version} 仍在使用。要在这个月继续记投入，需要你确认「沿用 v${p.version}」——不会自动改动任何金额，也不会交易或移动资金。</p>
    <div class="stack" style="gap:6px">${R.goals.map((g) => ld({ label: esc(g.name), sub: `还剩 ${g.months} 个月`, amount: Y(g.affordable) })).join('')}</div>
    ${gaps.length ? `<label class="consent"><input type="checkbox" data-act="renew-consent" ${UI.renewConsent ? 'checked' : ''}><span>我已了解：${gaps.map((g) => `「${esc(g.name)}」按现在的投入仍有 ${Y(g.shortfall)} 缺口`).join('；')}。</span></label>` : ''}
    <div class="row">
      <button class="btn primary" type="button" data-act="renew" ${gaps.length && !UI.renewConsent ? 'disabled' : ''}>沿用方案，生成本月计划</button>
      ${btn('先去试算调整', { href: '#/scenarios' })}
      ${gaps.length && !UI.renewConsent ? '<span class="hint">勾选上面的确认后才能沿用。</span>' : ''}
    </div>
  </section>`;
}

function ledgerRows() {
  const items = S.ledger.slice().sort((a, b) => (a.date < b.date ? 1 : -1));
  const f = UI.filter;
  const rows = items.filter((e) => (f === 'all' ? true : f === 'voided' ? e.voided : f === 'forecast' ? !e.voided && e.state === 'forecast' : !e.voided && e.state === 'confirmed'));
  if (!rows.length) return `<tr><td colspan="5" class="muted" style="padding:28px 14px">${f === 'all' ? '还没有记录。点上面的「记录本次投入」或「＋ 记一笔」开始。' : '这个筛选下没有记录。'}</td></tr>`;
  const kindName = { income: '收入', expense: '支出', transfer: '转账', debt_payment: '还款' };
  return rows.map((e) => {
    const src = asset(e.asset_id);
    const tgt = asset(e.target_asset_id);
    const amt = Y(e.amount);
    const arrive = Y(e.target_amount ?? e.amount);
    const lines = e.kind === 'income' ? [`<span class="plus">${esc(src?.name)} ＋${amt}</span>`]
      : e.kind === 'transfer' ? [`<span class="minus">${esc(src?.name)} −${amt}</span>`, `<span class="plus">${esc(tgt?.name)} ＋${arrive}</span>`]
      : [`<span class="minus">${esc(src?.name)} −${amt}</span>`];
    const g = e.goal_id ? goal(e.goal_id) : null;
    const st = e.voided ? 'voided' : e.state;
    return `<tr class="${e.voided ? 'void' : ''}">
      <td class="num" style="white-space:nowrap">${e.date}<div class="faint small">${dow(e.date)}</div></td>
      <td><span class="strike"><b>${e.adjust ? '对账调整' : g ? '投入目标' : kindName[e.kind]}</b>${g ? ` · ${esc(g.name)}` : ''}</span>
        <div class="faint small">${e.mode === 'move_existing' ? '移动已分配资金，不计入进度' : g ? '新增投入 · 计入本月进度' : ''}${e.note ? ` ${esc(e.note)}` : ''}${e.source ? ` · ${esc(e.source)}` : ''}</div></td>
      <td><div class="eff">${lines.join('')}</div></td>
      <td>${statusChip(st)}</td>
      <td class="r">${!e.voided ? `<button class="btn sm" type="button" data-act="void-open" data-id="${e.id}" ${fix('I7')}>作废</button>` : ''}</td>
    </tr>`;
  }).join('');
}

function monthReview(m, closing) {
  const ev = S.ledger.filter((e) => !e.voided && e.state === 'confirmed' && e.date.startsWith(m));
  const sum = (k) => ev.filter((e) => e.kind === k);
  const total = (arr) => arr.reduce((s, e) => s + e.amount, 0);
  const inc = sum('income');
  const exp = sum('expense');
  const debt = sum('debt_payment');
  const p = plan();
  const acts = actionsFor(p, S.ledger, m, S.today);
  const info = monthInfo();
  const left = m === info.m ? info.left : 0;
  const tile = (name, arr, hint) => `<div class="tile"><small>${name}</small>${arr.length ? `<b>${Y(total(arr))}</b><span>已记录 ${arr.length} 笔</span>` : `<b class="none">未记录</b><span>${hint}</span>`}</div>`;
  const stale = S.reviewRev != null && S.reviewRev !== S.rev;
  return `<article class="card review rv" style="--i:2" ${fix('N2')}>
    <div class="row"><h3 style="font:700 20px var(--font-sans)">${monthLabel(m)}${closing ? '收官复盘' : '复盘'}</h3>${closing ? chip('本月已结束', 'plain') : chip(`进行中 · 还剩 ${left} 天`, 'olive')}${stale && !closing ? chip('有新记录，建议更新', 'warn') : ''}<span class="sp"></span>
      ${closing ? btn('更新为收官复盘', { cls: 'sm', act: 'review-make', data: `data-m="${m}"` }) : btn(S.reviewRev != null ? '更新本月复盘' : '生成本月复盘', { cls: 'primary sm', act: 'review-make', data: `data-m="${m}"` })}</div>
    <div class="review-banner">${ic('info', 18)}<span>${closing ? `${monthLabel(m)}已经结束，这是最后一次对照；<b>${md(monthAfter(m + '-01', 1).slice(0, 7) + '-10')} 前可以补做</b>，之后只读。` : `截至 ${md(S.today)}，这个月还没结束。`}这里只统计你确认过的记录——<b>没有记录不等于没有收支</b>。</span></div>
    <div class="tiles">
      ${tile('已记录收入', inc, `建档里预计 ${Y(S.household.income, { dec: false })}/月，不会自动记为实际`)}
      ${tile('已记录支出', exp, `建档里预计 ${Y(S.household.expenses, { dec: false })}/月`)}
      ${tile('已记录还款', debt, `最低还款 ${Y(S.household.debts[0]?.minimum || 0, { dec: false })}/月`)}
    </div>
    <div style="margin-top:20px">
      ${acts.map((a) => {
        const g = goal(a.goal_id);
        const pc = a.amount ? Math.min(100, Math.round((a.actual * 100) / a.amount)) : 0;
        const verdict = a.status === 'completed' ? chip(`${ic('check', 14, 2)}已达成`, 'up') : closing ? chip(`未达成 · 差 ${Y(a.remaining)}`, 'down') : chip(`进行中 · 还差 ${Y(a.remaining)}`, 'olive');
        return `<div class="rv-goal"><div class="row"><b>${esc(g.name)}</b>${verdict}</div><div class="num"><b>${Y(a.actual)}</b> <span class="muted">/ ${Y(a.amount)}</span></div><div class="bar ${a.status === 'completed' ? 'done' : ''}"><i style="width:${pc}%"></i></div></div>`;
      }).join('') || '<p class="muted">这个月没有投入计划可对照。</p>'}
    </div>
    <div class="row" style="margin-top:18px">${btn('按偏差调整方案', { href: '#/scenarios', icon: 'arrow' })}<span class="hint">复盘不会修改建档或流水。</span></div>
  </article>`;
}

function viewExecution() {
  if (S.rev === 0) return needHousehold('每月做一点，变化看得见。');
  const p = plan();
  const acts = ACTS();
  const info = monthInfo();
  const isNew = S.today.slice(0, 7) !== '2026-10';
  const rec = S.reviewRev != null;
  const filters = [['all', '全部'], ['confirmed', '已发生'], ['forecast', '预计'], ['voided', '已作废']];
  return `
  ${pageHead({ eyebrow: info.label, title: '每月做一点，变化看得见。', lede: '记下真实发生的事，再与方案对照。', meta: `${btn('＋ 记一笔', { cls: 'primary', act: 'open-record', data: 'data-kind="expense"' })}${planMeta()}` })}
  ${ribbon()}
  <section class="sec" aria-label="本月投入计划">
    ${secHead('01', '本月投入计划', acts.length ? `${monthLabel(info.m)} · 记录投入后进度自动累计` : '')}
    ${!p ? `<div class="card pad"><p class="muted">还没有使用中的方案。先去试算并确认一份方案，这里才会出现本月计划。</p><div class="row" style="margin-top:12px">${btn('去方案测算', { cls: 'primary', href: '#/scenarios' })}</div></div>`
      : acts.length ? `<div class="stack">${acts.map((a, i) => actCard(a, i + 3)).join('')}</div>` : renewCard()}
  </section>
  <section class="sec rv" style="--i:5" aria-label="流水与预测">
    ${secHead('02', '流水与预测', '只有「已发生」会改动余额；预计不入账')}
    <div class="row" style="margin-bottom:14px"><div class="filters" role="group" aria-label="筛选">${filters.map(([k, n]) => `<button type="button" data-act="filter" data-f="${k}" aria-pressed="${UI.filter === k}">${n}</button>`).join('')}</div><span class="sp"></span>${btn('＋ 记一笔', { cls: 'sm', act: 'open-record', data: 'data-kind="expense"' })}</div>
    <div class="card ledger-wrap"><table class="ledger"><thead><tr><th>日期</th><th>说明</th><th class="r">账户变化</th><th>状态</th><th class="r">操作</th></tr></thead><tbody>${ledgerRows()}</tbody></table></div>
  </section>
  <section class="sec" aria-label="复盘">
    ${secHead('03', isNew ? '复盘' : '本月复盘', '每月一次，对照计划与实际')}
    ${isNew
      ? `<div class="stack">${rec ? monthReview('2026-10', true) : ''}${`<div class="card pad rv" style="--i:2"><div class="row"><b>${info.label}复盘</b>${chip(`进行中 · 还剩 ${info.left} 天`, 'olive')}<span class="sp"></span>${btn('生成本月复盘', { cls: 'primary sm', act: 'review-make', data: `data-m="${info.m}"` })}</div><p class="hint" style="margin-top:8px">还没有生成。月底前（${info.zoneStart}–${info.n} 日）生成即可，也可以中途先看看。</p></div>`}</div>`
      : rec ? monthReview(info.m, false)
      : `<div class="card pad rv" style="--i:2"><div class="row"><b>${info.label}复盘</b>${chip(`进行中 · 还剩 ${info.left} 天`, 'olive')}<span class="sp"></span>${btn('生成本月复盘', { cls: 'primary sm', act: 'review-make', data: `data-m="${info.m}"` })}</div><p class="hint" style="margin-top:8px">还没有生成。月底前（${info.zoneStart}–${info.n} 日）生成即可，也可以中途先看看。${info.day >= info.zoneStart ? '今天已经进入复盘建议期。' : ''}</p></div>`}
  </section>`;
}
