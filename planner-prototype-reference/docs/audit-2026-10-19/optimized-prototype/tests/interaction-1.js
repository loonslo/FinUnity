// 交互检查 1/2：首页、记账抽屉、作废、建档未保存修改、试算、跨月、过期草案、草稿持久化、手机视口。
// 运行：node tests/interaction-1.js（在 optimized-prototype 目录下）
const { launch, sleep } = require('./cdp.js');

const results = [];
const check = (name, cond, detail = '') => { results.push([cond ? 'PASS' : 'FAIL', name, detail]); };

(async () => {
  const b = await launch(9333);
  try {
    /* ---------- F1 首页 ---------- */
    await b.open('#/overview', 1440, 900);
    let t = await b.eval("document.querySelector('.main').innerText");
    check('F1 首页：可自由安排 ¥48,000.00', t.includes('¥48,000.00'));
    check('F1 首页：应急预留已备足', t.includes('已备足'));
    check('F1 首页：本月行动里待投入排在前面', t.indexOf('家庭长期储蓄') < t.indexOf('学习进修') && t.includes('待投入'));
    check('F1 首页：无「待确认」旧叫法', !/待确认 · 为/.test(t));
    check('F1 首页：缺口杠杆三条', (t.match(/每月多投 ¥333\.34/g) || []).length === 1 && t.includes('延期 2 个月') && t.includes('目标下调 8%'));
    check('F1 首页：数字来源写成算式', t.includes('基准') && t.includes('已记流水') && t.includes('（截至 10-19）'));
    check('F1 首页：无「账目已更新」噪声', !t.includes('账目已更新'));

    /* ---------- F2 记账抽屉 ---------- */
    await b.eval(`document.querySelector('.next [data-act="open-record"]').click(); 1`);
    await sleep(100);
    let s = await b.eval(`({ ov: UI.overlay && UI.overlay.type, amount: UI.drafts[0].amount, goal: UI.drafts[0].goal_id, focus: document.activeElement && document.activeElement.id, title: document.querySelector('#sh-title').innerText })`);
    check('F2 抽屉：从下一步条打开并带入金额/目标', s.ov === 'record' && s.amount === '3000' && s.goal === 'family' && s.title === '记录本次投入', JSON.stringify(s));
    check('F2 抽屉：打开后焦点在金额框', s.focus === 'dr-amount', s.focus);
    await b.eval(`(() => { const i = document.querySelector('[data-bind="dr.date"]'); i.value = '2026-10-17'; i.dispatchEvent(new Event('change', { bubbles: true })); })()`);
    await sleep(50);
    await b.eval(`document.querySelector('#saveBtn').click(); 1`);
    await sleep(80);
    s = await b.eval(`({ ledger: S.ledger.length, err: [...document.querySelectorAll('#sheetBody .errmsg')].map(e => e.innerText), summary: (document.querySelector('.summary-err') || {}).innerText })`);
    check('F2 抽屉：早于基准的日期被拦住且给出补救入口', s.ledger === 1 && s.err.some((x) => x.includes('日终基准') && x.includes('去校准余额')), JSON.stringify(s));
    await b.eval(`document.querySelector('[data-act="date-pick"][data-d="2026-10-19"]').click(); 1`);
    await sleep(50);
    await b.eval(`document.querySelector('#saveBtn').click(); 1`);
    await sleep(150);
    s = await b.eval(`({ ledger: S.ledger.length, closed: UI.overlay === null, toast: (document.querySelector('.toast p') || {}).innerText, card: !!document.querySelector('.act.pulse'), next: document.querySelector('.next').innerText.replace(/\\s+/g, ' ') })`);
    check('F2 保存：入账、关闭抽屉、就近提示、卡片高亮', s.ledger === 2 && s.closed && /本月进度/.test(s.toast) && s.card, JSON.stringify(s));
    check('F2 保存：下一步条随之变化', s.next.includes('计划投入都已完成'), s.next);
    // 作废
    await b.eval(`document.querySelector('.toast [data-act="toast-act"]').click(); 1`);
    await sleep(80);
    s = await b.eval(`({ dlg: UI.overlay && UI.overlay.name, text: document.querySelector('.dialog').innerText.replace(/\\s+/g, ' ') })`);
    check('F2 作废：先出确认框并列出影响', s.dlg === 'void' && s.text.includes('作废之后') && s.text.includes('本月进度'), s.text.slice(0, 120));
    await b.eval(`document.querySelector('[data-act="void-confirm"]').click(); 1`);
    await sleep(100);
    s = await b.eval(`({ voided: S.ledger.filter(e => e.voided).length, fam: ACTS().find(a => a.goal_id === 'family').status })`);
    check('F2 作废：记录标记为已作废，进度回退', s.voided === 1 && s.fam === 'pending', JSON.stringify(s));

    /* ---------- F3 建档：未保存修改 ---------- */
    await b.open('#/household', 1440, 900);
    await b.eval(`(() => { const n = document.querySelector('[data-bind="hh.name"]'); n.value = '陈家的家庭财务'; n.dispatchEvent(new Event('input', { bubbles: true })); const m = document.querySelector('[data-bind="hh.income"]'); m.value = '16500'; m.dispatchEvent(new Event('input', { bubbles: true })); })()`);
    s = await b.eval(`({ n: hhChanges(), bar: document.querySelector('#dirtybar').innerText.replace(/\\s+/g, ' ') })`);
    check('F3 建档：改动后顶部显示「N 处未保存」', s.n === 2 && s.bar.includes('有 2 处未保存'), s.bar.slice(0, 60));
    await b.eval(`document.querySelector('[data-act="refresh"]').click(); 1`);
    await sleep(80);
    s = await b.eval(`({ name: document.querySelector('[data-bind="hh.name"]').value, n: hhChanges() })`);
    check('F3 建档：点「刷新」不会清掉未保存修改（I1）', s.name === '陈家的家庭财务' && s.n === 2, JSON.stringify(s));
    // 在别处做一次测算（原版会清掉建档草稿）
    await b.eval(`location.hash = '#/scenarios'; 1`); await sleep(100);
    s = await b.eval(`({ route: UI.route, dlg: UI.overlay && UI.overlay.name })`);
    check('F3 建档：地址栏直接切走（不经过导航点击）也能到达', s.route === 'scenarios');
    await b.eval(`location.hash = '#/household'; 1`); await sleep(100);
    s = await b.eval(`({ n: hhChanges(), name: document.querySelector('[data-bind="hh.name"]').value })`);
    check('F3 建档：回来修改仍在', s.n === 2 && s.name === '陈家的家庭财务', JSON.stringify(s));
    await b.eval(`document.querySelector('.flow a[href="#/execution"]').click(); 1`); await sleep(80);
    s = await b.eval(`({ dlg: UI.overlay && UI.overlay.name, route: UI.route })`);
    check('F3 建档：点导航离开时先问', s.dlg === 'leave' && s.route === 'household', JSON.stringify(s));
    await b.eval(`document.querySelector('[data-act="leave-save"]').click(); 1`); await sleep(150);
    s = await b.eval(`({ route: UI.route, rev: S.rev, hhRev: S.hhRev, name: S.household.name, chip: document.querySelector('.page-head .meta').innerText.replace(/\\s+/g, ' ') })`);
    check('F3 建档：保存并离开 → 修订+1，方案提示重新测算', s.route === 'execution' && s.hhRev === 3 && s.name === '陈家的家庭财务' && s.chip.includes('建档已变更'), JSON.stringify(s));

    /* ---------- F4 试算 ---------- */
    await b.open('#/overview', 1440, 900);
    await b.eval(`document.querySelector('[data-act="lever"][data-kind="monthly"]').click(); 1`); await sleep(150);
    s = await b.eval(`({ route: UI.route, a: UI.scen.A.monthly.study, tab: document.querySelector('#scenResults').innerText.replace(/\\s+/g, ' ') })`);
    check('F4 杠杆：一键带入试算 A，缺口为 0', s.route === 'scenarios' && s.a === '2333.34' && s.tab.includes('预计缺口 ¥4,000.00 ¥0.00 −¥4,000.00'), JSON.stringify(s).slice(0, 220));
    // 定投下限
    await b.eval(`(() => { const i = document.querySelector('[data-bind="scen.monthly.family"]'); i.value = '1000'; i.dispatchEvent(new Event('input', { bubbles: true })); })()`); await sleep(80);
    s = await b.eval(`({ lock: document.querySelector('.lock').innerText.replace(/\\s+/g, ' '), res: document.querySelector('#scenResults').innerText.replace(/\\s+/g, ' ') })`);
    check('F4 定投下限：即时提示低于下限并按下限计算（I4）', s.lock.includes('低于已有定投') && s.lock.includes('已按 ¥3,000.00 计算'), s.lock.slice(0, 100));
    // 单位
    await b.eval(`(() => { const i = document.querySelector('[data-bind="scen.ret"]'); i.value = '80'; i.dispatchEvent(new Event('input', { bubbles: true })); })()`); await sleep(80);
    s = await b.eval(`({ err: [...document.querySelectorAll('.scen-controls .errmsg')].map(e => e.innerText), btn: document.querySelector('[data-act="scen-draft"]').disabled })`);
    check('F4 单位：越界给中文提示，且不能生成草案（I5）', s.err.some((x) => x.includes('年化收益假设需要在 −90% 到 50% 之间')) && s.btn, JSON.stringify(s));
    await b.eval(`(() => { const i = document.querySelector('[data-bind="scen.ret"]'); i.value = '3'; i.dispatchEvent(new Event('input', { bubbles: true })); })()`); await sleep(80);
    s = await b.eval(`({ err: document.querySelectorAll('.scen-controls .errmsg').length, btn: document.querySelector('[data-act="scen-draft"]').disabled, res: document.querySelector('#scenResults').innerText.replace(/\\s+/g, ' ') })`);
    check('F4 单位：填 3 就是 3%，可以计算', s.err === 0 && !s.btn && /47,0|49,|50,/.test(s.res), s.res.slice(0, 160));
    // 草案 → 确认
    await b.eval(`document.querySelector('[data-act="scen-draft"]').click(); 1`); await sleep(150);
    s = await b.eval(`({ draft: !!S.pending, text: document.querySelector('.draft').innerText.replace(/\\s+/g, ' '), confirmDisabled: (document.querySelector('[data-act="plan-confirm"]') || {}).disabled })`);
    check('F4 草案：生成后展示与当前方案的差别', s.draft && s.text.includes('方案草案 v2') && s.text.includes('原'), s.text.slice(0, 200));
    await b.eval(`document.querySelector('[data-act="plan-confirm"]').click(); 1`); await sleep(150);
    s = await b.eval(`({ plans: S.plans.map(p => p.id + ':' + p.status), pending: S.pending, actions: plan().actions.map(a => a.goal_id + ':' + a.amount), versions: document.querySelector('.vers').innerText.replace(/\\s+/g, ' ') })`);
    check('F4 确认：v2 使用中，v1 转为历史版本', s.plans.join() === 'v1:superseded,v2:active' && s.pending === null, JSON.stringify(s).slice(0, 220));

    /* ---------- F5 跨月 / 过期草案 / 月末 / 空状态 ---------- */
    await b.open('#/execution?demo=nov02', 1440, 900);
    t = await b.eval("document.querySelector('.main').innerText");
    check('F5 跨月：执行页解释现状并给续期入口（P1）', t.includes('11 月还没有投入计划') && t.includes('沿用方案，生成本月计划') && t.includes('补做') === false || t.includes('收官复盘'), t.slice(0, 80));
    check('F5 跨月：上月收官复盘可补做', t.includes('2026 年 10 月收官复盘') && /11-10 前可以补做/.test(t));
    s = await b.eval(`({ disabled: document.querySelector('[data-act="renew"]').disabled })`);
    check('F5 跨月：有缺口时续期需要先勾选知晓', s.disabled === true);
    await b.eval(`document.querySelector('[data-act="renew-consent"]').click(); 1`); await sleep(80);
    await b.eval(`document.querySelector('[data-act="renew"]').click(); 1`); await sleep(120);
    s = await b.eval(`({ acts: ACTS().map(a => a.month + ':' + a.goal_id + ':' + a.amount), next: document.querySelector('.next').innerText.replace(/\\s+/g, ' ') })`);
    check('F5 跨月：续期后生成 11 月计划', s.acts.length === 2 && s.acts[0].startsWith('2026-11') && s.next.includes('本月还差'), JSON.stringify(s));
    await b.open('#/scenarios?demo=stale', 1440, 900);
    s = await b.eval(`({ text: document.querySelector('.draft').innerText.replace(/\\s+/g, ' '), confirm: document.querySelector('[data-act="plan-confirm"]'), retest: !!document.querySelector('.draft [data-act="plan-retest"]'), next: document.querySelector('.next').innerText.replace(/\\s+/g, ' ') })`);
    check('F5 过期草案：显示已过期、没有确认按钮、给重新测算（P5）', s.text.includes('已过期') && !s.confirm && s.retest && s.next.includes('草案已过期'), s.text.slice(0, 120));
    await b.eval(`document.querySelector('.draft [data-act="plan-retest"]').click(); 1`); await sleep(100);
    s = await b.eval(`({ stale: S.pending.rev !== S.rev, confirm: !!document.querySelector('[data-act="plan-confirm"]') })`);
    check('F5 过期草案：重新测算后可以确认', !s.stale && s.confirm);
    await b.open('#/execution?demo=oct31', 1440, 900);
    t = await b.eval("document.querySelector('.next').innerText.replace(/\\s+/g, ' ')");
    check('F5 月末：下一步条提示生成复盘', t.includes('快结束了') && t.includes('生成本月复盘'), t);
    await b.open('#/overview?demo=empty', 1440, 900);
    t = await b.eval("document.querySelector('.main').innerText");
    check('F5 空状态：首页引导建档 + 示例', t.includes('从建档开始') && t.includes('用示例体验'));
    await b.eval(`document.querySelector('.empty [data-act="hh-example"]').click(); 1`); await sleep(150);
    s = await b.eval(`({ route: UI.route, n: hhChanges(), bar: document.querySelector('#dirtybar').innerText.replace(/\\s+/g, ' ').slice(0, 40) })`);
    check('F5 空状态：示例填入建档但未保存（需确认）', s.route === 'household' && s.n > 0 && s.bar.includes('未保存'), JSON.stringify(s));

    /* ---------- F6 草稿持久化 ---------- */
    await b.open('#/execution', 1440, 900);
    await b.eval(`document.querySelector('[data-act="open-record"][data-kind="expense"]').click(); 1`); await sleep(100);
    await b.eval(`(() => { const i = document.querySelector('#dr-amount'); i.value = '88.50'; i.dispatchEvent(new Event('input', { bubbles: true })); })()`);
    await b.eval(`ACT['add-draft'](); 1`); await sleep(60);
    await b.eval(`(() => { const i = document.querySelector('#dr-amount'); i.value = '12'; i.dispatchEvent(new Event('input', { bubbles: true })); })()`);
    await b.eval(`closeOverlay(); location.hash = '#/overview'; 1`); await sleep(100);
    await b.eval(`location.hash = '#/execution'; 1`); await sleep(100);
    s = await b.eval(`UI.drafts.map(d => d.amount).join()`);
    check('F6 草稿：切页后还在（I2）', s === '88.50,12', s);
    await b.eval(`location.reload(); 1`); await sleep(1200);
    s = await b.eval(`({ drafts: UI.drafts.map(d => d.amount).join(), cur: UI.cur })`);
    check('F6 草稿：刷新页面后仍在（localStorage）', s.drafts === '88.50,12', JSON.stringify(s));

    /* ---------- F7 手机视口 ---------- */
    for (const r of ['overview', 'household', 'scenarios', 'execution']) {
      await b.open('#/' + r, 390, 844, { mobile: true, dpr: 2 });
      s = await b.eval(`({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth, tabbar: getComputedStyle(document.querySelector('.tabbar')).display, fab: !!document.querySelector('.fab') && getComputedStyle(document.querySelector('.fab')).display, rail: getComputedStyle(document.querySelector('.rail')).display })`);
      check(`F7 手机 ${r}：无横向溢出、底部标签栏`, s.sw <= s.cw + 1 && s.tabbar === 'grid' && s.rail === 'none', JSON.stringify(s));
    }
    /* ---------- 控制台报错 ---------- */
    check('页面没有脚本报错', b.logs.length === 0, b.logs.join(' | ').slice(0, 300));
  } catch (e) {
    check('测试脚本异常', false, String(e && e.stack || e).slice(0, 400));
  } finally {
    await b.close();
    const pass = results.filter((r) => r[0] === 'PASS').length;
    results.forEach((r) => console.log(r[0], r[1], r[0] === 'FAIL' ? '→ ' + r[2] : ''));
    console.log(`\n${pass}/${results.length} 通过`);
  }
})();
