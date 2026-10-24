// 核对 JS 版测算引擎与 8011 后端的数字：node test_engine.js
// 期望（默认示例）：净资产 820,000、现金 98,000、应急 30,000、可自由安排 48,000、学习目标到期测算 46,000 / 缺口 4,000、家庭目标缺口 0。
const E = require('./engine.js');
const D = require('./data.js');
global.shiftDay = E.shiftDay;
const s = D.loadDemo('oct19');
const r = E.calc(s.household, s.ledger, {}, s.today);
const y = (c) => E.fmtYuan(c);
console.log('净资产', y(r.netWorth), '| 现金', y(r.cash), '| 应急', y(r.reserve), '| 可自由安排', y(r.free), '| 已分配现金', y(r.cashAllocated), '| 月结余', y(r.surplus), '| 剩余额度', y(r.remaining));
for (const g of r.goals) console.log(g.name, '已分配', y(g.funded), '每月', y(g.affordable), '月数', g.months, '到期测算', y(g.projected), '缺口', y(g.shortfall), '需要每月', y(g.needed), 'floor', y(g.floor), g.floorBinds);
const lv = E.levers(r.goals[0], r, s.household, {}, s.today);
console.log('杠杆 学习进修:', JSON.stringify(lv));
const plan = s.plans[0];
console.log('actions', JSON.stringify(E.actionsFor(plan, s.ledger, '2026-10', s.today).map(a => [a.goal_id, a.amount, a.actual, a.status])));
// 试算：定投下限
const r2 = E.calc(s.household, s.ledger, { monthly: { family: 100000 } }, s.today);
console.log('家庭 每月 1000 → 实际', y(r2.goals[1].affordable), 'floorBinds', r2.goals[1].floorBinds);
// 11-02
const n = D.loadDemo('nov02');
const rn = E.calc(n.household, n.ledger, {}, n.today);
console.log('11-02 学习:', rn.goals[0].months, y(rn.goals[0].projected), y(rn.goals[0].shortfall));
// 收益 3% 年化
const r3 = E.calc(s.household, s.ledger, { ret: 3 }, s.today);
console.log('年化 3%:', r3.goals.map(g => [g.name, y(g.projected), y(g.shortfall)]));
