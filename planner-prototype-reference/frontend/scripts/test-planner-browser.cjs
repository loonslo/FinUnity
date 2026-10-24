/* Real React UI + real API; requires tests/planner_ui_server.py on 127.0.0.1:8012. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { launch, sleep } = require("./planner-cdp.cjs");
const ROOT = "http://127.0.0.1:8012";
const OUT = path.resolve(
  __dirname,
  "../../docs/aesthetic-validation-2026-10-21/images",
);
const checks = [];
let api;
async function http(url, data) {
  const response = await fetch(
    ROOT + url,
    data === undefined
      ? undefined
      : {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify(data),
        },
  );
  const body = await response.json();
  assert(response.ok, `${url}: ${JSON.stringify(body)}`);
  return body;
}
async function wait(expr) {
  for (let i = 0; i < 80; i++) {
    if (await api.eval(expr)) return;
    await sleep(100);
  }
  throw new Error("Timed out: " + expr);
}
async function check(name, expr) {
  assert(await api.eval(expr), name);
  checks.push(name);
  console.log("PASS " + name);
}
async function click(text, scope = "document") {
  if (text === "作废")
    await api.eval(
      `document.querySelector('.row-menu')?.setAttribute('open','');1`,
    );
  await api.eval(
    `(()=>{const e=[...${scope}.querySelectorAll('button,a.btn')].find(e=>e.getClientRects().length&&e.textContent.trim()===${JSON.stringify(text)});if(!e)throw new Error('Missing button '+${JSON.stringify(text)});if(e.disabled)throw new Error('Disabled '+${JSON.stringify(text)});e.click();return true;})()`,
  );
  await sleep(150);
}
async function input(title, value, scope = "document") {
  if (title === "金额变化类型") {
    await api.eval(
      `[...document.querySelector('[aria-label="金额变化类型"]').querySelectorAll('button')][${JSON.stringify(["income", "expense", "transfer", "debt_payment"])}.indexOf(${JSON.stringify(value)})].click();1`,
    );
    return;
  }
  await api.eval(
    `(()=>{const label=[...${scope}.querySelectorAll('label.field')].find(e=>e.querySelector('.lab')?.textContent===${JSON.stringify(title)});const el=label?.querySelector('input,select,textarea');if(!el)throw new Error('Missing field '+${JSON.stringify(title)});const proto=el instanceof HTMLSelectElement?HTMLSelectElement.prototype:el instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event(el instanceof HTMLSelectElement?'change':'input',{bubbles:true}));return true;})()`,
  );
  await sleep(100);
}
async function nav(route) {
  await api.eval(`location.hash=${JSON.stringify("/" + route)};1`);
  await sleep(200);
}
async function screenshot(name, full = false) {
  await api.eval(
    'document.documentElement.classList.add("still");document.querySelector(".toast button")?.click();document.activeElement?.blur();1',
  );
  await sleep(120);
  await api.shot(path.join(OUT, name + ".png"), { full });
}
async function theme(value) {
  await api.eval(
    `(()=>{const el=document.querySelector('[aria-label="外观主题"]');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event('change',{bubbles:true}));return true;})()`,
  );
  await sleep(100);
}
async function navigateFresh(route = "overview") {
  await api.send("Page.navigate", {
    url: ROOT + "/?ui=" + Date.now() + "#/" + route,
  });
  await wait(`!!document.querySelector('.topbar')`);
  await sleep(200);
}
async function clearDrafts() {
  await api.eval(
    `localStorage.removeItem('finunity-household-draft-v1');localStorage.removeItem('finunity-record-draft-v1');1`,
  );
  await navigateFresh();
}
async function date(value) {
  await http("/__test__/clock", { date: value });
  await api.eval(
    `window.__testDate=${JSON.stringify(value)};document.querySelector('[aria-label="关闭抽屉"]')?.click();1`,
  );
  await clickRefresh();
}
async function clickRefresh() {
  await api.eval(`window.dispatchEvent(new Event('focus'));1`);
  await sleep(500);
}

(async () => {
  // This endpoint only exists in the dedicated, temporary-database test process.
  await http("/__test__/reset", {});
  fs.mkdirSync(OUT, { recursive: true });
  api = await launch(9347);
  try {
    await api.send("Page.addScriptToEvaluateOnNewDocument", {
      source: `(()=>{const RealDate=Date;window.__testDate='2026-10-19';window.Date=class extends RealDate{constructor(...args){if(args.length)super(...args);else super(window.__testDate+'T04:00:00Z');}static now(){return new RealDate(window.__testDate+'T04:00:00Z').getTime();}};})()`,
    });
    await api.viewport(1440, 900);
    await navigateFresh();
    await clearDrafts();
    await check(
      "empty state explains four steps",
      `document.querySelector('.empty').textContent.includes('从建档开始')`,
    );
    await screenshot("10-empty", true);
    await check(
      "empty household is unstarted with setup estimate",
      `document.querySelector('.main-nav a[href="#/household"] small').textContent==='未开始'&&document.querySelector('.eyebrow').textContent.includes('5 分钟')`,
    );
    await click("开始建档");
    await check(
      "new household defaults to ended day",
      `document.querySelector('input[type=date]').value==='2026-10-18'&&document.querySelector('input[type=date]').max==='2026-10-18'`,
    );
    await click("用示例体验");
    await click("填入草稿");
    await check(
      "setup starts with income and hides account sections",
      `!document.querySelector('.h-sec').hidden&&document.querySelectorAll('.h-sec')[1].hidden`,
    );
    await click("下一步");
    await screenshot("21-setup-accounts");
    await check(
      "setup second step shows accounts and debts",
      `!document.querySelectorAll('.h-sec')[1].hidden&&!document.querySelectorAll('.h-sec')[2].hidden&&document.querySelector('.h-sec').hidden`,
    );
    await click("下一步");
    await check(
      "completeness checks are at final setup step",
      `document.querySelector('.h-sec.card.pad:not([hidden])')?.textContent.includes('保存前核对')`,
    );
    await check(
      "same-page next step hidden in setup",
      `!document.querySelector('.next')`,
    );
    await click("保存建档");
    await wait(
      `document.querySelector('.dirtybar')?.textContent.includes('没有修改')`,
    );
    await check(
      "save disabled without modifications",
      `[...document.querySelectorAll('button')].find(x=>x.textContent==='保存建档').disabled`,
    );
    await nav("scenarios");
    await wait(
      `document.querySelector('.cmp')?.textContent.includes('无')||document.querySelector('.cmp')?.textContent.includes('4,000')`,
    );
    await wait(
      `!![...document.querySelectorAll('button')].find(x=>x.textContent==='用试算 A 生成方案草案'&&!x.disabled)`,
    );
    await click("用试算 A 生成方案草案");
    await wait(`!!document.querySelector('.sheet .draft')`);
    await check(
      "shortfall acceptance explicit in proposal drawer",
      `!![...document.querySelectorAll('.sheet button')].find(x=>x.textContent.trim()==='接受列示缺口并采用方案')&&!document.querySelector('.draft input[type=checkbox]')`,
    );
    assert.equal(
      (await http("/api/v1/planner/plans")).filter((p) => p.status === "active")
        .length,
      0,
    );
    checks.push("draft does not confirm until explicit acceptance");
    await screenshot("15-proposal-sheet");
    await api.eval("history.back();1");
    await sleep(200);
    await check(
      "browser back unmounts proposal drawer and restores page scrolling",
      `location.hash==='#/household'&&!document.querySelector('[role="dialog"]')&&document.body.style.overflow!=='hidden'`,
    );
    await nav("scenarios");
    await wait(`!!document.querySelector('.sheet .draft')`);
    await click("接受列示缺口并采用方案");
    await wait(
      `document.querySelector('.page-head')&& !document.querySelector('.draft')`,
    );
    await nav("overview");
    await api.eval(
      `(()=>{const a=[...document.querySelectorAll('.compact-action')].find(x=>x.querySelector('b').textContent==='学习进修');a.querySelector('button').click();return true;})()`,
    );
    await wait(`!!document.querySelector('.sheet')`);
    await check(
      "plan contribution prefilled",
      `[...document.querySelectorAll('.sheet label')].find(x=>x.querySelector('.lab')?.textContent==='转出金额').querySelector('input').value==='2000.00'`,
    );
    await screenshot("05-record-sheet");
    await click("保存 1 条记录");
    await wait(`!document.querySelector('.sheet')`);
    await check(
      "cash is free cash and invested facts update progress",
      `document.querySelector('.hero-num').textContent.includes('48,000')&&[...document.querySelectorAll('.compact-action')].find(x=>x.querySelector('b').textContent==='学习进修').textContent.includes('已完成')`,
    );
    await check(
      "ordinary records do not trigger household warning",
      `!document.querySelector('.page-head .meta').textContent.includes('建档已变更')`,
    );
    await check(
      "three adjustment levers use backend suggestions",
      `document.querySelectorAll('.lever-grid .lever').length===3`,
    );
    await screenshot("01-overview", true);
    await api.eval(`document.querySelector('.hero .nw details').open=true;1`);
    await nav("household");
    await input("名称", "未保存的家庭名");
    await check(
      "household draft dirty",
      `document.querySelector('.dirtybar').textContent.includes('未保存')`,
    );
    await screenshot("02-household", true);
    await check(
      "money grouping changes presentation only",
      `[...document.querySelectorAll('label.field')].find(x=>x.querySelector('.lab')?.textContent==='月收入（税后）').querySelector('input').value==='16,000'&&JSON.parse(localStorage.getItem('finunity-household-draft-v1')).value.income==='16000'`,
    );
    await clickRefresh();
    await check(
      "refresh preserves household draft",
      `document.querySelector('label.field input').value==='未保存的家庭名'`,
    );
    await nav("scenarios");
    await input("每月额外收入", "200");
    await sleep(650);
    await nav("household");
    await check(
      "calculation and navigation preserve household draft",
      `document.querySelector('label.field input').value==='未保存的家庭名'`,
    );
    await navigateFresh("household");
    await check(
      "browser reload restores household draft",
      `document.querySelector('label.field input').value==='未保存的家庭名'`,
    );
    await click("放弃修改");
    await click("放弃修改", 'document.querySelector(".dialog")');
    await check(
      "discard restores saved household",
      `document.querySelector('label.field input').value==='我的家庭财务'`,
    );
    await api.eval(
      `document.querySelector('[aria-label="上移家庭长期储蓄"]').click();1`,
    );
    await check(
      "goal ordering writes consecutive priorities",
      `JSON.parse(localStorage.getItem('finunity-household-draft-v1')).value.goals.every((g,i)=>g.priority===i+1)&&JSON.parse(localStorage.getItem('finunity-household-draft-v1')).value.goals[0].id==='family'`,
    );
    await click("放弃修改");
    await click("放弃修改", 'document.querySelector(".dialog")');
    await nav("scenarios");
    await api.eval("history.back();1");
    await sleep(200);
    await check(
      "browser back restores hash navigation",
      `document.querySelector('.rail a[aria-current=page]').getAttribute('href')==='#/household'`,
    );
    await nav("scenarios");
    await input("年化收益假设", "3");
    await check(
      "percentages accept 3 as 3 percent",
      `[...document.querySelectorAll('label.field')].find(x=>x.querySelector('.lab')?.textContent==='年化收益假设').querySelector('input').value==='3'`,
    );
    await input("年化收益假设", "3.");
    await check(
      "percentage input keeps decimal typing",
      `[...document.querySelectorAll('label.field')].find(x=>x.querySelector('.lab')?.textContent==='年化收益假设').querySelector('input').value==='3.'`,
    );
    await input("年化收益假设", "3");
    await input("家庭长期储蓄", "1000");
    await input("年通胀假设", "31");
    await check(
      "local Chinese errors expose floor and bounds",
      `document.querySelector('.scen-controls').textContent.includes('下限')&&document.querySelector('.scen-controls').textContent.includes('30%')`,
    );
    await screenshot("16-scenario-hints");
    await input("家庭长期储蓄", "3000");
    await input("年通胀假设", "0");
    await input("年化收益假设", "0");
    await click("＋ 对比");
    await input("每月额外收入", "500");
    await sleep(650);
    await check(
      "A B comparisons show per-goal differences",
      `document.querySelectorAll('.cmp thead th').length===4&&document.querySelector('.cmp').textContent.includes('试算 B')`,
    );
    await screenshot("03-scenarios", true);
    await nav("execution");
    await click("生成本月复盘");
    await wait(`!!document.querySelector('.review .tiles')`);
    await check(
      "unrecorded actuals are not zero",
      `[...document.querySelectorAll('.review .tile b')].every(x=>x.textContent==='未记录')`,
    );
    await check(
      "partial review is in progress",
      `document.querySelector('.review').textContent.includes('进行中')`,
    );
    await screenshot("04-execution", true);
    await click("＋ 记一笔");
    await input("减少金额", "123");
    await click("稍后再记");
    await nav("overview");
    await nav("execution");
    await click("＋ 记一笔");
    await check(
      "record draft survives close and navigation",
      `[...document.querySelectorAll('.sheet label.field')].find(x=>x.querySelector('.lab')?.textContent==='减少金额').querySelector('input').value==='123'`,
    );
    await click("稍后再记");
    await navigateFresh("execution");
    await click("＋ 记一笔");
    await check(
      "record draft survives browser reload",
      `[...document.querySelectorAll('.sheet label.field')].find(x=>x.querySelector('.lab')?.textContent==='减少金额').querySelector('input').value==='123'`,
    );
    await input("减少金额", "10");
    await click("＋ 再加一条");
    await input("金额变化类型", "expense");
    await input("减少金额", "99999999");
    const before = await http("/api/v1/planner/state");
    await click("保存 2 条记录");
    await wait(
      `document.querySelector('.sheet .summary-err')?.textContent.includes('第 2 条')`,
    );
    await check(
      "batch failure selects offending draft",
      `document.querySelector('.draft-chip[aria-current=true]').textContent.includes('第 2 条')`,
    );
    assert.equal(
      (await http("/api/v1/planner/state")).revision,
      before.revision,
    );
    await check(
      "batch error focuses amount field",
      `document.activeElement?.matches("input.in.err")`,
    );
    checks.push("batch rolls back all rows");
    await check(
      "server error is not repeated at top and field",
      `document.querySelectorAll('.sheet .errmsg').length===1&&!document.querySelector('.sheet .summary-err').textContent.includes('余额不足')`,
    );
    await check(
      "negative preview has explicit insufficient balance warning",
      `document.querySelector('.negative-balance')?.textContent.includes('余额不足')`,
    );
    await screenshot("06-record-errors");
    await click("稍后再记");
    await clearDrafts();
    await nav("execution");
    await click("作废");
    await screenshot("07-void-dialog");
    const beforeVoid = await http("/api/v1/planner/state");
    await click("取消");
    assert.equal(
      (await http("/api/v1/planner/state")).revision,
      beforeVoid.revision,
    );
    checks.push("cancel void does not mutate");
    await nav("overview");
    await api.eval(`document.querySelector('.prov details').open=true;1`);
    await click("对账");
    await input("银行 / 机构实际余额（原币）", "97500");
    await screenshot("14-recon");
    await click("确认差额调整");
    await wait(`!document.querySelector('.dialog')`);
    await check(
      "reconciliation changes balance and leaves contribution progress",
      `document.querySelector('.hero-num').textContent.includes('47,500')&&[...document.querySelectorAll('.compact-action')].find(x=>x.querySelector('b').textContent==='学习进修').textContent.includes('已完成')`,
    );
    await nav("execution");
    await click("更新本月复盘");
    await wait(
      `document.querySelector('.review')?.textContent.includes('未记录')`,
    );
    await check(
      "reconciliation is excluded from income and expenses",
      `[...document.querySelectorAll('.review .tile b')].every(x=>x.textContent==='未记录')`,
    );
    await click("作废");
    await click("确认作废");
    await wait(`!document.querySelector('.dialog')`);
    await nav("overview");
    await check(
      "void reconciliation restores free cash",
      `document.querySelector('.hero-num').textContent.includes('48,000')`,
    );
    await api.eval(`document.querySelector('.topbar .iconbtn').click();1`);
    await screenshot("18-assistant");
    await check(
      "unconfigured assistant has no unread dot and disabled input is distinct",
      `!document.querySelector('.topbar .dotmark')&&getComputedStyle(document.querySelector('.sheet textarea:disabled')).opacity==='0.65'`,
    );
    await check(
      "unconfigured assistant explains setup and UTC boundary",
      `document.querySelector('.sheet').textContent.includes('08:00')&&document.querySelector('.sheet').textContent.includes('OPENROUTER_MODEL')`,
    );
    await click("稍后再记").catch(() => {});
    await api.eval(
      `document.querySelector('[aria-label="关闭抽屉"]').click();1`,
    );
    await nav("execution");
    await api.eval(
      `(()=>{const row=[...document.querySelectorAll('.ledger tbody tr')].find(x=>x.textContent.includes('转账'));row.querySelector('button').click();return true;})()`,
    );
    await click("作废并重新录入");
    await wait(`!!document.querySelector('.sheet')`);
    await check(
      "void and reenter restores original contribution in draft",
      `[...document.querySelectorAll('.sheet label.field')].find(x=>x.querySelector('.lab')?.textContent==='转出金额').querySelector('input').value==='2000.00'`,
    );
    await click("保存 1 条记录");
    await wait(`!document.querySelector('.sheet')`);
    await nav("overview");
    await check(
      "reentered contribution restores progress without double counting",
      `document.querySelector('.hero-num').textContent.includes('48,000')&&[...document.querySelectorAll('.compact-action')].find(x=>x.querySelector('b').textContent==='学习进修').textContent.includes('已完成')`,
    );
    await nav("scenarios");
    await sleep(650);
    await wait(
      `!![...document.querySelectorAll('button')].find(x=>x.textContent==='用试算 A 生成方案草案'&&!x.disabled)`,
    );
    await click("用试算 A 生成方案草案");
    await wait(`!!document.querySelector('.sheet .draft')`);
    await check(
      "new proposal again requires explicit acceptance",
      `!!document.querySelector('.sheet .draft')&&(document.querySelector('.draft').textContent.includes('采用方案')||document.querySelector('.draft').textContent.includes('采用这份方案'))`,
    );
    await date("2026-10-20");
    await check(
      "date expiration disables confirmation",
      `document.querySelector('.draft').textContent.includes('已过期')&&!document.querySelector('.draft input[type=checkbox]')`,
    );
    await screenshot("09-stale-draft");
    await click("放弃草案");
    await wait(`!document.querySelector('.draft')`);
    await date("2026-11-02");
    await nav("execution");
    await check(
      "month rollover offers renewal and previous closing",
      `document.querySelector('.renew').textContent.includes('生成本月计划')&&document.querySelector('main').textContent.includes('补做 2026-10')`,
    );
    await screenshot("08-new-month", true);
    await check(
      "renewal is inside monthly plans; closing review is in review section",
      `document.querySelector('.renew').parentElement.querySelector('h2').textContent.includes('本月投入计划')&&[...document.querySelectorAll('.renew')].find(x=>x.textContent.includes('补做')).parentElement.querySelector('h2').textContent.includes('本月复盘')&&!document.querySelector('.next')`,
    );
    await click(
      "接受现有方案与缺口，生成本月计划",
      'document.querySelector(".renew")',
    );
    await wait(`document.querySelectorAll('article.act').length===2`);
    await check(
      "explicit renewal produces current actions",
      `[...document.querySelectorAll('article.act')].every(a=>a.textContent.includes('2026-11'))`,
    );
    await date("2026-10-19");
    await nav("overview");
    await theme("dark");
    await screenshot("11-dark", true);
    await theme("light");
    await api.viewport(390, 844, true);
    await nav("overview");
    await screenshot("12-mobile-overview", true);
    for (const r of ["overview", "household", "scenarios", "execution"]) {
      await nav(r);
      await check(
        `mobile ${r} has no horizontal page overflow`,
        `document.documentElement.scrollWidth<=window.innerWidth`,
      );
    }
    await nav("overview");
    await api.eval(`document.querySelector('.fab').click();1`);
    await screenshot("13-mobile-sheet");
    await check(
      "mobile sheet preview stays above footer",
      `document.querySelector('.sheet-pv').getBoundingClientRect().bottom<=document.querySelector('.sheet-foot').getBoundingClientRect().top+1`,
    );
    await check(
      "mobile amount is visible and autofocus target",
      `!!document.querySelector('[data-autofocus]')&&document.querySelector('[data-autofocus]').getBoundingClientRect().bottom<document.querySelector('.sheet-pv').getBoundingClientRect().top`,
    );
    await check(
      "record type is four option buttons",
      `document.querySelectorAll('[aria-label="金额变化类型"] button').length===4`,
    );
    await check(
      "small account sets use selectable cards",
      `document.querySelectorAll('.account-options button').length>0&&!document.querySelector('.account-options select')`,
    );
    await click("稍后再记");
    await theme("dark");
    await check(
      "theme available on mobile",
      `document.documentElement.dataset.theme==='dark'`,
    );
    await screenshot("19-mobile-dark");
    await theme("system");
    await check(
      "theme can return to system",
      `document.querySelector('[aria-label="外观主题"]').value==='system'`,
    );
    await api.viewport(1440, 900);
    await nav("overview");
    await check(
      "overview provenance collapsed",
      `!document.querySelector('.prov details').open`,
    );
    await check(
      "net worth currency shares large money styling",
      `getComputedStyle(document.querySelector('.nw-num .cur')).verticalAlign!=='baseline'`,
    );
    await api.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      key: "2",
      code: "Digit2",
    });
    await wait(
      `location.hash==='#/execution'&&[...document.querySelectorAll('h1')].find(x=>x.getClientRects().length)?.textContent==='本月'`,
    );
    await check(
      "keyboard route uses navigation order",
      `[...document.querySelectorAll('h1')].find(x=>x.getClientRects().length)?.textContent==='本月'`,
    );
    await api.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      key: "n",
      code: "KeyN",
    });
    await wait(`!!document.querySelector('.sheet')`);
    await check(
      "N opens amount first and focuses amount",
      `document.activeElement===document.querySelector('[data-autofocus]')`,
    );
    await api.send("Input.dispatchKeyEvent", {
      type: "keyDown",
      key: "1",
      code: "Digit1",
    });
    await check(
      "shortcuts ignored while editing",
      `location.hash==='#/execution'`,
    );
    await input("减少金额", "1");
    const countBefore = (await http("/api/v1/planner/occurrences")).length;
    await click("保存并再记一条");
    await wait(
      `document.querySelector('.sheet')&&document.querySelector('[data-autofocus]')?.value===''`,
    );
    assert.equal(
      (await http("/api/v1/planner/occurrences")).length,
      countBefore + 1,
    );
    await check(
      "save and new clears committed batch and keeps drawer",
      `document.querySelectorAll('.draft-chip').length===1&&document.activeElement===document.querySelector('[data-autofocus]')`,
    );
    await click("稍后再记");
    await nav("execution");
    await check(
      "six month history leaves missing months unknown",
      `document.querySelectorAll('.trend-month').length===6&&document.querySelector('.trend').textContent.includes('未记录')`,
    );
    await date("2026-11-02");
    await wait(
      `document.querySelector('[aria-label="流水月份"]')?.value==='2026-11'`,
    );
    await check(
      "month filter excludes previous month ledger rows",
      `[...document.querySelectorAll('.ledger tbody tr')].every(x=>!x.textContent.includes('2026-10-19'))`,
    );
    await api.viewport(390, 844, true);
    await api.eval(
      `const select=document.querySelector('[aria-label="流水月份"]');select.value='2026-10';select.dispatchEvent(new Event('change',{bubbles:true}));1`,
    );
    await screenshot("20-mobile-ledger", true);
    await check(
      "mobile trend initially shows the latest months",
      `(()=>{const chart=document.querySelector('.trend-chart');return chart.scrollLeft>0&&Math.abs(chart.scrollWidth-chart.clientWidth-chart.scrollLeft)<2})()`,
    );
    await check(
      "mobile financial amounts remain on one line",
      `[...document.querySelectorAll('.act .figs b')].every(x=>x.getBoundingClientRect().height<30)&&document.documentElement.scrollWidth<=innerWidth`,
    );
    await check(
      "mobile ledger uses cards within viewport",
      `getComputedStyle(document.querySelector('.ledger tr')).display==='grid'&&document.querySelector('.ledger').getBoundingClientRect().width<390`,
    );
    await api.eval(`document.documentElement.classList.remove('still');1`);
    await check(
      "page motion wired to live sections",
      `getComputedStyle([...document.querySelectorAll('.rv')].find(x=>x.getClientRects().length)).animationName==='rise'`,
    );
    await api.send("Emulation.setEmulatedMedia", {
      features: [{ name: "prefers-reduced-motion", value: "reduce" }],
    });
    await check(
      "reduced motion respected",
      `matchMedia('(prefers-reduced-motion: reduce)').matches&&parseFloat(getComputedStyle([...document.querySelectorAll('.rv')].find(x=>x.getClientRects().length)).animationDuration)<=0.001`,
    );
    assert.equal(api.logs.length, 0, JSON.stringify(api.logs));
    checks.push("no browser exceptions or console errors");
    fs.writeFileSync(
      path.join(OUT, "../browser-checks.json"),
      JSON.stringify({ date: "2026-10-21", checks }, null, 2),
    );
    console.log(`TOTAL ${checks.length} browser checks passed`);
  } finally {
    if (api) await api.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
