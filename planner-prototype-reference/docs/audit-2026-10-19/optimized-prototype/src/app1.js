/* ============================================================
   原型主程序 · 第 1 段：工具、状态、派生数据、通用部件
   ============================================================ */
'use strict';

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const clone = (o) => JSON.parse(JSON.stringify(o));
const uid = () => Math.random().toString(36).slice(2, 10);
const Y = (c, o) => fmtYuan(c, o);
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const dow = (iso) => WEEK[new Date(iso + 'T12:00:00Z').getUTCDay()];
const md = (iso) => iso.slice(5);
const daysIn = (month) => new Date(Date.UTC(+month.slice(0, 4), +month.slice(5, 7), 0)).getUTCDate();
const monthLabel = (m) => `${m.slice(0, 4)} 年 ${+m.slice(5, 7)} 月`;
const getPath = (o, p) => p.split('.').reduce((a, k) => (a == null ? a : a[k]), o);
function setPath(o, p, v) {
  const ks = p.split('.');
  const last = ks.pop();
  const t = ks.reduce((a, k) => a[k], o);
  t[last] = v;
}
const centsInput = (c) => (c == null ? '' : (c / 100).toFixed(2).replace(/\.00$/, ''));

/* 带标注的包装：打开「显示评审标注」后，这些元素会出现 ID 角标 */
const fix = (id) => `data-fix="${id}"`;

/* ---------- 图标 ---------- */
const P = {
  home: '<path d="M3.5 11 12 4l8.5 7M6 9.5V20h12V9.5"/>',
  book: '<path d="M5 4.5h11a3 3 0 0 1 3 3V20H8a3 3 0 0 1-3-3V4.5zM5 17a3 3 0 0 1 3-3h11"/>',
  sliders: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9M13 4.5v5M7 14.5v5"/>',
  check: '<path d="M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0zM8 12.4l2.7 2.7 5.3-5.6"/>',
  spark: '<path d="M12 3.5l1.9 5.4 5.4 1.9-5.4 1.9L12 18.1l-1.9-5.4-5.4-1.9 5.4-1.9L12 3.5z"/>',
  lock: '<path d="M6.5 11h11V20h-11zM8.7 11V8.2a3.3 3.3 0 0 1 6.6 0V11"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  chev: '<path d="M9.5 6l6 6-6 6"/>',
  arrow: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  info: '<path d="M12 11v5.5M12 7.6v.1M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"/>',
  alert: '<path d="M12 4 21 19.5H3L12 4zM12 10v4.2M12 16.8v.1"/>',
  moon: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4"/>',
  refresh: '<path d="M20 11a8 8 0 0 0-14.3-3.6M4.5 4v4h4M4 13a8 8 0 0 0 14.3 3.6M19.5 20v-4h-4"/>',
  cal: '<path d="M5 6.5h14V20H5zM5 10.5h14M9 4v4M15 4v4"/>',
  up: '<path d="M12 18.5V6M6.5 11.5 12 6l5.5 5.5"/>',
  down: '<path d="M12 5.5V18M6.5 12.5 12 18l5.5-5.5"/>',
  scale: '<path d="M12 4v16M6 20h12M5 8l3.5 6h-7L5 8zM19 8l3.5 6h-7L19 8zM5 8h14"/>',
  list: '<path d="M8.5 7h11M8.5 12h11M8.5 17h11M4.5 7h.1M4.5 12h.1M4.5 17h.1"/>',
  more: '<path d="M6 12h.1M12 12h.1M18 12h.1"/>',
};
const ic = (n, w = 20, sw = 1.7) => `<svg class="ic" width="${w}" height="${w}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[n]}</svg>`;

/* ---------- 状态 ---------- */
let S = loadDemo('oct19');           // 业务数据（随评审视图切换）
const KEY = 'finunity-optimized-proto-v1';
const ROUTES = ['overview', 'household', 'scenarios', 'execution'];
const UI = {
  route: 'overview', theme: 'light', notes: false,
  hh: null, raw: {}, rawErr: {},
  scen: null, draftConsent: false, renewConsent: false,
  overlay: null,                      // {type:'record'|'assist'|'legend'|'dialog:<name>', ...}
  drafts: [], cur: 0, showErr: false, savedAt: null,
  filter: 'all', toasts: [], pulse: null, flash: [], pop: false, fresh: false,
  lastFocus: null, banner: true,
};
const blankScen = () => ({ extraIncome: '', extraExpenses: '', reducePct: '', ret: '', infl: '', extend: 0, monthly: {} });
UI.scen = { tab: 'A', A: blankScen(), B: null, C: null };

