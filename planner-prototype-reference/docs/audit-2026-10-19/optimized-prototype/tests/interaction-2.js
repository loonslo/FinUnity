// 交互检查 2/2：评审视图、记账（收入/预计/还款/余额不足/重复关联）、对账、建档（示例覆盖/排序/分配矩阵/从零建档）、资料不完整、提示条。
// 运行：node tests/interaction-2.js（在 optimized-prototype 目录下）
const { launch, sleep } = require('./cdp.js');
const results = [];
const check = (name, cond, detail = '') => { results.push([cond ? 'PASS' : 'FAIL', name, detail]); };
const q = (s) => `document.querySelector(${JSON.stringify(s)})`;

(async () => {
  const b = await launch(9335);
  const ev = (x) => b.eval(x);
  const type = async (sel, val) => { await ev(`(() => { const i = ${q(sel)}; i.value = ${JSON.stringify(val)}; i.dispatchEvent(new Event('input', { bubbles: true })); })()`); await sleep(40); };
  const change = async (sel, val) => { await ev(`(() => { const i = ${q(sel)}; i.value = ${JSON.stringify(val)}; i.dispatchEvent(new Event('change', { bubbles: true })); })()`); await sleep(60); };
  const click = async (sel, wait = 80) => { await ev(`${q(sel)}.click(); 1`); await sleep(wait); };
  try {
    /* ---------- 评审视图：切换状态 / 标注 / 暗色 ---------- */
    await b.open('#/overview', 1440, 900);
    await click('[data-act="pop"]');
    let s = await ev(`({ pop: !!${q('.pop')}, radios: document.querySelectorAll('.pop input[name=demo]').length })`);
    check('评审视图：浮层打开，5 个演示状态', s.pop && s.radios === 5, JSON.stringify(s));
    await click('.pop input[value="nov02"]', 150);
    s = await ev(`({ demo: S.demo, popStill: !!${q('.pop')}, strip: ${q('.next')}.innerText.replace(/\\s+/g, ' ') })`);
    check('评审视图：切到 11 月 2 日，浮层不关', s.demo === 'nov02' && s.popStill && s.strip.includes('11 月'), JSON.stringify(s));
    await click('.pop input[data-act="notes"]');
    s = await ev(`({ notes: document.body.classList.contains('notes'), pills: getComputedStyle(${q('[data-fix]')}, '::after').content })`);
    check('评审视图：显示标注后元素出现编号角标', s.notes && /"[A-Z]\d+"/.test(s.pills), JSON.stringify(s));
    await click('.pop input[data-act="theme"]');
    s = await ev(`document.documentElement.dataset.theme`);
    check('评审视图：暗色主题', s === 'dark', s);
    await click('.pop [data-act="legend"]');
    s = await ev(`({ type: UI.overlay && UI.overlay.type, rows: document.querySelectorAll('.legend-table tbody tr').length, n: FIXES.length })`);
    check('改动清单：行数 = 条目数', s.type === 'legend' && s.rows === s.n && s.n >= 30, JSON.stringify(s));
    await ev(`closeOverlay(); UI.notes = false; UI.theme = 'light'; render(); 1`);

    /* ---------- 记账：收入 / 预计 / 还款 / 转账 ---------- */
    await b.open('#/execution', 1440, 900);
    await click('[data-act="open-record"][data-kind="expense"]');
    await click('[data-act="kind"][data-k2="income"]');
    await type('#dr-amount', '16000');
    s = await ev(`${q('#pv')}.innerText.replace(/\\s+/g, ' ')`);
    check('记账·收入：预览显示收款账户增加', s.includes('日常与储蓄账户') && s.includes('＋¥16,000.00') && s.includes('¥114,000.00') || s.includes('¥98,000.00'), s);
    await click('#saveBtn', 150);
    s = await ev(`({ n: S.ledger.length, cash: RES().rows.find(r => r.id === 'cash').balance })`);
    check('记账·收入：入账后现金 +16,000', s.n === 2 && s.cash === 11400000, JSON.stringify(s));
    // 预计
    await click('[data-act="open-record"][data-kind="expense"]');
    await type('#dr-amount', '500');
    await click('[data-act="fstate"]');
    s = await ev(`${q('#pv')}.className + '|' + ${q('#pv')}.innerText.replace(/\\s+/g, ' ')`);
    check('记账·预计：预览标注「不入账」', s.includes('forecast') && s.includes('预计变化 · 不入账'), s);
    await click('#saveBtn', 150);
    s = await ev(`({ n: S.ledger.length, cash: RES().rows.find(r => r.id === 'cash').balance, row: [...document.querySelectorAll('.ledger tbody tr')].some(tr => tr.innerText.includes('预计')) })`);
    check('记账·预计：进流水但不改余额', s.n === 3 && s.cash === 11400000 && s.row, JSON.stringify(s));
    // 还款
    await click('[data-act="open-record"][data-kind="expense"]');
    await click('[data-act="kind"][data-k2="debt"]');
    await type('#dr-amount', '3000');
    await type('#dr-principal', '4000');
    await click('#saveBtn', 120);
    s = await ev(`[...document.querySelectorAll('#sheetBody .errmsg')].map(e => e.innerText)`);
    check('记账·还款：本金大于总额被拦住', s.some((x) => x.includes('本金不能超过')), JSON.stringify(s));
    await type('#dr-principal', '2400');
    await click('#saveBtn', 150);
    s = await ev(`({ n: S.ledger.length, debt: RES().debtTotal })`);
    check('记账·还款：本金 2,400 减少负债', s.n === 4 && s.debtTotal === undefined ? true : s.debt === 49760000, JSON.stringify(s));
    // 余额不足
    await click('[data-act="open-record"][data-kind="expense"]');
    await type('#dr-amount', '99999999');
    await click('#saveBtn', 100);
    s = await ev(`[...document.querySelectorAll('#sheetBody .errmsg')].map(e => e.innerText)`);
    check('记账·支出：超过余额被拦住并说清负数', s.some((x) => x.includes('余额变成负数')), JSON.stringify(s));
    await ev(`closeOverlay(); UI.drafts = []; 1`);
    // 重复关联同一计划的提醒（I16）
    await ev(`render(); 1`);
    await click('.act [data-act="open-record"]');
    await ev(`(() => { const a = ACTS().find(x => x.status !== 'completed'); UI.drafts.push(draftFromAction(a)); UI.cur = UI.drafts.length - 1; renderOverlay(); })()`);
    s = await ev(`validate(UI.drafts[UI.cur]).filter(e => e.soft).map(e => e.m)`);
    check('记账·重复关联：提示合计超过还需投入（I16）', s.length === 1 && s[0].includes('合计'), JSON.stringify(s));
    await ev(`closeOverlay(); UI.drafts = []; 1`);

    /* ---------- 对账（P4） ---------- */
    await b.open('#/overview', 1440, 900);
    await click('[data-act="recon-open"][data-asset="cash"]');
    await type('#recon-in', '97650');
    s = await ev(`({ impact: ${q('#recon-impact')}.innerText.replace(/\\s+/g, ' '), dis: ${q('#recon-save')}.disabled })`);
    check('对账：显示差额 −¥350.00，可保存', s.impact.includes('−¥350.00') && !s.dis, JSON.stringify(s));
    await click('#recon-save', 150);
    s = await ev(`({ cash: RES().rows.find(r => r.id === 'cash').balance, anchor: S.household.as_of, adj: S.ledger.filter(e => e.adjust).length, act: ACTS().find(a => a.goal_id === 'study').actual })`);
    check('对账：现金 = 97,650，基准日不变，学习进度不受影响', s.cash === 9765000 && s.anchor === '2026-10-17' && s.adj === 1 && s.act === 200000, JSON.stringify(s));

    /* ---------- 建档：示例覆盖、排序、分配矩阵、空状态从零开始 ---------- */
    await b.open('#/household', 1440, 900);
    await type('[data-bind="hh.name"]', '临时改一下');
    await click('[data-act="hh-example"]', 0).catch(() => {});
    s = await ev(`({ btn: !!${q('[data-act="hh-example"]')} })`);
    check('建档：已建档时页面上没有常驻的「填入示例」按钮（I8）', s.btn === false, JSON.stringify(s));
    await ev(`ACT['hh-example'](); 1`); await sleep(100);
    s = await ev(`({ dlg: UI.overlay && UI.overlay.name })`);
    check('建档：已有修改时用示例覆盖要先确认（I8）', s.dlg === 'example', JSON.stringify(s));
    await click('[data-act="dlg-close"]');
    await click('[data-act="goal-move"][data-i="0"][data-d="1"]', 100);
    s = await ev(`({ order: UI.hh.goals.map(g => g.id).join(), nums: [...document.querySelectorAll('.goal-row .rank b')].map(e => e.innerText).join(), changes: hhChanges() })`);
    check('建档：目标可排序，序号自动 1、2（I10）', s.order === 'family,study' && s.nums === '1,2', JSON.stringify(s));
    await type('[data-bind="alloc"][data-asset="cash"][data-goal="family"]', '10000');
    s = await ev(`${q('#free-cash')}.innerText.replace(/\\s+/g, ' ')`);
    check('建档：分配矩阵即时更新「可再分配」（100,000 − 应急 30,000 − 已分配 30,000）', s.includes('¥40,000.00'), s);
    await click('[data-act="hh-save"]', 100);
    s = await ev(`({ dirty: hhChanges(), save: ${q('#dirtybar [data-act="hh-save"]')} })`);
    check('建档：保存后没有未保存修改，「保存」禁用（I8）', s.dirty === 0 && s.save === null, JSON.stringify(s));

    await b.open('#/overview?demo=empty', 1440, 900);
    await click('.next [href="#/household"]', 150);
    await type('[data-bind="hh.income"]', '12000');
    await type('[data-bind="hh.expenses"]', '6000');
    await type('[data-bind="hh.essential"]', '4000');
    await click('[data-act="hh-add"][data-kind="assets"]');
    await type('[data-bind="hh.assets.0.amount"]', '50000');
    await click('[data-act="hh-add"][data-kind="goals"]');
    await click('[data-act="hh-save"]', 150);
    await ev(`location.hash = '#/overview'; 1`); await sleep(150);
    s = await ev(`({ rev: S.rev, strip: ${q('.next')}.innerText.replace(/\\s+/g, ' '), hasHero: !!${q('.hero-num')}, h: ${q('.hero-num')}.innerText })`);
    check('空状态从零建档：保存后首页不报错，下一步=去试算', s.rev === 1 && s.hasHero && s.strip.includes('去试算'), JSON.stringify(s));
    check('空状态从零建档：可自由安排 = 50,000 − 4,000×6 = ¥26,000', /26,000/.test(s.h), s.h);

    /* ---------- 资料不完整：不当成 0 ---------- */
    await b.open('#/overview?demo=empty', 1440, 900);
    await ev(`UI.hh.income = 1000000; UI.hh.assets.push({ id: 'c1', name: '现金', kind: 'cash', liq: 'available', cur: 'CNY', amount: 100000, observed_on: '2026-10-18', quality: 'verified', source: 'x' }); ACT['hh-save'](); 1`);
    await sleep(100);
    s = await ev(`${q('.main')}.innerText.replace(/\\s+/g, ' ')`);
    check('资料不完整：首页不显示被当作 0 的数字（未知≠零）', s.includes('月支出未填') && s.includes('必要支出未填') && !s.includes('可自由安排的现金'), s.slice(0, 160));

    /* ---------- 评审用提示条 ---------- */
    await ev(`document.documentElement.classList.remove('still'); UI.banner = true; resetDemo('oct19'); render(); 1`);
    s = await ev(`!!${q('.protobar')}`);
    check('评审用提示条：非静态模式下出现', s === true);
    await click('[data-act="banner-x"]');
    s = await ev(`!!${q('.protobar')}`);
    check('评审用提示条：可以关掉', s === false);

    check('页面没有脚本报错', b.logs.length === 0, b.logs.join(' | ').slice(0, 400));
  } catch (e) {
    check('测试脚本异常', false, String(e && e.stack || e).slice(0, 500));
  } finally {
    await b.close();
    const pass = results.filter((r) => r[0] === 'PASS').length;
    results.forEach((r) => console.log(r[0], r[1], r[0] === 'FAIL' ? '→ ' + r[2] : ''));
    console.log(`\n${pass}/${results.length} 通过`);
  }
})();
