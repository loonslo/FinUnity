import { useEffect, useRef, useState } from "react";
import type { Event, Household, Review } from "./api";
import { fmt } from "./format";
import { reviewHistory } from "./reviewHistory";

export function ReviewTrend({
  reviews,
  events,
  h,
  month,
}: {
  reviews: Review[];
  events: Event[];
  h: Household;
  month: string;
}) {
  const [selected, setSelected] = useState("");
  const goal = h.goals.find((g) => g.id === selected) ?? h.goals[0];
  const goalId = goal?.id;
  const chart = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = chart.current;
    if (!node) return;
    const latest = () => {
      node.scrollLeft = node.scrollWidth;
    };
    const resize = new ResizeObserver(latest);
    resize.observe(node);
    latest();
    return () => resize.disconnect();
  }, [goalId, month]);
  if (!goal) return null;
  const rows = reviewHistory(reviews, events, month, h.base_currency, goal.id);
  const max = Math.max(
    1,
    ...rows.flatMap((r) => [Number(r.planned ?? 0), Number(r.actual ?? 0)]),
  );
  return (
    <section className="card pad trend">
      <div className="row">
        <h3>近六个月 · 计划与已记录投入</h3>
        <select
          className="in"
          aria-label="历史趋势目标"
          value={goal.id}
          onChange={(e) => setSelected(e.target.value)}
        >
          {h.goals.map((g) => (
            <option value={g.id} key={g.id}>
              {g.name}
            </option>
          ))}
        </select>
      </div>
      <p className="hint">
        保存的复盘快照 ·
        浅色为计划，橄榄绿为实际；未记录保留空白。更新复盘后刷新。
      </p>
      <p className="hint mobile-only">可横向滑动查看其他月份</p>
      <div
        className="trend-chart"
        ref={chart}
        tabIndex={0}
        role="region"
        aria-label="六个月复盘趋势，可横向滚动"
      >
        {rows.map((r) => (
          <div className="trend-month" key={r.month}>
            <div className="trend-bars" aria-hidden="true">
              {r.planned != null && (
                <i
                  className="planned"
                  style={{ height: `${(Number(r.planned) / max) * 100}%` }}
                />
              )}
              {r.actual != null && (
                <i
                  className="actual"
                  style={{ height: `${(Number(r.actual) / max) * 100}%` }}
                />
              )}
            </div>
            <b>{r.month.slice(5)} 月</b>
            <small>
              计划{" "}
              {r.planned == null ? "未复盘" : fmt(r.planned, h.base_currency)}
            </small>
            <small>
              实际{" "}
              {r.actual == null ? "未记录" : fmt(r.actual, h.base_currency)}
            </small>
            {r.asOf && <small>截至 {r.asOf.slice(5)}</small>}
          </div>
        ))}
      </div>
    </section>
  );
}