function resetDemo(name) {
  S = loadDemo(name);
  S.hhRev = S.rev >= 1 ? 1 : 0;
  S.reviewRev = S.reviewedAt ? S.rev : null;
  UI.hh = clone(S.household);
  UI.raw = {}; UI.rawErr = {};
  UI.scen = { tab: 'A', A: blankScen(), B: null, C: null };
  UI.draftConsent = false; UI.renewConsent = false; UI.overlay = null; UI.drafts = []; UI.cur = 0; UI.showErr = false;
  if (S.pending) S.pending.params = S.pending.scen;
}
resetDemo('oct19');

function persist() {
  try {
    localStorage.setItem(KEY, JSON.stringify({ theme: UI.theme, notes: UI.notes, demo: S.demo, drafts: { [S.demo]: UI.drafts }, cur: UI.cur }));
  } catch (e) { /* 隐私模式下忽略 */ }
}
function restore() {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) || 'null');
    if (!v) return;
    UI.theme = v.theme || 'light'; UI.notes = !!v.notes;
    if (v.demo && DEMOS[v.demo] && v.demo !== S.demo) resetDemo(v.demo);
    const d = v.drafts && v.drafts[S.demo];
    if (Array.isArray(d) && d.length) { UI.drafts = d; UI.cur = Math.min(v.cur || 0, d.length - 1); }
  } catch (e) { /* 忽略损坏的本地数据 */ }
}

/* ---------- 派生数据 ---------- */
const plan = () => S.plans.find((p) => p.status === 'active') || null;
const planParams = () => (plan() && plan().params) || {};
const RES = (params) => calc(S.household, S.ledger, params ?? planParams(), S.today);
const month = () => S.today.slice(0, 7);
const ACTS = () => actionsFor(plan(), S.ledger, month(), S.today).sort((a, b) => ({ in_progress: 0, pending: 1, completed: 2 }[a.status] - { in_progress: 0, pending: 1, completed: 2 }[b.status]));
const asset = (id) => S.household.assets.find((a) => a.id === id);
const goal = (id) => S.household.goals.find((g) => g.id === id);
function monthInfo() {
  const m = month();
  const n = daysIn(m);
  const d = Number(S.today.slice(8));
  return { m, label: monthLabel(m), day: d, n, left: n - d, zoneStart: n - 3 };
}
function diffCount(a, b) {
  if (a === b) return 0;
  if (a == null || b == null || typeof a !== 'object' || typeof b !== 'object') return 1;
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  let n = 0;
  keys.forEach((k) => { n += diffCount(a[k], b[k]); });
  return n;
}
const hhChanges = () => diffCount(UI.hh, S.household);
const isDirty = () => hhChanges() > 0;
const newRecords = () => S.ledger.filter((e) => !e.voided).length;

/* ---------- 通用部件 ---------- */
function money(c, cls = '') {
  return `<span class="num ${cls}">${esc(Y(c))}</span>`;
}
function bigMoney(c) {
  const neg = c < 0;
  const abs = Math.abs(c);
  const whole = nf0.format(Math.floor(abs / 100));
  const frac = String(abs % 100).padStart(2, '0');
  return `<span class="cur">${neg ? '−' : ''}¥</span>${whole}<span class="dec">.${frac}</span>`;
}
function chip(text, cls = '') { return `<span class="chip ${cls}">${text}</span>`; }
function statusChip(s) {
  return {
    pending: chip('待投入', 'plain'), in_progress: chip('进行中', 'olive'), completed: chip(`${ic('check', 14, 2)}已完成`, 'up'),
    confirmed: chip('已发生', 'up'), forecast: chip('预计 · 不入账', 'dashed'), voided: chip('已作废', 'down'),
  }[s] || chip(esc(s));
}
function btn(label, { cls = '', act = '', href = '', data = '', icon = '' } = {}) {
  const inner = `${icon ? ic(icon, 18) : ''}${label}`;
  return href
    ? `<a class="btn ${cls}" href="${href}" ${data}>${inner}</a>`
    : `<button type="button" class="btn ${cls}" data-act="${act}" ${data}>${inner}</button>`;
}
function ld({ label, sub = '', note = '', amount, sign = '', sw = '', cls = '' }) {
  const row = `<div class="ld ${cls}">${sign ? `<span class="sign">${sign}</span>` : ''}${sw ? `<span class="sw ${sw}"></span>` : ''}<span>${label}${sub ? ` <span class="sub">${sub}</span>` : ''}</span><i></i><b>${amount}</b></div>`;
  return note ? `<div class="ldw">${row}<div class="ld-note ${sign || sw ? 'ind' : ''}">${note}</div></div>` : row;
}
function secHead(no, title, aside = '') {
  return `<div class="sec-head"><h2><span class="no">${no}</span>${title}</h2>${aside ? `<p>${aside}</p>` : ''}</div>`;
}
