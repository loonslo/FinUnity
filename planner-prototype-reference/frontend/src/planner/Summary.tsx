import { fmt } from "./format";
import type {
  Action,
  Calculation,
  Event,
  Household,
  PlanRequest,
  Review,
  Snapshot,
} from "./api";
import type { ReactNode } from "react";
import { effects } from "./execution";
import { label } from "./i18n";
import {
  BigMoney,
  Button,
  Chip,
  Icon,
  Issues,
  LedgerLine,
  SectionHead,
} from "./ui";

export function ActionCard({
  action: a,
  h,
  events,
  record,
  busy,
  complete,
}: {
  action: Action;
  h: Household;
  events: Event[];
  record: (a: Action) => void;
  busy: boolean;
  complete: (id: string) => void;
}) {
  const percentage =
    Number(a.amount) > 0 && a.actual_amount != null
      ? Math.min(100, (Number(a.actual_amount) / Number(a.amount)) * 100)
      : 0;
  return (
    <article
      className="card act"
      id={`action-${a.id}`}
      key={`${a.id}-${a.actual_amount}`}
    >
      <div>
        <div className="row">
          <h3>{h.goals.find((g) => g.id === a.goal_id)?.name ?? a.name}</h3>
          <Chip
            tone={
              a.status === "completed"
                ? "up"
                : a.status === "in_progress"
                  ? "olive"
                  : "plain"
            }
          >
            {a.status === "pending" && a.requires_event
              ? "待投入"
              : label(a.status)}
          </Chip>
          {Number(a.excess_amount) > 0 && (
            <Chip tone="warn">
              超出 {fmt(a.excess_amount, a.base_currency)}
            </Chip>
          )}
        </div>
        <p className="muted small">{a.month} 投入计划</p>
        {a.requires_event ? (
          <div className="figs">
            {[
              ["本月计划", a.amount],
              ["已投入", a.actual_amount],
              ["还需投入", a.remaining_amount],
            ].map(([k, v]) => (
              <div key={k}>
                <small>{k}</small>
                <b>{fmt(v, a.base_currency)}</b>
              </div>
            ))}
          </div>
        ) : (
          <p>{a.name}</p>
        )}
      </div>
      <div className="side">
        {a.can_record && (
          <Button
            primary={false}
            className={a.status === "completed" ? "ghost sm" : ""}
            disabled={busy}
            onClick={() => record(a)}
          >
            {a.status === "completed" ? "再记一笔" : "记录本次投入"}
          </Button>
        )}
        {!a.requires_event && a.status === "pending" && (
          <Button disabled={busy} onClick={() => complete(a.id)}>
            已检查安排
          </Button>
        )}
      </div>
      {a.requires_event && (
        <>
          <div
            className={`bar ${a.status === "completed" ? "done" : ""}`}
            role="progressbar"
            aria-valuenow={percentage}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${a.name}本月投入进度`}
          >
            <i style={{ width: `${percentage}%` }} />
          </div>
          <div className="recs">
            {a.event_ids.length
              ? a.event_ids.map((id) => {
                  const e = events.find((x) => x.id === id);
                  return (
                    e && (
                      <p key={id}>
                        {e.event.date} · {effects(h, e.event).join(" → ")}
                      </p>
                    )
                  );
                })
              : "本月还没有投入记录。"}
            {a.progress_missing.map((x) => (
              <p className="errmsg" key={x}>
                {x}
              </p>
            ))}
          </div>
        </>
      )}
    </article>
  );
}

export function Goals({
  result: R,
  adjust,
}: {
  result: Calculation;
  adjust?: (request: PlanRequest) => void;
}) {
  return (
    <>
      {R.goals.map((g) => {
        const target = Number(g.target),
          current = Math.min(Number(g.current), target),
          future = Math.max(
            Math.min(Number(g.projected) - Number(g.current), target - current),
            0,
          ),
          gap = Number(g.shortfall);
        const pct = (n: number) =>
          `${(Math.max(0, n) / Math.max(target, 1)) * 100}%`;
        return (
          <article className="card goal" key={g.id}>
            <div className="goal-head">
              <h3>{g.name}</h3>
              <Chip>第 {g.priority} 优先</Chip>
              <Chip tone={gap > 0 ? "warn" : "up"}>
                {gap > 0
                  ? `缺口 ${fmt(g.shortfall, R.base_currency)}`
                  : "按计划可达成"}
              </Chip>
              <span className="due">
                {g.due} 到期 · 还有 {g.months} 个月
              </span>
            </div>
            <div
              className="track"
              role="img"
              aria-label={`已分配 ${fmt(g.current, R.base_currency)}，到期测算 ${fmt(g.projected, R.base_currency)}，缺口 ${fmt(g.shortfall, R.base_currency)}`}
            >
              <span className="a" style={{ width: pct(current) }} />
              <span className="f" style={{ width: pct(future) }} />
              <span
                className="g"
                style={{
                  width: pct(
                    Math.min(gap, Math.max(0, target - current - future)),
                  ),
                }}
              />
              <span className="flag">
                目标 {fmt(g.target, R.base_currency)}
              </span>
            </div>
            <div className="legend3">
              <div>
                <span className="k">
                  <i className="k-a" />
                  已分配
                </span>
                <b>{fmt(g.current, R.base_currency)}</b>
                <small>已经分给这个目标的钱</small>
              </div>
              <div>
                <span className="k">
                  <i className="k-f" />
                  {Number(R.assumptions.annual_return ?? 0) === 0
                    ? "未来投入"
                    : "未来投入与假设收益"}
                </span>
                <b>
                  {fmt(
                    Math.max(Number(g.projected) - Number(g.current), 0),
                    R.base_currency,
                  )}
                </b>
                <small>
                  每月 {fmt(g.affordable_monthly, R.base_currency)} × {g.months}{" "}
                  个月
                </small>
              </div>
              <div>
                <span className="k">
                  <i className="k-g" />
                  预计缺口
                </span>
                <b>{gap > 0 ? fmt(g.shortfall, R.base_currency) : "无缺口"}</b>
                <small>
                  {gap > 0
                    ? `每月需要 ${fmt(g.needed_monthly, R.base_currency)} 才补得上`
                    : "到期时预计达到目标"}
                </small>
              </div>
            </div>
            {gap > 0 && adjust && (
              <div className="levers">
                <p>
                  怎么补上 {fmt(g.shortfall, R.base_currency)}
                  ？选一条，带到试算里看看：
                </p>
                <div className="lever-grid">
                  {g.adjustments?.map((option) => (
                    <button
                      type="button"
                      className="lever"
                      key={option.kind}
                      onClick={() => adjust(option.request)}
                    >
                      <b>
                        {option.kind === "monthly"
                          ? `每月多投 ${fmt(Number(option.request.monthly?.[g.id] ?? g.needed_monthly) - Number(g.affordable_monthly), R.base_currency)}`
                          : option.kind === "reduction"
                            ? `目标下调 ${option.request.scenario?.target_reduction_percent}%`
                            : option.title}
                      </b>
                      <small>{option.description}</small>
                      {option.kind === "monthly" && (
                        <small className={option.within_budget ? "ok" : "no"}>
                          {option.within_budget
                            ? "在月度剩余额度之内"
                            : "超过月度剩余额度，需要一起调整其它条件"}
                        </small>
                      )}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </article>
        );
      })}
    </>
  );
}

export function Overview({
  s,
  R,
  actions,
  events,
  record,
  busy,
  adjust,
  reconcile,
}: {
  s: Snapshot;
  R: Calculation;
  actions: Action[];
  events: Event[];
  record: (a: Action) => void;
  busy: boolean;
  complete: (id: string) => void;
  adjust: (request: PlanRequest) => void;
  reconcile: (id: string) => void;
}) {
  const h = s.household,
    currency = R.base_currency,
    cash = R.available_cash == null ? null : Number(R.available_cash),
    free = R.unallocated_cash == null ? null : Number(R.unallocated_cash),
    reserve = R.reserve == null ? null : Number(R.reserve),
    held = cash == null || reserve == null ? null : Math.min(cash, reserve),
    allocated =
      cash == null || free == null || held == null
        ? null
        : Math.max(0, cash - free - held);
  const strata = [
    ["reserve", "应急预留", held],
    ["alloc", "已分给目标", allocated],
    ["free", "可自由安排", free],
  ] as const;
  const assetTotal = R.assets.reduce((n, a) => n + Number(a.value_base), 0),
    debtTotal = assetTotal - Number(R.known_net_worth),
    estimated = R.assets.filter((a) => a.quality === "estimated").length;
  const scenario = R.assumptions,
    income =
      h.income == null
        ? null
        : Number(h.income) + Number(scenario.extra_income ?? 0),
    expenses =
      h.expenses == null
        ? null
        : Number(h.expenses) + Number(scenario.extra_expenses ?? 0);
  return (
    <>
      {(R.errors.length > 0 || R.missing.length > 0) && <Issues result={R} />}
      <section className="hero">
        <div className="hero-main">
          <p className="eyebrow">可自由安排的现金</p>
          <p className="hero-num number-update" key={R.unallocated_cash}>
            <BigMoney value={R.unallocated_cash} currency={currency} />
          </p>
          <p className="hero-sub">
            账户现金共 <b>{fmt(cash, currency)}</b>。其中{" "}
            <b>{fmt(reserve, currency)}</b> 是应急预留（必要支出{" "}
            {fmt(h.essential_expenses, currency)} × {h.reserve_months} 个月），
            <b>{fmt(allocated, currency)}</b>{" "}
            已经分给了目标——剩下的，才是能自由安排的钱。
          </p>
          <div className="strata">
            <div
              className="strata-bar"
              role="img"
              aria-label="现金构成：应急预留、已分给目标、可自由安排"
            >
              {strata.map(([key, title, value]) => (
                <div
                  className={`seg ${key}`}
                  key={key}
                  style={{ flex: Math.max(value ?? 0, 1) }}
                >
                  <span>{title}</span>
                </div>
              ))}
            </div>
            <div className="strata-legend">
              <LedgerLine
                sw="reserve"
                title={
                  <>
                    应急预留{" "}
                    <Chip
                      tone={Number(R.reserve_shortfall) > 0 ? "warn" : "up"}
                    >
                      {R.reserve_shortfall == null
                        ? "待核对"
                        : Number(R.reserve_shortfall) > 0
                          ? `缺口 ${fmt(R.reserve_shortfall, currency)}`
                          : "已备足"}
                    </Chip>
                  </>
                }
                value={fmt(held, currency)}
              />
              <LedgerLine
                sw="alloc"
                title="已分给目标"
                value={fmt(allocated, currency)}
              />
              <LedgerLine
                sw="free"
                title="可自由安排"
                value={fmt(free, currency)}
              />
              <LedgerLine
                title="账户现金合计"
                value={fmt(cash, currency)}
                total
              />
            </div>
          </div>
        </div>
        <aside className="card nw">
          <p className="eyebrow">
            净资产{" "}
            {estimated > 0 && <Chip tone="warn">含 {estimated} 项估算</Chip>}
          </p>
          <p className="nw-num number-update" key={R.net_worth}>
            <BigMoney value={R.net_worth} currency={currency} />
          </p>
          <div className="nw-bars">
            {R.assets
              .toSorted((a, b) => Number(b.value_base) - Number(a.value_base))
              .map((a) => (
                <div className="nwb" key={a.id}>
                  <div className="nwb-top">
                    <span>
                      {a.name}{" "}
                      {a.liquidity !== "available" && (
                        <span className="sub">不易变现</span>
                      )}
                    </span>
                    <b>{fmt(a.value_base, currency)}</b>
                  </div>
                  <div
                    className={`nwb-bar ${a.liquidity !== "available" ? "ill" : ""}`}
                  >
                    <i
                      style={{
                        width: `${Math.max(1.2, (Number(a.value_base) / Math.max(assetTotal, 1)) * 100)}%`,
                      }}
                    />
                  </div>
                </div>
              ))}
            {debtTotal > 0 && (
              <div>
                <div className="nwb-top">
                  <span>负债</span>
                  <b className="neg">−{fmt(debtTotal, currency)}</b>
                </div>
                <div className="nwb-bar debt">
                  <i
                    style={{
                      width: `${Math.min((debtTotal / Math.max(assetTotal, 1)) * 100, 100)}%`,
                    }}
                  />
                </div>
              </div>
            )}
          </div>
          <p className="hint" style={{ marginTop: 14 }}>
            房产与受限资产计入净资产，但不会算进可自由安排的现金。
          </p>
          <details>
            <summary>
              <Icon name="chev" size={16} />
              哪些是估算？
            </summary>
            {R.warnings.map((x) => (
              <p className="hint" key={x}>
                {x}
              </p>
            ))}
          </details>
        </aside>
      </section>
      <div className="two sec" style={{ marginTop: 36 }}>
        <section>
          <SectionHead
            no="01"
            title="本月行动"
            aside={`${actions.filter((a) => a.status === "completed").length} / ${actions.length} 已完成`}
          />
          <div className="card compact-actions">
            {actions.map((a) => (
              <article
                className="compact-action"
                key={`${a.id}-${a.actual_amount}`}
              >
                <div>
                  <b>
                    {h.goals.find((g) => g.id === a.goal_id)?.name ?? a.name}
                  </b>
                  <p className="hint">
                    {a.status === "completed"
                      ? "已完成"
                      : !a.event_ids.length || a.actual_amount == null
                        ? "未记录"
                        : `已投入 ${fmt(a.actual_amount, a.base_currency)}`}{" "}
                    / 计划 {fmt(a.amount, a.base_currency)}
                  </p>
                  <div className="bar">
                    <i
                      style={{
                        width: `${Number(a.amount) > 0 ? Math.min(100, (Number(a.actual_amount ?? 0) / Number(a.amount)) * 100) : 0}%`,
                      }}
                    />
                  </div>
                </div>
                {a.can_record && (
                  <Button
                    className="sm"
                    disabled={busy}
                    onClick={() => record(a)}
                  >
                    {a.status === "completed" ? "再记一笔" : "记录投入"}
                  </Button>
                )}
              </article>
            ))}
          </div>
          {!actions.length && (
            <div className="card pad">
              <p className="muted">这个月还没有投入计划。</p>
              <a className="btn add-button" href="#/execution">
                去生成本月计划
              </a>
            </div>
          )}
        </section>
        <section>
          <SectionHead
            no="02"
            title="本月预算"
            aside={`每月 ${fmt(income, currency)} 的去向`}
          />
          <div className="card pad">
            <LedgerLine
              title="月收入"
              sign="＋"
              value={fmt(income, currency)}
            />
            <LedgerLine
              title="月支出"
              sign="−"
              sw="c-exp"
              value={
                expenses == null ? "待补充" : `−${fmt(expenses, currency)}`
              }
              note={`其中必要支出 ${fmt(h.essential_expenses, currency)}`}
            />
            <LedgerLine
              title="最低还款"
              sign="−"
              sw="c-debt"
              value={`−${fmt(R.minimum_payments, currency)}`}
            />
            <LedgerLine
              title={
                Number(R.unlinked_commitments) > 0
                  ? "目标投入与独立承诺"
                  : "目标投入"
              }
              sign="−"
              sw="c-goal"
              value={`−${fmt(R.monthly_committed, currency)}`}
              note={R.goals
                .map((g) => `${g.name} ${fmt(g.affordable_monthly, currency)}`)
                .join(" + ")}
            />
            <LedgerLine
              title="本月可再分配"
              sign="＝"
              sw="c-left"
              value={fmt(R.monthly_remaining, currency)}
              total
            />
            <div className="tape-bar">
              {[
                ["c-exp", expenses],
                ["c-debt", Number(R.minimum_payments)],
                ["c-goal", Number(R.monthly_committed)],
                ["c-left", Number(R.monthly_remaining)],
              ].map(([key, value]) => (
                <span
                  className={String(key)}
                  key={String(key)}
                  style={{
                    width: `${(Math.max(0, Number(value)) / Math.max(income ?? 0, 1)) * 100}%`,
                  }}
                />
              ))}
            </div>
            <p className="hint">
              已有定投已包含在关联目标的每月投入里，不会重复扣减。
            </p>
          </div>
        </section>
      </div>
      <section className="sec">
        <SectionHead
          no="03"
          title="目标进度"
          aside="按优先级排：资金不够时，靠前的目标先拿到预算"
        />
        <Goals result={R} adjust={adjust} />
      </section>
      <section className="sec">
        <SectionHead no="04" title="这些数字从哪来" aside={`截至 ${R.as_of}`} />
        <div className="card prov">
          <details>
            <summary>
              <Icon name="chev" size={16} />
              账户余额 = 基准 + 已记流水
            </summary>
            {R.assets.map((a) => {
              const recorded = events.filter(
                (e) =>
                  e.status === "confirmed" &&
                  e.event.date > a.observed_on &&
                  e.event.date <= R.as_of &&
                  (e.event.asset_id === a.id ||
                    e.event.target_asset_id === a.id),
              );
              const delta =
                a.balance == null || a.amount == null
                  ? null
                  : Number(a.balance) - Number(a.amount);
              return (
                <div className="item" key={a.id}>
                  <div className="row">
                    <b>
                      {a.name}
                      {a.quality === "estimated" ? "（估算）" : ""}
                    </b>
                    <span className="sp" />
                    {["cash", "investment"].includes(a.kind) && (
                      <Button
                        className="ghost sm"
                        disabled={busy || a.balance == null}
                        onClick={() => reconcile(a.id)}
                      >
                        对账
                      </Button>
                    )}
                  </div>
                  <p className="eq">
                    基准 <b>{fmt(a.amount, a.currency)}</b>（{a.observed_on}{" "}
                    日终 · {a.quality === "estimated" ? "估算" : "已核实"}）
                    {recorded.length ? (
                      <>
                        {" "}
                        {delta != null && delta < 0 ? "−" : "＋"} 已记流水{" "}
                        <b>
                          {fmt(
                            delta == null ? null : Math.abs(delta),
                            a.currency,
                          )}
                        </b>
                        （{recorded.length} 笔）＝{" "}
                        <b>{fmt(a.balance, a.currency)}</b>（截至 {R.as_of}）
                      </>
                    ) : (
                      "，之后没有流水"
                    )}
                  </p>
                  <span className="hint">来源：{a.source}</span>
                </div>
              );
            })}
            <p className="hint" style={{ marginTop: 12 }}>
              只记一部分流水时，余额可能偏离银行数字。用「对账」生成可作废的差额调整，基准日不变。
            </p>
          </details>
        </div>
      </section>
    </>
  );
}

export function ReviewCard({
  review: r,
  events,
  footer,
}: {
  review: Review;
  events: Event[];
  footer?: ReactNode;
}) {
  const rows = events.filter((e) => r.result.event_ids.includes(e.id)),
    currency = r.result.base_currency,
    known = r.result.missing.length === 0;
  return (
    <article className="card review">
      <div className="row">
        <h3>{r.result.month.replace("-", " 年 ")} 月复盘</h3>
        <Chip tone={r.result.partial_month ? "olive" : "up"}>
          {r.result.partial_month
            ? `进行中 · 截至 ${r.result.as_of}`
            : "月份已结束"}
        </Chip>
      </div>
      <div className="review-banner">
        {r.result.partial_month
          ? "本月还在进行中，差额表示当前还需投入；月底后再作收官判断。"
          : "月份已结束；只汇总已确认的记录，未记录不等于没有发生。"}
      </div>
      <div className="tiles">
        {(
          [
            ["income", "收入", r.result.income],
            ["expense", "支出", r.result.expenses],
            ["debt_payment", "还款", r.result.debt_payments],
          ] as const
        ).map(([kind, title, value]) => {
          const count = rows.filter((e) => e.event.kind === kind).length;
          return (
            <div className="tile" key={kind}>
              <small>{title}</small>
              <b className={!count || !known ? "none" : ""}>
                {!known ? "待核对" : count ? fmt(value, currency) : "未记录"}
              </b>
              <span>{count} 笔已记录</span>
            </div>
          );
        })}
      </div>
      <p className="hint">{r.result.coverage}</p>
      {r.result.missing.map((x) => (
        <p className="errmsg" key={x}>
          {x}
        </p>
      ))}
      {r.result.goals.map((g) => {
        const recorded = rows.some(
          (e) =>
            e.event.goal_id === g.id &&
            e.event.allocation_mode === "new_funding",
        );
        return (
          <div className="rv-goal" key={g.id}>
            <div className="row">
              <b>{g.name}</b>
              <Chip
                tone={
                  !known || !recorded
                    ? "plain"
                    : Number(g.variance) >= 0
                      ? "up"
                      : "warn"
                }
              >
                {!known
                  ? "待核对"
                  : !recorded
                    ? r.result.partial_month
                      ? "进行中 · 待记录"
                      : "待记录"
                    : r.result.partial_month
                      ? Number(g.variance) >= 0
                        ? "已达成"
                        : `进行中 · 还差 ${fmt(Math.max(-Number(g.variance), 0), currency)}`
                      : Number(g.variance) >= 0
                        ? "已达到计划"
                        : `已记录投入少于计划 ${fmt(-Number(g.variance), currency)}`}
              </Chip>
            </div>
            <span className="small">
              <b>
                {known && recorded
                  ? fmt(g.actual, currency)
                  : known
                    ? "未记录"
                    : "待核对"}
              </b>{" "}
              / {fmt(g.planned, currency)}
            </span>
            <div
              className={`bar ${known && recorded && Number(g.variance) >= 0 ? "done" : ""}`}
            >
              <i
                style={{
                  width: `${known && recorded && Number(g.planned) > 0 ? Math.min((Number(g.actual) / Number(g.planned)) * 100, 100) : 0}%`,
                }}
              />
            </div>
          </div>
        );
      })}
      <p className="hint">对账调整、内部转账不计入收入或支出。</p>
      {footer && (
        <div className="row" style={{ marginTop: 20 }}>
          {footer}
        </div>
      )}
    </article>
  );
}
