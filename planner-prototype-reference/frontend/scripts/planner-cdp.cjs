// 极简的 Chrome DevTools Protocol 驱动：用无头 Edge / Chrome 打开 ../index.html，执行脚本、截图。
// 需要 Node 22+（内置 fetch / WebSocket）。浏览器路径可用环境变量 BROWSER 指定。
// 只依赖本机浏览器，不装任何 npm 包。
const { spawn } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

// Adapted from the audit prototype's dependency-free CDP harness.
const CANDIDATES = [
  process.env.BROWSER,
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
].filter(Boolean);
const EDGE = CANDIDATES.find((p) => fs.existsSync(p));
if (!EDGE)
  throw new Error(
    "找不到 Edge / Chrome，请设置环境变量 BROWSER 指向浏览器可执行文件",
  );
const BASE = fs.mkdtempSync(path.join(os.tmpdir(), "finunity-proto-"));
const URL = "http://127.0.0.1:8012/";
function removeTemp(target) {
  const resolved = path.resolve(target);
  const base = path.resolve(BASE);
  if (resolved !== base && !resolved.startsWith(base + path.sep))
    throw new Error("Refusing cleanup outside the test profile");
  fs.rmSync(resolved, { recursive: true, force: true });
}
process.on("exit", () => {
  try {
    removeTemp(BASE);
  } catch (e) {
    /* 临时目录，忽略 */
  }
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function launch(port = 9333) {
  const prof = `${BASE}/profile-${Date.now()}`;
  const proc = spawn(
    EDGE,
    [
      "--headless=new",
      "--disable-gpu",
      "--hide-scrollbars",
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${prof}`,
      "about:blank",
    ],
    { stdio: "ignore", windowsHide: true },
  );
  for (let i = 0; i < 60; i++) {
    try {
      await (await fetch(`http://127.0.0.1:${port}/json/version`)).json();
      break;
    } catch (e) {
      await sleep(250);
    }
  }
  const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  const page = targets.find((t) => t.type === "page");
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((r) => {
    ws.onopen = r;
  });
  let id = 0;
  const pending = new Map();
  const listeners = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) {
      const { res, rej } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result);
    } else listeners.forEach((l) => l(msg));
  };
  const send = (method, params = {}) =>
    new Promise((res, rej) => {
      const i = ++id;
      pending.set(i, { res, rej });
      ws.send(JSON.stringify({ id: i, method, params }));
    });
  await send("Page.enable");
  await send("Runtime.enable");
  const logs = [];
  listeners.push((m) => {
    if (m.method === "Runtime.exceptionThrown")
      logs.push(
        "EXC " +
          JSON.stringify(
            (m.params.exceptionDetails.exception &&
              m.params.exceptionDetails.exception.description) ||
              m.params.exceptionDetails.text,
          ),
      );
    if (
      m.method === "Runtime.consoleAPICalled" &&
      ["error", "warning"].includes(m.params.type)
    )
      logs.push(
        m.params.type +
          " " +
          m.params.args.map((a) => a.value || a.description).join(" "),
      );
  });

  const api = {
    logs,
    send,
    async viewport(w, h, mobile = false, dpr = 1) {
      await send("Emulation.setDeviceMetricsOverride", {
        width: w,
        height: h,
        deviceScaleFactor: dpr,
        mobile,
      });
    },
    async open(hash = "", w = 1440, h = 900, opts = {}) {
      await api.viewport(w, h, !!opts.mobile, opts.dpr || 1);
      if (opts.colorScheme)
        await send("Emulation.setEmulatedMedia", {
          features: [{ name: "prefers-color-scheme", value: opts.colorScheme }],
        });
      // 先开一次页面清掉上次残留的本地状态，再打开真正要看的状态
      await send("Page.navigate", { url: URL + "?clr=" + Date.now() });
      await sleep(500);
      await api.eval(
        "try{localStorage.removeItem('finunity-optimized-proto-v1')}catch(e){}; 1",
      );
      await send("Page.navigate", { url: URL + "?r=" + Date.now() + hash });
      await sleep(900);
      await api.eval("document.documentElement.classList.add('still'); 1");
    },
    async eval(expr) {
      const r = await send("Runtime.evaluate", {
        expression: expr,
        awaitPromise: true,
        returnByValue: true,
      });
      if (r.exceptionDetails)
        throw new Error(
          "页面脚本出错: " +
            (r.exceptionDetails.exception
              ? r.exceptionDetails.exception.description
              : r.exceptionDetails.text),
        );
      return r.result.value;
    },
    async height() {
      return api.eval(
        "Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)",
      );
    },
    async shot(file, { full = false, clip, beyond = false } = {}) {
      const jpg = /\.jpe?g$/i.test(file);
      const fmt = jpg ? { format: "jpeg", quality: 90 } : { format: "png" };
      let params = { ...fmt, captureBeyondViewport: !!full || beyond };
      let restore = null;
      if (full) {
        const h = await api.height();
        const w = await api.eval("window.innerWidth");
        const dpr = await api.eval("window.devicePixelRatio");
        const mobile = await api.eval(
          "/Android|Mobile/i.test(navigator.userAgent) || ('ontouchstart' in window && window.innerWidth < 700)",
        );
        restore = [w, await api.eval("window.innerHeight"), mobile, dpr];
        await api.viewport(w, h, mobile, dpr);
        await sleep(250);
        params = { ...fmt, captureBeyondViewport: false };
      }
      if (clip) params.clip = { ...clip, scale: 1 };
      const r = await send("Page.captureScreenshot", params);
      if (restore) await api.viewport(...restore);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, Buffer.from(r.data, "base64"));
      return file;
    },
    async shotEl(file, selector, pad = 16) {
      const r = await api.eval(
        `(() => { const e = document.querySelector(${JSON.stringify(selector)}); if (!e) return null; const b = e.getBoundingClientRect(); return { x: b.left + scrollX, y: b.top + scrollY, w: b.width, h: b.height }; })()`,
      );
      if (!r) throw new Error("找不到 " + selector);
      const clip = {
        x: Math.max(r.x - pad, 0),
        y: Math.max(r.y - pad, 0),
        width: r.w + pad * 2,
        height: r.h + pad * 2,
      };
      return api.shot(file, { clip, beyond: true });
    },
    async close() {
      try {
        await send("Browser.close");
      } catch (e) {
        /* 已关闭 */
      }
      try {
        ws.close();
      } catch (e) {
        /* 忽略 */
      }
      proc.kill();
      await sleep(300);
      try {
        removeTemp(prof);
      } catch (e) {
        /* 忽略 */
      }
    },
    sleep,
  };
  return api;
}
module.exports = { launch, sleep, BASE, URL };
