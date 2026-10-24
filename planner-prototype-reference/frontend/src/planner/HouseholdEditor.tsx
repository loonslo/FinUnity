import { fmt } from "./format";
import { useState } from "react";
import { post, errorMessage, type Household } from "./api";
import { day, yesterday } from "./sample";
import { Button, Field, PageHead, SectionHead } from "./ui";

const names: Record<string, string> = {
  cash: "现金账户",
  investment: "投资账户",
  property: "房产",
  other: "其他资产",
  available: "可动用",
  restricted: "受限",
  illiquid: "不易变现",
  verified: "已核实",
  estimated: "估算",
  unknown: "未知",
};
function Input({
  title,
  value,
  change,
  type = "text",
  max,
  hint,
}: {
  title: string;
  value: string | null;
  change: (v: string) => void;
  type?: string;
  max?: string;
  hint?: string;
}) {
  const [focused, setFocused] = useState(false);
  const monetary =
    type === "text" && /金额|余额|收入|支出|还款|投入/.test(title);
  const shown =
    monetary && !focused && value && /^-?\d+(\.\d*)?$/.test(value)
      ? value
          .split(".")
          .map((part, i) =>
            i === 0 ? part.replace(/\B(?=(\d{3})+(?!\d))/g, ",") : part,
          )
          .join(".")
      : (value ?? "");
  return (
    <Field title={title} hint={hint}>
      <input
        className="in"
        value={shown}
        type={type}
        max={max}
        inputMode={
          type === "text" && /金额|余额|收入|支出|还款|投入/.test(title)
            ? "decimal"
            : undefined
        }
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={(e) =>
          change(monetary ? e.target.value.replaceAll(",", "") : e.target.value)
        }
      />
    </Field>
  );
}
function Select({
  title,
  value,
  change,
  values,
}: {
  title: string;
  value: string;
  change: (v: string) => void;
  values: Record<string, string>;
}) {
  return (
    <Field title={title}>
      <select
        className="in"
        value={value}
        onChange={(e) => change(e.target.value)}
      >
        {Object.entries(values).map(([k, name]) => (
          <option key={k} value={k}>
            {name}
          </option>
        ))}
      </select>
    </Field>
  );
}
const choices = (...values: string[]) =>
  Object.fromEntries(values.map((x) => [x, names[x] ?? x]));

