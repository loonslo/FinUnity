import { fmt } from "./format";
import {
  Fragment,
  useCallback,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  errorMessage,
  post,
  type Calculation,
  type Household,
  type Plan,
  type PlanRequest,
  type Snapshot,
} from "./api";
import { fraction, percent, requestErrors } from "./scenario";
import { label } from "./i18n";
import { day } from "./sample";
import { Button, Chip, Field, Icon, Issues, Modal, PageHead } from "./ui";
import { Goals } from "./Summary";

export type Trials = { selected: number; requests: PlanRequest[] };

export function Scenarios({
  h,
  baseline,
  plans,
  revision,
  trials,
  setTrials,
  visible,
  busy,
  act,
  error,
  meta,
}: {
  h: Household;
  baseline: Calculation;
  plans: Plan[];
  revision: number;
  trials: Trials;
  setTrials: (v: Trials) => void;
  visible: boolean;
  busy: boolean;
  act: (work: () => Promise<unknown>, text?: string) => Promise<boolean>;
  error: string;
  meta?: ReactNode;
}) {
  const [results, setResults] = useState<(Calculation | null)[]>([]),
    [calcError, setCalcError] = useState(""),
    [confirmOpen, setConfirmOpen] = useState(false);
  const closeConfirm = useCallback(() => setConfirmOpen(false), []);
  const [rawPercents, setRawPercents] = useState<
    Record<string, { text: string; value: string }>
  >({});
  const requests = trials.requests,
    request = requests[trials.selected],
    s = request.scenario ?? {},
    errors = requestErrors(request, h);
  useEffect(() => {
    if (!visible) return;
    let alive = true;
    const timer = setTimeout(() => {
      void Promise.all(
        requests.map(async (r) => {
          if (Object.keys(requestErrors(r, h)).length) return null;
          const response = await post<Snapshot>("/calculate", r);
          return response.result;
        }),
      )
        .then((r) => {
          if (alive) {
            setResults(r);
            setCalcError("");
          }
        })
        .catch((e) => {
          if (alive) setCalcError(errorMessage(e));
        });
    }, 350);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [requests, h, revision, visible]);
  const update = (next: PlanRequest) => {
    setResults((old) => old.map((x, i) => (i === trials.selected ? null : x)));
    setTrials({
      ...trials,
      requests: requests.map((r, i) => (i === trials.selected ? next : r)),
    });
  };
  const pending = plans.find(
      (p) => p.status === "pending" || p.status === "stale",
    ),
    active = plans.find((p) => p.status === "active"),
    chosen = results[trials.selected];
  const stale =
    pending &&
    (pending.status === "stale" ||
      pending.revision !== revision ||
      pending.result.as_of !== day());
  const shortfalls =
    pending &&
    (pending.result.goals.some((g) => Number(g.shortfall) > 0) ||
      Number(pending.result.reserve_shortfall) > 0);
  const columns = [
    { name: active ? "当前方案" : "当前建档", result: baseline },
    ...requests.map((_, i) => ({
      name: `试算 ${String.fromCharCode(65 + i)}`,
      result: results[i] ?? null,
    })),
  ];
  return (
    <>
      <PageHead
        eyebrow={
          pending ? "草案待处理" : active ? "方案使用中" : "等待生成方案"
        }
        title="方案"
        lede="先试算，再选一份生成方案草案，最后确认。每一步都不会动你的账目。"
        meta={meta}
      />
      {pending && !stale && (
        <section className="card pad pending-summary">
          <div className="row">
            <h3>方案草案 · 待确认</h3>
            <Button onClick={() => setConfirmOpen(true)}>查看差异并确认</Button>
          </div>
        </section>
      )}
      {pending && (stale || (confirmOpen && visible)) && (
        <ProposalFrame stale={!!stale} close={closeConfirm} busy={busy}>
          <section
            className={`card draft stack ${stale ? "stale" : ""}`}
            key={pending.id}
          >
            <div className="row">
              <h3>方案草案</h3>
              <Chip tone={stale ? "warn" : "olive"}>
                {stale ? "已过期" : "待确认"}
              </Chip>
              <span className="muted small">
                测算修订 {pending.revision} · {pending.result.as_of}
              </span>
            </div>
            {stale ? (
              <>
                <p>账目或日期已变化，这份草案不能确认。试算条件会保留。</p>
                <div className="row">
                  <Button
                    primary
                    disabled={busy}
                    onClick={() => {
                      setTrials({ selected: 0, requests: [pending.request] });
                      void act(
                        () =>
                          post("/plans", {
                            ...pending.request,
                            expected_revision: revision,
                          }),
                        "已基于最新账目生成方案草案",
                      ).then((ok) => {
                        if (ok) setConfirmOpen(true);
                      });
                    }}
                  >
                    基于最新账目重新测算
                  </Button>
                  <Button
                    disabled={busy}
                    onClick={() =>
                      void act(
                        () => post(`/plans/${pending.id}/discard`, {}),
                        "草案已放弃",
                      )
                    }
                  >
                    放弃草案
                  </Button>
                </div>
              </>
            ) : (
              <>
                <Issues result={pending.result} />
                <p>
                  应急金缺口：
                  {fmt(pending.result.reserve_shortfall, h.base_currency)}
                </p>
                <div className="grid2">
                  {pending.result.goals.map((g) => (
                    <div key={g.id}>
                      <b>{g.name}</b>
                      <p>
                        每月{" "}
                        {fmt(
                          baseline.goals.find((x) => x.id === g.id)
                            ?.affordable_monthly,
                          h.base_currency,
                        )}{" "}
                        → {fmt(g.affordable_monthly, h.base_currency)}
                      </p>
                      <p>
                        到期{" "}
                        {baseline.goals.find((x) => x.id === g.id)?.due ?? "—"}{" "}
                        → {g.due}
                      </p>
                      <p className="hint">
                        预计缺口 {fmt(g.shortfall, h.base_currency)}
                      </p>
                    </div>
                  ))}
                </div>
                <details>
                  <summary>查看本方案条件</summary>
                  <Assumptions result={pending.result} />
                </details>
                <p className="hint">
                  确认后需自行执行；不会自动交易。下面的按钮同时确认列示的目标、应急金缺口与试算条件。
                </p>
                <div className="row">
                  <Button
                    primary
                    disabled={busy || !pending.result.executable}
                    onClick={() =>
                      void act(
                        () =>
                          post(`/plans/${pending.id}/confirm`, {
                            accept_shortfalls: !!shortfalls,
                          }),
                        "方案已确认；可以记录实际执行",
                      ).then((ok) => {
                        if (ok) setConfirmOpen(false);
                      })
                    }
                  >
                    {shortfalls ? "接受列示缺口并采用方案" : "确认采用这份方案"}
                  </Button>
                  <Button
                    disabled={busy}
                    onClick={() =>
                      void act(
                        () => post(`/plans/${pending.id}/discard`, {}),
                        "草案已放弃",
                      )
                    }
                  >
                    放弃草案
                  </Button>
                </div>
              </>
            )}
            {error && (
              <p className="summary-err" role="alert">
                {error}
              </p>
            )}
          </section>
        </ProposalFrame>
      )}
      <div className="scen">
        <section className="card scen-controls">
          <div className="scen-tabs" role="tablist" aria-label="试算条件">
            {requests.map((_, i) => (
              <button
                className="tab"
                type="button"
                role="tab"
                aria-selected={trials.selected === i}
                key={i}
                onClick={() => setTrials({ ...trials, selected: i })}
              >
                试算 {String.fromCharCode(65 + i)}
              </button>
            ))}
            {requests.length < 3 && (
              <Button
                className="ghost sm"
                onClick={() =>
                  setTrials({
                    selected: requests.length,
                    requests: [...requests, structuredClone(request)],
                  })
                }
              >
                ＋ 对比
              </Button>
            )}
          </div>

          <p className="group-title">月度收支</p>
          {(
            [
              ["extra_income", "每月额外收入"],
              ["extra_expenses", "每月额外支出"],
            ] as const
          ).map(([key, title]) => (
            <Field title={title} key={key} error={errors[key]}>
              <div className="pre l">
                <span>{h.base_currency === "CNY" ? "¥" : h.base_currency}</span>
                <input
                  className={`in ${errors[key] ? "err" : ""}`}
                  inputMode="decimal"
                  value={String(s[key] ?? "0")}
                  onChange={(e) =>
                    update({
                      ...request,
                      scenario: { ...s, [key]: e.target.value },
                    })
                  }
                />
              </div>
            </Field>
          ))}
          <p className="group-title">目标与假设</p>
          {(
            [
              ["target_reduction_percent", "目标金额下调"],
              ["annual_return", "年化收益假设"],
              ["annual_inflation", "年通胀假设"],
            ] as const
          ).map(([key, title]) => (
            <Field
              title={title}
              key={key}
              error={errors[key]}
              hint={
                key === "target_reduction_percent"
                  ? "填 8 表示把每个目标都降低 8%。"
                  : key === "annual_return"
                    ? "默认 0%。这是假设，不是收益保证。"
                    : "默认 0%。填 2 表示每年物价涨 2%，目标金额会相应变大。"
              }
            >
              <div className="pre r">
                <span>%</span>
                <input
                  className={`in ${errors[key] ? "err" : ""}`}
                  inputMode="decimal"
                  value={
                    rawPercents[trials.selected + key]?.value ===
                    String(s[key] ?? "0")
                      ? rawPercents[trials.selected + key].text
                      : key === "target_reduction_percent"
                        ? String(s[key] ?? "0")
                        : percent(s[key])
                  }
                  onChange={(e) => {
                    const value =
                      key === "target_reduction_percent"
                        ? e.target.value || "0"
                        : fraction(e.target.value || "0");
                    setRawPercents({
                      ...rawPercents,
                      [trials.selected + key]: { text: e.target.value, value },
                    });
                    update({
                      ...request,
                      scenario: {
                        ...s,
                        [key]: value,
                      },
                    });
                  }}
                />
              </div>
            </Field>
          ))}
          <div
            className="choice-row"
            role="group"
            aria-label="年化收益快捷假设"
          >
            {[0, 3, 5].map((n) => (
              <button
                type="button"
                key={n}
                aria-pressed={
                  s.annual_return === fraction(String(n)) ||
                  (!s.annual_return && n === 0)
                }
                onClick={() =>
                  update({
                    ...request,
                    scenario: { ...s, annual_return: fraction(String(n)) },
                  })
                }
              >
                {n}% 年化
              </button>
            ))}
          </div>
          <Field
            title="延期目标"
            error={errors.extension_months}
            hint="所有目标的到期日一起往后推。"
          >
            <div className="choice-row" role="group" aria-label="延期月数">
              {[0, 3, 6, 12].map((n) => (
                <button
                  type="button"
                  key={n}
                  aria-pressed={(s.extension_months ?? 0) === n}
                  onClick={() =>
                    update({
                      ...request,
                      scenario: { ...s, extension_months: n },
                    })
                  }
                >
                  {n ? `＋${n} 个月` : "不延期"}
                </button>
              ))}
            </div>
            <input
              className="in"
              type="number"
              aria-label="自定义延期月数"
              min={0}
              max={120}
              value={s.extension_months ?? 0}
              onChange={(e) =>
                update({
                  ...request,
                  scenario: {
                    ...s,
                    extension_months:
                      e.target.value === "" ? 0 : Number(e.target.value),
                  },
                })
              }
            />
          </Field>
          <p className="group-title">各目标每月投入</p>
          {h.goals.map((g) => {
            const floor = h.commitments
              .filter((c) => c.goal_id === g.id)
              .reduce((n, c) => n + Number(c.monthly), 0);
            return (
              <div key={g.id}>
                <Field title={g.name} error={errors[g.id]}>
                  <div className="pre l">
                    <span>
                      {h.base_currency === "CNY" ? "¥" : h.base_currency}
                    </span>
                    <input
                      className={`in ${errors[g.id] ? "err" : ""}`}
                      inputMode="decimal"
                      value={String(request.monthly?.[g.id] ?? g.monthly)}
                      onChange={(e) =>
                        update({
                          ...request,
                          monthly: {
                            ...request.monthly,
                            [g.id]: e.target.value,
                          },
                        })
                      }
                    />
                  </div>
                </Field>
                {floor > 0 && (
                  <p className="lock">
                    <Icon name="lock" size={16} />
                    含已有定投 {fmt(floor, h.base_currency)}
                    /月（下限），不能在此静默降低。
                  </p>
                )}
              </div>
            );
          })}
          <Button
            className="sm"
            onClick={() =>
              update(active?.request ?? { scenario: {}, monthly: {} })
            }
          >
            还原为当前方案
          </Button>
        </section>
        <section className="stack">
          <div className="sec-head" style={{ marginBottom: 0 }}>
            <h2>结果对比</h2>
            <p>改动过的格子会高亮；绿色＝更好，红色＝更差</p>
          </div>
          <div className="card">
            <div className="ledger-wrap">
              <table className="cmp">
                <thead>
                  <tr>
                    <th>对比项目</th>
                    {columns.map((c, i) => (
                      <th
                        className={
                          i === 0
                            ? "base"
                            : i === trials.selected + 1
                              ? "selected-column"
                              : ""
                        }
                        key={c.name}
                      >
                        {c.name}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>每月可再分配</td>
                    {columns.map((c, i) => (
                      <td
                        className={
                          i === 0
                            ? "base"
                            : c.result?.monthly_remaining !==
                                baseline.monthly_remaining
                              ? "chg"
                              : ""
                        }
                        key={c.name}
                      >
                        {c.result
                          ? fmt(c.result.monthly_remaining, h.base_currency)
                          : "等待有效条件"}
                        {i > 0 &&
                          c.result?.monthly_remaining != null &&
                          baseline.monthly_remaining != null && (
                            <span
                              className={`delta ${Number(c.result.monthly_remaining) === Number(baseline.monthly_remaining) ? "same" : Number(c.result.monthly_remaining) > Number(baseline.monthly_remaining) ? "good" : "bad"}`}
                            >
                              {Number(c.result.monthly_remaining) ===
                              Number(baseline.monthly_remaining)
                                ? ""
                                : `${Number(c.result.monthly_remaining) > Number(baseline.monthly_remaining) ? "＋" : "−"}${fmt(Math.abs(Number(c.result.monthly_remaining) - Number(baseline.monthly_remaining)), h.base_currency)}`}
                            </span>
                          )}
                      </td>
                    ))}
                  </tr>
                  {baseline.goals.map((g) => (
                    <Fragment key={g.id}>
                      <tr className="grp">
                        <td colSpan={columns.length + 1}>
                          {g.name} · 第 {g.priority} 优先
                        </td>
                      </tr>
                      {(
                        [
                          ["affordable_monthly", "每月投入"],
                          ["due", "到期日"],
                          ["projected", "到期测算"],
                          ["shortfall", "预计缺口"],
                        ] as const
                      ).map(([key, title]) => (
                        <tr key={key}>
                          <td>{title}</td>
                          {columns.map((c, i) => {
                            const goal = c.result?.goals.find(
                                (x) => x.id === g.id,
                              ),
                              value = goal?.[key],
                              base = g[key],
                              delta =
                                key === "due"
                                  ? null
                                  : value == null
                                    ? null
                                    : Number(value) - Number(base);
                            return (
                              <td
                                className={
                                  i === 0 ? "base" : value !== base ? "chg" : ""
                                }
                                key={c.name}
                              >
                                {value == null
                                  ? "待测算"
                                  : key === "due"
                                    ? value
                                    : fmt(value, h.base_currency)}
                                {i > 0 && value != null && (
                                  <span
                                    className={`delta ${delta == null || delta === 0 ? "same" : (key === "shortfall" ? delta < 0 : delta > 0) ? "good" : "bad"}`}
                                  >
                                    {key === "due"
                                      ? value === base
                                        ? ""
                                        : "已调整"
                                      : delta === 0
                                        ? ""
                                        : `${(delta ?? 0) > 0 ? "＋" : "−"}${fmt(Math.abs(delta ?? 0), h.base_currency)}`}
                                  </span>
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          {calcError && (
            <p className="summary-err" role="alert">
              {calcError}
            </p>
          )}
          {chosen &&
            (chosen.errors.length > 0 || chosen.missing.length > 0) && (
              <Issues result={chosen} />
            )}
          <div className="row">
            <Button
              primary={!pending}
              disabled={
                busy || Object.keys(errors).length > 0 || !chosen?.executable
              }
              onClick={() =>
                void act(
                  () =>
                    post("/plans", { ...request, expected_revision: revision }),
                  "方案草案已生成，请检查并确认",
                ).then((ok) => {
                  if (ok) setConfirmOpen(true);
                })
              }
            >
              用试算 {String.fromCharCode(65 + trials.selected)} 生成方案草案
            </Button>
          </div>
          {error && !pending && (
            <p className="summary-err" role="alert">
              {error}
            </p>
          )}
          <p className="hint">
            方案草案冻结当前账目与条件；之后账目变化时需重新生成。
          </p>
          <section className="sec">
            <div className="sec-head">
              <h2>方案版本</h2>
            </div>
            <div className="card pad">
              <ol className="vers">
                {plans.map((p) => (
                  <li className={p.status === "active" ? "on" : ""} key={p.id}>
                    <b>{label(p.status)}</b> ·{" "}
                    {new Date(p.created_at).toLocaleString("zh-CN", {
                      timeZone: "Asia/Shanghai",
                    })}
                    <p className="hint">
                      测算修订 {p.revision} · 确认不会自动交易或移动资金
                    </p>
                  </li>
                ))}
              </ol>
            </div>
            <details>
              <summary>查看当前方案条件与目标</summary>
              {active && <Assumptions result={active.result} />}
              <Goals result={baseline} />
            </details>
          </section>
        </section>
      </div>
    </>
  );
}
export function Assumptions({ result: R }: { result: Calculation }) {
  const a = R.assumptions;
  return (
    <div className="stack small">
      <p>
        每月额外收入 {fmt(String(a.extra_income ?? "0"), R.base_currency)}
        ；额外支出 {fmt(String(a.extra_expenses ?? "0"), R.base_currency)}
      </p>
      <p>
        全部目标延期 {String(a.extension_months ?? 0)} 个月；目标总下调{" "}
        {String(a.target_reduction_percent ?? 0)}%
      </p>
      <p>
        名义年收益 {Number(a.annual_return ?? 0) * 100}%；年通胀{" "}
        {Number(a.annual_inflation ?? 0) * 100}%
      </p>
      <p>{String(a.return_convention ?? "月末投入；收益不是承诺")}</p>
      <details>
        <summary>高级：核对原始假设</summary>
        <pre>{JSON.stringify(a, null, 2)}</pre>
      </details>
    </div>
  );
}

function ProposalFrame({
  stale,
  close,
  busy,
  children,
}: {
  stale: boolean;
  close: () => void;
  busy: boolean;
  children: ReactNode;
}) {
  return stale ? (
    <>{children}</>
  ) : (
    <Modal sheet title="确认方案" close={close} busy={busy}>
      {children}
    </Modal>
  );
}
