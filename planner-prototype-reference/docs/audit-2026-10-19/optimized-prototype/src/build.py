"""把拆开的源文件拼成一个自包含的 ../index.html（字体内嵌，离线可用）。

用法（任意目录）：python docs/audit-2026-10-19/optimized-prototype/src/build.py
依赖：Python 3 与 Node（只用来做语法检查）。字体取自项目 frontend/public/fonts。
源文件：tokens.css（设计令牌）、app.css（组件与页面）、engine.js（测算引擎的示意移植）、data.js（虚构示例与评审状态）、
        fixes.json（改动清单，同时是报告里编号的出处）、app1–5.js（状态、页面、浮层、事件）、template.html。
"""
import base64, json, pathlib, subprocess, sys, tempfile

here = pathlib.Path(__file__).resolve().parent
root = here.parents[3]                       # 项目根目录
fonts = root / "frontend" / "public" / "fonts"
out = here.parent / "index.html"

b64 = lambda p: base64.b64encode(p.read_bytes()).decode()
css = (here / "tokens.css").read_text(encoding="utf-8") + "\n" + (here / "app.css").read_text(encoding="utf-8")
css = css.replace("@@IS_LATIN@@", b64(fonts / "instrument-serif-latin.woff2")).replace("@@AR_LATIN@@", b64(fonts / "archivo-latin.woff2"))

fixes = json.loads((here / "fixes.json").read_text(encoding="utf-8"))
js = "\n".join([
    (here / "engine.js").read_text(encoding="utf-8"),
    (here / "data.js").read_text(encoding="utf-8"),
    "const FIXES = " + json.dumps(fixes, ensure_ascii=False) + ";",
    *[(here / f"app{i}.js").read_text(encoding="utf-8") for i in range(1, 6)],
])
with tempfile.TemporaryDirectory() as tmp:
    bundle = pathlib.Path(tmp) / "bundle.js"
    bundle.write_text(js, encoding="utf-8")
    chk = subprocess.run(["node", "--check", str(bundle)], capture_output=True, text=True)
if chk.returncode:
    print(chk.stderr); sys.exit(1)

html = (here / "template.html").read_text(encoding="utf-8").replace("@@CSS@@", css).replace("@@JS@@", js)
out.write_text(html, encoding="utf-8")
print(f"built {out}  {len(html)/1024:.0f} KB (css {len(css)/1024:.0f} KB, js {len(js)/1024:.0f} KB)")