export function HouseholdEditor({
  h,
  set,
  changes,
  conflict,
  busy,
  save,
  discard,
  example,
  error,
  revision,
}: {
  h: Household;
  set: (h: Household) => void;
  changes: number;
  conflict: boolean;
  busy: boolean;
  save: () => void;
  discard: () => void;
  example: () => void;
  error: string;
  revision: number;
}) {
  const [raw, setRaw] = useState("");
  const [step, setStep] = useState(0);
  const wizard = revision === 0;
  const [parseError, setParseError] = useState("");
  const patch = <K extends keyof Household>(key: K, value: Household[K]) =>
    set({ ...h, [key]: value });
  const remove = (
    key: "assets" | "debts" | "goals" | "commitments" | "rates",
    index: number,
  ) => {
    const row = h[key][index];
    const id = "id" in row ? row.id : "";
    const next = { ...h, [key]: h[key].filter((_, i) => i !== index) };
    if (key === "assets")
      next.allocations = h.allocations.filter((a) => a.asset_id !== id);
    if (key === "goals") {
      next.allocations = h.allocations.filter((a) => a.goal_id !== id);
      next.commitments = h.commitments.map((c) =>
        c.goal_id === id ? { ...c, goal_id: null } : c,
      );
      next.goals = next.goals.map((g, i) => ({ ...g, priority: i + 1 }));
    }
    set(next);
  };
  function move(index: number, direction: number) {
    const goals = [...h.goals];
    [goals[index], goals[index + direction]] = [
      goals[index + direction],
      goals[index],
    ];
    patch(
      "goals",
      goals.map((g, i) => ({ ...g, priority: i + 1 })),
    );
  }
  const dateMax = yesterday();
  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        if (wizard && step < 2) setStep(step + 1);
        else save();
      }}
    >
      <PageHead
        eyebrow={revision ? "资料已保存" : `未开始 · 第 ${step + 1}/3 步`}
        title="资料"
        lede="余额是所选日期的日终记录；月收支用于预测，不代表已经到账。"
        meta={
          !revision && (
            <Button className="sm" onClick={example}>
              用示例体验
            </Button>
          )
        }
      />
      {wizard && (
        <div
          className="choice-row setup-steps"
          role="group"
          aria-label="建档步骤"
        >
          {["收支", "账户与负债", "目标与核对"].map((title, i) => (
            <button
              key={title}
              type="button"
              aria-pressed={step === i}
              onClick={() => setStep(i)}
            >
              {i + 1}. {title}
            </button>
          ))}
        </div>
      )}
      <div className={`dirtybar ${changes ? "dirty" : ""}`} role="status">
        <span className="t">
          {changes ? (
            <>
              <b>有 {changes} 处未保存的修改</b>
              <span className="muted small">
                {" "}
                · 测算、记账、刷新都不会清掉它们。
              </span>
            </>
          ) : (
            <span className="muted">
              {revision
                ? "已保存。没有修改时不需要保存。"
                : "填好账户、收支和目标后保存。"}
            </span>
          )}
        </span>
        {changes > 0 && (
          <Button className="sm" onClick={discard} disabled={busy}>
            放弃修改
          </Button>
        )}
        <button
          className="btn primary sm"
          hidden={!wizard || step === 2}
          disabled={busy || ((!wizard || step === 2) && !changes) || conflict}
          type="submit"
        >
          {wizard && step < 2 ? "下一步" : "保存建档"}
        </button>
      </div>
      {conflict && (
        <p className="summary-err">
          保存的建档已在其他页面变化。草稿已保留；请先核对差异，或放弃草稿读取最新建档，避免覆盖新数据。
        </p>
      )}
      {error && (
        <p className="summary-err" role="alert">
          {error}
        </p>
      )}
      <section className="h-sec" hidden={wizard && step !== 0}>
        <SectionHead no="01" title="基本信息与月收支" />
        <div className="card pad">
          <div className="h-grid">
            <Input
              title="名称"
              value={h.name}
              change={(v) => patch("name", v)}
            />
            <Input
              title="余额截至（日终）"
              value={h.as_of}
              type="date"
              max={dateMax}
              change={(v) => patch("as_of", v)}
              hint={`请选择已经结束的一天（最晚 ${yesterday()}）。今天的收支请到「执行」里记。`}
            />
            <Input
              title="基础币种"
              value={h.base_currency}
              change={(v) => patch("base_currency", v.toUpperCase())}
            />
            <Input
              title="月收入（税后）"
              value={h.income}
              change={(v) => patch("income", v || null)}
            />
            <Input
              title="月总支出"
              value={h.expenses}
              change={(v) => patch("expenses", v || null)}
            />
            <Input
              title="其中必要支出"
              value={h.essential_expenses}
              change={(v) => patch("essential_expenses", v || null)}
              hint="应急预留 = 必要支出 × 月数。"
            />
            <Field title="应急预留月数" hint="默认 6 个月，可调整。">
              <div className="stepper">
                <button
                  type="button"
                  aria-label="少 1 个月"
                  disabled={h.reserve_months <= 0}
                  onClick={() => patch("reserve_months", h.reserve_months - 1)}
                >
                  −
                </button>
                <output>{h.reserve_months} 个月</output>
                <button
                  type="button"
                  aria-label="多 1 个月"
                  disabled={h.reserve_months >= 60}
                  onClick={() => patch("reserve_months", h.reserve_months + 1)}
                >
                  ＋
                </button>
              </div>
            </Field>
            <Select
              title="风险偏好"
              value={h.risk_tolerance}
              values={{
                unknown: "尚未明确",
                cautious: "谨慎",
                balanced: "平衡",
                high: "能接受较大波动",
              }}
              change={(v) =>
                patch("risk_tolerance", v as Household["risk_tolerance"])
              }
            />
            <Input
              title="收支来源"
              value={h.source}
              change={(v) => patch("source", v)}
            />
          </div>
          <div style={{ marginTop: 16 }}>
            <Field title="个人约束、用途与期限">
              <textarea
                className="in"
                value={h.constraints}
                onChange={(e) => patch("constraints", e.target.value)}
              />
            </Field>
          </div>
        </div>
      </section>
      <section className="h-sec" hidden={wizard && step !== 1}>
        <SectionHead
          no="02"
          title="账户与资产"
          aside="每个账户的余额是「所选日期日终」的数"
        />
        <div className="card pad">
          <div className="row-head account-head" aria-hidden="true">
            {["名称", "类型", "流动性", "余额"].map((x) => (
              <span key={x}>{x}</span>
            ))}
          </div>
          {h.assets.map((a, i) => {
            const update = (p: Partial<typeof a>) =>
              patch(
                "assets",
                h.assets.map((x, j) => (j === i ? { ...a, ...p } : x)),
              );
            return (
              <div className="acct" key={a.id}>
                <Input
                  title="名称"
                  value={a.name}
                  change={(v) => update({ name: v })}
                />
                <Select
                  title="类型"
                  value={a.kind}
                  values={choices("cash", "investment", "property", "other")}
                  change={(v) =>
                    update({
                      kind: v as typeof a.kind,
                      liquidity: v === "property" ? "illiquid" : a.liquidity,
                    })
                  }
                />
                <Select
                  title="流动性"
                  value={a.liquidity}
                  values={choices("available", "restricted", "illiquid")}
                  change={(v) => update({ liquidity: v as typeof a.liquidity })}
                />
                <Input
                  title="余额"
                  value={a.amount}
                  change={(v) => update({ amount: v || null })}
                />
                <div className="more">
                  <details>
                    <summary>
                      来源：{a.source} · 币种 {a.currency}
                    </summary>
                    <div className="grid2">
                      {" "}
                      <Input
                        title="日终日期"
                        type="date"
                        max={h.as_of}
                        value={a.observed_on}
                        change={(v) => update({ observed_on: v })}
                      />
                      <Select
                        title="数值质量"
                        value={a.quality}
                        values={choices("verified", "estimated", "unknown")}
                        change={(v) =>
                          update({ quality: v as typeof a.quality })
                        }
                      />
                      <Input
                        title="来源"
                        value={a.source}
                        change={(v) => update({ source: v })}
                      />
                      <Input
                        title="币种"
                        value={a.currency}
                        change={(v) => update({ currency: v.toUpperCase() })}
                      />
                    </div>
                  </details>
                  <button
                    className="linkbtn"
                    type="button"
                    onClick={() => remove("assets", i)}
                  >
                    移除这个账户
                  </button>
                </div>
              </div>
            );
          })}
          {!h.assets.length && (
            <p className="muted">还没有账户。点下面添加。</p>
          )}
          <Button
            className="sm add-button"
            onClick={() =>
              patch("assets", [
                ...h.assets,
                {
                  id: crypto.randomUUID(),
                  name: "新账户",
                  kind: "cash",
                  liquidity: "available",
                  currency: h.base_currency,
                  amount: null,
                  observed_on: h.as_of,
                  quality: "verified",
                  source: "手动录入",
                },
              ])
            }
          >
            ＋ 添加账户
          </Button>
        </div>
      </section>
      <section className="h-sec" hidden={wizard && step !== 1}>
        <SectionHead no="03" title="负债与最低还款" />
        <div className="card pad">
          <div className="row-head debt-head" aria-hidden="true">
            {["名称", "余额", "每月最低还款", "已计入支出"].map((x) => (
              <span key={x}>{x}</span>
            ))}
          </div>
          {h.debts.map((d, i) => {
            const update = (p: Partial<typeof d>) =>
              patch(
                "debts",
                h.debts.map((x, j) => (i === j ? { ...d, ...p } : x)),
              );
            return (
              <div className="acct debt-row" key={d.id}>
                <Input
                  title="名称"
                  value={d.name}
                  change={(v) => update({ name: v })}
                />
                <Input
                  title="余额"
                  value={d.balance}
                  change={(v) => update({ balance: v || null })}
                />
                <Input
                  title="每月最低还款"
                  value={d.minimum_payment}
                  change={(v) => update({ minimum_payment: v || null })}
                />
                <label className="consent">
                  <input
                    type="checkbox"
                    checked={d.payment_in_expenses}
                    onChange={(e) =>
                      update({ payment_in_expenses: e.target.checked })
                    }
                  />
                  已包含在月总支出里
                </label>
                <div className="more">
                  <Input
                    title="币种"
                    value={d.currency}
                    change={(v) => update({ currency: v.toUpperCase() })}
                  />
                  <button
                    className="linkbtn"
                    type="button"
                    onClick={() => remove("debts", i)}
                  >
                    移除
                  </button>
                </div>
              </div>
            );
          })}
          <Button
            className="sm add-button"
            onClick={() =>
              patch("debts", [
                ...h.debts,
                {
                  id: crypto.randomUUID(),
                  name: "新负债",
                  balance: null,
                  currency: h.base_currency,
                  minimum_payment: null,
                  payment_in_expenses: false,
                  source: "手动录入",
                },
              ])
            }
          >
            ＋ 添加负债
          </Button>
        </div>
      </section>
      <section className="h-sec" hidden={wizard && step !== 2}>
        <SectionHead
          no="04"
          title="目标与优先级"
          aside="排在上面的目标，资金不够时先拿到每月预算"
        />
        <div className="card pad">
          <div className="row-head goal-head" aria-hidden="true">
            {["优先级", "目标", "目标金额", "到期日期", "每月投入"].map((x) => (
              <span key={x}>{x}</span>
            ))}
          </div>
          {h.goals.map((g, i) => {
            const update = (p: Partial<typeof g>) =>
              patch(
                "goals",
                h.goals.map((x, j) => (i === j ? { ...g, ...p } : x)),
              );
            return (
              <div className="goal-row" key={g.id}>
                <div className="rank">
                  <button
                    type="button"
                    aria-label={`上移${g.name}`}
                    disabled={i === 0}
                    onClick={() => move(i, -1)}
                  >
                    ↑
                  </button>
                  <b>{i + 1}</b>
                  <button
                    type="button"
                    aria-label={`下移${g.name}`}
                    disabled={i === h.goals.length - 1}
                    onClick={() => move(i, 1)}
                  >
                    ↓
                  </button>
                </div>
                <div>
                  <Input
                    title="目标"
                    value={g.name}
                    change={(v) => update({ name: v })}
                  />
                  <button
                    className="linkbtn small"
                    type="button"
                    onClick={() => remove("goals", i)}
                  >
                    移除
                  </button>
                </div>
                <Input
                  title="目标金额"
                  value={g.target}
                  change={(v) => update({ target: v })}
                />
                <Input
                  title="到期日期"
                  type="date"
                  value={g.target_date}
                  change={(v) => update({ target_date: v })}
                />
                <Input
                  title="每月投入"
                  value={g.monthly}
                  change={(v) => update({ monthly: v })}
                />
              </div>
            );
          })}
          <Button
            className="sm add-button"
            onClick={() =>
              patch("goals", [
                ...h.goals,
                {
                  id: crypto.randomUUID(),
                  name: "新目标",
                  target: "10000",
                  target_date: `${Number(day().slice(0, 4)) + 1}-12-31`,
                  monthly: "0",
                  priority: h.goals.length + 1,
                },
              ])
            }
          >
            ＋ 添加目标
          </Button>
        </div>
      </section>
      <section className="h-sec" hidden={wizard && step !== 2}>
        <SectionHead
          no="05"
          title="现有资金分配"
          aside="只是用途标记，不会移动资金"
        />
        <div className="card pad">
          <div className="ledger-wrap">
            <table className="matrix">
              <thead>
                <tr>
                  <th>账户</th>
                  {h.goals.map((g) => (
                    <th key={g.id}>{g.name}</th>
                  ))}
                  <th>已分配（原币）</th>
                </tr>
              </thead>
              <tbody>
                {h.assets
                  .filter(
                    (a) =>
                      ["cash", "investment"].includes(a.kind) &&
                      a.liquidity === "available",
                  )
                  .map((a) => (
                    <tr key={a.id}>
                      <td>
                        <b>{a.name}</b>
                        <div className="hint">
                          余额 {fmt(a.amount, a.currency)}
                        </div>
                      </td>
                      {h.goals.map((g) => (
                        <td key={g.id}>
                          <input
                            className="in"
                            inputMode="decimal"
                            aria-label={`${a.name} 分给 ${g.name}`}
                            value={
                              h.allocations.find(
                                (x) =>
                                  x.asset_id === a.id && x.goal_id === g.id,
                              )?.amount ?? ""
                            }
                            onChange={(e) => {
                              const rest = h.allocations.filter(
                                (x) =>
                                  x.asset_id !== a.id || x.goal_id !== g.id,
                              );
                              patch(
                                "allocations",
                                e.target.value
                                  ? [
                                      ...rest,
                                      {
                                        asset_id: a.id,
                                        goal_id: g.id,
                                        amount: e.target.value,
                                      },
                                    ]
                                  : rest,
                              );
                            }}
                          />
                        </td>
                      ))}
                      <td>
                        {fmt(
                          h.allocations
                            .filter((x) => x.asset_id === a.id)
                            .reduce((n, x) => n + Number(x.amount), 0),
                          a.currency,
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <p className="hint">
            应急预留先扣，不能再分给目标；同一笔钱不能重复分配。分配有效性由后端保存时核对。
          </p>
        </div>
      </section>
      <section className="h-sec" hidden={wizard && step !== 2}>
        <SectionHead no="06" title="已有定投与资金承诺" />
        <div className="card pad">
          {h.commitments.map((c, i) => {
            const update = (p: Partial<typeof c>) =>
              patch(
                "commitments",
                h.commitments.map((x, j) => (i === j ? { ...c, ...p } : x)),
              );
            return (
              <div className="acct commitment-row" key={c.id}>
                <Input
                  title="名称"
                  value={c.name}
                  change={(v) => update({ name: v })}
                />
                <Input
                  title="每月金额"
                  value={c.monthly}
                  change={(v) => update({ monthly: v })}
                />
                <Select
                  title="对应目标"
                  value={c.goal_id ?? ""}
                  values={{
                    "": "独立承诺（不属于任何目标）",
                    ...Object.fromEntries(h.goals.map((g) => [g.id, g.name])),
                  }}
                  change={(v) => update({ goal_id: v || null })}
                />
                <div className="more">
                  <span>关联后计入该目标每月投入的下限，不会重复扣减。</span>
                  <button
                    className="linkbtn"
                    type="button"
                    onClick={() => remove("commitments", i)}
                  >
                    移除
                  </button>
                </div>
              </div>
            );
          })}
          {!h.commitments.length && <p className="muted">没有定投。</p>}
          <Button
            className="sm add-button"
            onClick={() =>
              patch("commitments", [
                ...h.commitments,
                {
                  id: crypto.randomUUID(),
                  name: "新定投",
                  monthly: "0",
                  goal_id: null,
                },
              ])
            }
          >
            ＋ 添加定投
          </Button>
        </div>
      </section>
      <section className="h-sec stack" hidden={wizard && step !== 2}>
        <div className="card pad">
          <details>
            <summary>外币汇率 · 手动核对</summary>
            {h.rates.map((r, i) => (
              <div className="h-grid" key={i}>
                <Input
                  title="币种"
                  value={r.currency}
                  change={(v) =>
                    patch(
                      "rates",
                      h.rates.map((x, j) =>
                        j === i ? { ...r, currency: v.toUpperCase() } : x,
                      ),
                    )
                  }
                />
                <Input
                  title={`1 单位外币折合 ${h.base_currency}`}
                  value={r.to_base}
                  change={(v) =>
                    patch(
                      "rates",
                      h.rates.map((x, j) =>
                        j === i ? { ...r, to_base: v } : x,
                      ),
                    )
                  }
                />
                <Input
                  title="汇率日期"
                  value={r.observed_on}
                  type="date"
                  max={h.as_of}
                  change={(v) =>
                    patch(
                      "rates",
                      h.rates.map((x, j) =>
                        j === i ? { ...r, observed_on: v } : x,
                      ),
                    )
                  }
                />
                <Input
                  title="汇率来源"
                  value={r.source}
                  change={(v) =>
                    patch(
                      "rates",
                      h.rates.map((x, j) =>
                        j === i ? { ...r, source: v } : x,
                      ),
                    )
                  }
                />
                <button
                  type="button"
                  className="linkbtn"
                  onClick={() => remove("rates", i)}
                >
                  移除
                </button>
              </div>
            ))}
            <Button
              className="sm add-button"
              onClick={() =>
                patch("rates", [
                  ...h.rates,
                  {
                    currency: "USD",
                    to_base: "1",
                    observed_on: h.as_of,
                    source: "手动填写",
                  },
                ])
              }
            >
              ＋ 添加汇率
            </Button>
            <p className="hint">缺少汇率的资产不会被当成零。</p>
          </details>
        </div>
        <div className="card pad">
          <details>
            <summary>高级：导入或核对建档 JSON</summary>
            <textarea
              className="in"
              aria-label="建档 JSON"
              value={raw}
              onChange={(e) => setRaw(e.target.value)}
            />
            <Button
              onClick={async () => {
                try {
                  const value = await post<Household>(
                    "/household/validate",
                    JSON.parse(raw),
                  );
                  set(value);
                  setParseError("");
                } catch (error) {
                  setParseError(
                    error instanceof SyntaxError
                      ? "JSON 格式无效，请检查后重试。"
                      : errorMessage(error),
                  );
                }
              }}
            >
              应用到草稿
            </Button>
            {parseError && <p className="errmsg">{parseError}</p>}
          </details>
        </div>
      </section>
      <section className="h-sec card pad" hidden={wizard && step !== 2}>
        <h2>保存前核对</h2>{" "}
        <div className="row" style={{ marginTop: 20 }}>
          {(
            [
              ["assets_complete", "资产已完整填写"],
              ["income_complete", "收入已完整填写"],
              ["expenses_complete", "支出已完整填写"],
              ["debts_complete", "负债已完整填写（没有也请确认）"],
            ] as const
          ).map(([key, title]) => (
            <label className="consent small" key={key}>
              <input
                type="checkbox"
                checked={h[key]}
                onChange={(e) => patch(key, e.target.checked)}
              />
              {title}
            </label>
          ))}
        </div>
        <p className="hint">
          未确认完整的资料继续显示为待补充，不会当作零。保存后仍可修改。
        </p>
        <div className="row">
          {wizard && <Button onClick={() => setStep(1)}>上一步</Button>}
          <Button primary type="submit" disabled={busy || !changes || conflict}>
            保存建档
          </Button>
        </div>
      </section>
    </form>
  );
}
