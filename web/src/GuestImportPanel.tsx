import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";
import type { GuestRow } from "./onboarding";
import type { Locale } from "./types";

export function GuestImportPanel({ rows, locale, onDone }: { rows: GuestRow[]; locale: Locale; onDone: () => void }) {
  const zh = locale === "zh-CN";
  const [done, setDone] = useState<string[]>([]);
  const [attempted, setAttempted] = useState<string[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const lock = useRef(false);
  const panel = useRef<HTMLElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panel.current?.querySelector<HTMLButtonElement>("button")?.focus();
    const key = (event: KeyboardEvent) => {
      if (event.key !== "Tab") return;
      const buttons = Array.from(panel.current?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)") || []);
      if (!buttons.length) { event.preventDefault(); return; }
      const first = buttons[0], last = buttons.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", key);
    return () => { document.body.style.overflow = overflow; document.removeEventListener("keydown", key); previous?.focus(); };
  }, []);
  const finish = (id: string) => {
    const next = [...done, id]; setDone(next);
    if (next.length === rows.length) onDone();
  };
  return <div className="auth-backdrop"><section ref={panel} className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="guest-transfer-title">
    <h2 id="guest-transfer-title">{zh ? "带入你自己录入的数据" : "Bring your own records"}</h2>
    <p>{zh ? "逐条确认后才写入。示例家庭、示例截图和对示例行的修改不会带入。来源不会沿用示例机构。" : "Confirm each record to save it. Example household rows, sample screenshots and edits to example rows are excluded. Example sources are not copied."}</p>
    {error && <p role="alert">{error}</p>}
    {rows.filter(row => !done.includes(row.id)).map(row => <article className="guest-row" key={row.id}>
      <strong>{row.label}</strong>
      <p>{row.holding ? `${row.holding.currency} · ${row.holding.quantity} · ${row.holding.asset_type}` : `${row.transaction?.transaction_date} · ${row.transaction?.currency} ${((row.transaction?.amount_minor ?? 0) / 100).toFixed(2)}`}</p>
      {row.holding && <p>{zh ? "成本价 / 现价 / 分类" : "Cost / Price / Bucket"}: {row.holding.average_cost ?? "—"} / {row.holding.current_price ?? "—"} / {row.holding.risk_bucket ?? "—"}</p>}
      <button className="primary-button" disabled={Boolean(busy)} onClick={async () => {
        if (lock.current) return;
        lock.current = true; setBusy(row.id); setError(""); setAttempted(previous => [...previous, row.id]);
        try {
          if (row.holding) await api.saveHoldings([row.holding], row.requestId);
          if (row.transaction) await api.saveTransactions([row.transaction], row.requestId);
          finish(row.id);
        } catch (cause) {
          if (cause instanceof ApiError && [400, 422].includes(cause.status)) setAttempted(previous => previous.filter(id => id !== row.id));
          setError(`${cause instanceof Error ? cause.message : String(cause)} ${zh ? "可重试此行，请求 ID 保持不变。" : "Retry this row with the same request ID."}`);
        }
        finally { lock.current = false; setBusy(null); }
      }}>{busy === row.id ? zh ? "写入中…" : "Saving…" : attempted.includes(row.id) ? zh ? "重试此行" : "Retry row" : zh ? "确认带入此行" : "Save this row"}</button>
      <button className="text-button" disabled={Boolean(busy) || attempted.includes(row.id)} onClick={() => finish(row.id)}>{zh ? "跳过" : "Skip"}</button>
    </article>)}
  </section></div>;
}
