// 重新生成 ../screenshots/ 下的全部截图：node tests/capture-screenshots.js [名称片段 ...]
const path = require('path');
const { launch } = require('./cdp.js');
const OUT = path.join(__dirname, '..', 'screenshots');
const list = [
  // [文件名, hash, 宽, 高, 选项]  文件名即最终放进文档的名字
  ['01-overview', '#/overview?still=1', 1440, 900, { full: true }],
  ['02-household', '#/household?still=1&hh=dirty', 1440, 900, { full: true }],
  ['03-scenarios', '#/scenarios?still=1&scenB=1', 1440, 900, { full: true }],
  ['04-execution', '#/execution?still=1', 1440, 900, { full: true }],
  ['05-record-sheet', '#/execution?still=1&sheet=record&action=act-family', 1440, 900, {}],
  ['06-record-errors', '#/execution?still=1&sheet=record&drafts=2&err=2', 1440, 900, {}],
  ['07-void-dialog', '#/execution?still=1&dialog=void', 1440, 900, {}],
  ['08-new-month', '#/execution?still=1&demo=nov02', 1440, 900, { full: true }],
  ['09-stale-draft', '#/scenarios?still=1&demo=stale', 1440, 900, {}],
  ['10-empty', '#/overview?still=1&demo=empty', 1440, 900, {}],
  ['11-dark', '#/overview?still=1&theme=dark', 1440, 900, { full: true }],
  ['12-mobile-overview', '#/overview?still=1', 390, 844, { mobile: true, dpr: 2 }],
  ['13-mobile-sheet', '#/execution?still=1&sheet=record&action=act-family', 390, 844, { mobile: true, dpr: 2 }],
  ['14-recon', '#/overview?still=1&dialog=recon', 1440, 900, {}],
  ['15-annotations', '#/overview?still=1&notes=1', 1440, 900, {}],
  ['16-scenario-hints', '#/scenarios?still=1&lock=1&scenerr=1', 1440, 900, { el: '.scen-controls' }],
  ['17-legend', '#/overview?still=1&sheet=legend', 1440, 900, {}],
  ['18-assistant', '#/overview?still=1&sheet=assist', 1440, 900, {}],
];
(async () => {
  const only = process.argv.slice(2);
  const b = await launch(9334);
  try {
    for (const [name, hash, w, h, o] of list) {
      if (only.length && !only.some((k) => name.includes(k))) continue;
      await b.open(hash, w, h, o);
      await b.sleep(400);
      if (o.el) await b.shotEl(`${OUT}/${name}.jpg`, o.el, 12); else await b.shot(`${OUT}/${name}.jpg`, { full: !!o.full });
      console.log('ok', name, await b.height());
    }
    console.log('console errors:', b.logs.length ? b.logs : 'none');
  } finally { await b.close(); }
})();
