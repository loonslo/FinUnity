import { fmt } from "./format";
import { useRef, useState } from "react";
import type { Action, Calculation, Event, Household, Occurrence } from "./api";
import { errorMessage } from "./api";
import {
  atoms,
  changeKind,
  draftErrors,
  effects,
  emptyOccurrence,
  forAction,
  kinds,
} from "./execution";
import { day, yesterday } from "./sample";
import type { RecordDraft } from "./drafts";
import { Button, Field, Modal } from "./ui";

export function RecordSheet({
  h,
  result,
  actions,
  records,
  draft,
  setDraft,
  submit,
  close,
  storageOK,
  focusAction,
}: {
  h: Household;
  result: Calculation;
  actions: Action[];
  records: Event[];
  draft: RecordDraft;
  setDraft: (v: RecordDraft) => void;
  submit: (items: Occurrence[], key: string) => Promise<void>;
  close: () => void;
  storageOK: boolean;
  focusAction?: string;
}) {
  const [current, setCurrent] = useState(() =>
      Math.max(
        0,
        draft.items.findIndex((x) => x.action_id === focusAction),
      ),
    ),
    [errors, setErrors] = useState<string[][]>([]),
    [saving, setSaving] = useState(false),
    [serverError, setServerError] = useState("");
  const form = useRef<HTMLFormElement>(null),
    items = draft.items,
    index = Math.min(current, items.length - 1),
    item = items[index];
  function patch(values: Partial<Occurrence>) {
    setDraft({
      ...draft,
      items: items.map((x, i) => (i === index ? { ...x, ...values } : x)),
    });
    setErrors([]);
    setServerError("");
  }
  const money = (
    title: string,
    key: "amount" | "target_amount" | "principal",
  ) => {
    const fieldError = errors[index]?.find((x) =>
      key === "principal"
        ? /本金/.test(x)
        : key === "target_amount"
          ? /转入金额|收到的金额/.test(x)
          : /请填写金额|金额只填写|金额必须|余额不足|资金不足|超额还款/.test(x),
    );
    return (
      <Field title={title} error={fieldError}>
        <div className="pre l">
          <span>
            {(h.assets.find(
              (a) =>
                a.id ===
                (key === "target_amount"
                  ? item.target_asset_id
                  : item.asset_id),
            )?.currency ?? h.base_currency) === "CNY"
              ? "¥"
              : (h.assets.find(
                  (a) =>
                    a.id ===
                    (key === "target_amount"
                      ? item.target_asset_id
                      : item.asset_id),
                )?.currency ?? h.base_currency)}
          </span>
          <input
            className={`in ${fieldError ? "err" : ""}`}
            aria-invalid={!!fieldError || undefined}
            data-autofocus={key === "amount" || undefined}
            aria-label={title}
            inputMode="decimal"
            value={item[key] ?? ""}
            onChange={(e) => patch({ [key]: e.target.value.trim() || null })}
          />
        </div>
      </Field>
    );
  };
  async function save(another = false) {
    const validation = items.map((x) => draftErrors(h, x, actions));
    setErrors(validation);
    const first = validation.findIndex((x) => x.length);
    if (first >= 0) {
      setCurrent(first);
      requestAnimationFrame(() => {
        (
          form.current?.querySelector<HTMLElement>(
            ".in.err, [aria-invalid=true]",
          ) ?? form.current?.querySelector<HTMLElement>("input,select")
        )?.focus();
        form.current?.scrollIntoView({ block: "start" });
      });
      return;
    }
    setSaving(true);
    setServerError("");
    try {
      await submit(items, draft.batchKey);
      setDraft({ items: [emptyOccurrence(h)], batchKey: crypto.randomUUID() });
      if (!another) close();
      else {
        setCurrent(0);
        setErrors([]);
        requestAnimationFrame(() =>
          form.current
            ?.querySelector<HTMLInputElement>("[data-autofocus]")
            ?.focus(),
        );
      }
    } catch (error) {
      const detail = (
        error as { response?: { data?: { detail?: { item_index?: number } } } }
      ).response?.data?.detail;
      const errorIndex = detail?.item_index;
      if (errorIndex != null && errorIndex >= 0 && errorIndex < items.length) {
        setCurrent(errorIndex);
        setErrors(
          items.map((_, i) => (i === errorIndex ? [errorMessage(error)] : [])),
        );
      }
      if (errorIndex == null) setServerError(errorMessage(error));
      requestAnimationFrame(() =>
        (
          form.current?.querySelector<HTMLElement>(
            ".in.err, [aria-invalid=true]",
          ) ?? form.current?.querySelector<HTMLElement>("input,select")
        )?.focus(),
      );
    } finally {
      setSaving(false);
    }
  }
  const deltas = new Map<string, bigint>();
  const bump = (id: string, n: bigint) =>
    deltas.set(id, (deltas.get(id) ?? 0n) + n);
  items.forEach((x) => {
    const amount = atoms(x.amount);
    if (x.state !== "confirmed" || amount == null) return;
    bump(x.asset_id, x.kind === "income" ? amount : -amount);
    if (x.kind === "transfer" && x.target_asset_id)
      bump(x.target_asset_id, atoms(x.target_amount ?? x.amount) ?? 0n);
  });
  const preview = (
    <div className={`preview ${item.state !== "confirmed" ? "forecast" : ""}`}>
      <h4>
        {item.state === "confirmed"
          ? "整批保存后的余额预览"
          : "预计变化 · 不入账"}
      </h4>
      {item.state !== "confirmed"
        ? effects(h, item).map((x) => <p key={x}>{x}</p>)
        : [...deltas].map(([id, delta]) => {
            const asset = result.assets.find((a) => a.id === id),
              before = atoms(asset?.balance),
              after = before == null ? null : before + delta;
            return (
              <div className="pv-row" key={id}>
                <span>{asset?.name ?? "请选择账户"}</span>
                <span className={`d ${delta >= 0n ? "plus" : "minus"}`}>
                  {delta >= 0n ? "＋" : "−"}
                  {fmt(
                    Number(delta < 0n ? -delta : delta) / 1e8,
                    asset?.currency,
                  )}
                </span>
                <span className="range">
                  <span className="from">
                    {fmt(asset?.balance, asset?.currency)}
                  </span>
                  <span className="arrow"> → </span>
                  <b
                    className={
                      after != null && after < 0n
                        ? "negative-balance"
                        : undefined
                    }
                  >
                    {after != null && after < 0n && <small>余额不足 </small>}
                    {fmt(
                      after == null ? null : Number(after) / 1e8,
                      asset?.currency,
                    )}
                  </b>
                </span>
              </div>
            );
          })}
    </div>
  );
  return (
    <Modal
      sheet
      title="记下发生了什么"
      busy={saving}
      close={close}
      preview={preview}
      footer={
        <>
          <span className="autosave">
            {storageOK ? (
              <>
                <i />
                草稿已自动保存在这台电脑
              </>
            ) : (
              "本机存储不可用，草稿仅保留在本次页面"
            )}
          </span>
          <Button disabled={saving} onClick={close}>
            稍后再记
          </Button>
          <Button disabled={saving} onClick={() => void save(true)}>
            保存并再记一条
          </Button>
          <Button primary disabled={saving} onClick={() => void save()}>
            {saving ? "保存中…" : `保存 ${items.length} 条记录`}
          </Button>
        </>
      }
    >
      <div className="draft-list" aria-label="本批草稿">
        {items.map((x, i) => (
          <span
            className={`draft-chip ${errors[i]?.length ? "bad" : ""}`}
            aria-current={index === i ? "true" : undefined}
            key={x.client_key}
          >
            <button type="button" onClick={() => setCurrent(i)}>
              <span>
                第 {i + 1} 条 · {kinds[x.kind]}
              </span>
              <b>
                {x.amount
                  ? fmt(
                      x.amount,
                      h.assets.find((a) => a.id === x.asset_id)?.currency ??
                        h.base_currency,
                    )
                  : "待填金额"}
              </b>
              <small>
                {h.assets.find((a) => a.id === x.asset_id)?.name ?? "待选账户"}
              </small>
            </button>
            <button
              type="button"
              className="x"
              aria-label={`移除第 ${i + 1} 条草稿`}
              disabled={saving}
              onClick={() => {
                setDraft({
                  ...draft,
                  items:
                    items.length === 1
                      ? [emptyOccurrence(h)]
                      : items.filter((_, j) => j !== i),
                });
                setCurrent(Math.max(0, i - 1));
                setErrors([]);
                setServerError("");
              }}
            >
              ×
            </button>
          </span>
        ))}
        <Button
          className="ghost sm"
          disabled={items.length >= 50 || saving}
          onClick={() => {
            setDraft({ ...draft, items: [...items, emptyOccurrence(h)] });
            setCurrent(items.length);
          }}
        >
          ＋ 再加一条
        </Button>
      </div>
      {serverError && (
        <div className="summary-err" role="alert">
          {serverError}
        </div>
      )}
      {errors[index]?.length > 0 && (
        <div className="summary-err" role="alert">
          <div>
            <p>
              第 {index + 1} 条有 {errors[index].length}{" "}
              处问题，请核对下方字段。
            </p>
            {errors[index]
              .filter(
                (x) =>
                  !/金额|余额不足|资金不足|超额还款|本金|日期|未来/.test(x),
              )
              .map((x) => (
                <p key={x}>{x}</p>
              ))}
            {errors[index].some((x) => x.includes("日终")) && (
              <a href="#/household" onClick={close}>
                去核对建档日终日期
              </a>
            )}
          </div>
        </div>
      )}
      <form
        ref={form}
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <fieldset disabled={saving} className="stack record-fields">
          <div className="record-amount">
            {money(
              item.kind === "income"
                ? "增加金额"
                : item.kind === "transfer"
                  ? "转出金额"
                  : "减少金额",
              "amount",
            )}
          </div>
          <div className="choice-row" role="group" aria-label="金额变化类型">
            {Object.entries(kinds)
              .filter(([key]) => key !== "adjustment")
              .map(([key, title]) => (
                <button
                  type="button"
                  key={key}
                  aria-pressed={item.kind === key}
                  onClick={() =>
                    patch(changeKind(item, key as Occurrence["kind"]))
                  }
                >
                  {title}
                </button>
              ))}
          </div>
          {item.kind === "transfer" && (
            <Field title="关联投入计划">
              <select
                className="in"
                value={item.action_id ?? ""}
                onChange={(e) => {
                  const action = actions.find((a) => a.id === e.target.value);
                  patch(
                    action
                      ? { ...forAction(h, action), client_key: item.client_key }
                      : { action_id: null },
                  );
                }}
              >
                <option value="">不指定计划 / 普通转账</option>
                {actions
                  .filter((a) => a.can_record)
                  .map((a) => (
                    <option
                      key={a.id}
                      value={a.id}
                      disabled={items.some(
                        (x, i) => i !== index && x.action_id === a.id,
                      )}
                    >
                      {h.goals.find((g) => g.id === a.goal_id)?.name ?? a.name}{" "}
                      · {a.month}
                      {items.some((x, i) => i !== index && x.action_id === a.id)
                        ? " · 已在草稿中"
                        : ""}
                    </option>
                  ))}
              </select>
            </Field>
          )}
          <AccountPicker
            title={
              item.kind === "income"
                ? "收款账户（增加）"
                : "付款 / 转出账户（减少）"
            }
            accounts={h.assets.filter((a) => a.kind === "cash")}
            value={item.asset_id}
            change={(id) => patch({ asset_id: id })}
          />
          {item.kind === "transfer" && (
            <>
              <div className="grid2">
                <AccountPicker
                  title="转入账户（增加）"
                  accounts={h.assets.filter(
                    (a) =>
                      a.id !== item.asset_id &&
                      ["cash", "investment"].includes(a.kind) &&
                      a.liquidity === "available",
                  )}
                  value={item.target_asset_id ?? ""}
                  change={(id) =>
                    patch({ target_asset_id: id || null, target_amount: null })
                  }
                />
                {money("转入金额（同币种可留空）", "target_amount")}
              </div>
              <Field title="投入目标">
                <select
                  className="in"
                  value={item.goal_id ?? ""}
                  onChange={(e) =>
                    patch({ goal_id: e.target.value || null, action_id: null })
                  }
                >
                  <option value="">普通内部转账</option>
                  {h.goals.map((g) => (
                    <option value={g.id} key={g.id}>
                      {g.name}
                    </option>
                  ))}
                </select>
              </Field>
              {item.goal_id && (
                <Field title="资金用途">
                  <select
                    className="in"
                    value={item.allocation_mode}
                    onChange={(e) =>
                      patch({
                        allocation_mode: e.target
                          .value as Occurrence["allocation_mode"],
                        action_id:
                          e.target.value === "move_existing"
                            ? null
                            : item.action_id,
                      })
                    }
                  >
                    <option value="new_funding">新增投入</option>
                    <option value="move_existing">
                      移动已有资金（不计入进度）
                    </option>
                  </select>
                </Field>
              )}
            </>
          )}
          {item.kind === "debt_payment" && (
            <div className="grid2">
              <Field title="所还负债">
                <select
                  className="in"
                  value={item.debt_id ?? ""}
                  onChange={(e) => patch({ debt_id: e.target.value || null })}
                >
                  <option value="">请选择负债</option>
                  {h.debts.map((d) => (
                    <option value={d.id} key={d.id}>
                      {d.name}
                    </option>
                  ))}
                </select>
              </Field>
              {money("其中本金（纯利息填 0）", "principal")}
            </div>
          )}
          <div className="record-date">
            <Field
              title="日期"
              error={errors[index]?.find((x) => /日期|未来/.test(x))}
            >
              <input
                className="in"
                type="date"
                aria-invalid={
                  errors[index]?.some((x) => /日期|未来/.test(x)) || undefined
                }
                max={item.state === "confirmed" ? day() : undefined}
                value={item.date}
                onChange={(e) => patch({ date: e.target.value })}
              />
            </Field>
            <div className="date-chips">
              <button
                type="button"
                aria-pressed={item.date === day()}
                onClick={() => patch({ date: day() })}
              >
                今天
              </button>
              <button
                type="button"
                aria-pressed={item.date === yesterday()}
                onClick={() => patch({ date: yesterday() })}
              >
                昨天
              </button>
            </div>
          </div>{" "}
          <details className="more">
            <summary>
              更多选项 ·{" "}
              {item.state === "confirmed"
                ? "已发生"
                : item.state === "forecast"
                  ? "预计"
                  : "待核对"}
              、来源与关联
            </summary>
            <div className="stack">
              <div className="field">
                <span className="lab">发生状态</span>
                <div className="choice-row" role="group" aria-label="发生状态">
                  {(
                    [
                      ["confirmed", "已发生"],
                      ["forecast", "预计 · 不入账"],
                      ["pending", "待核对 · 不入账"],
                    ] as const
                  ).map(([key, title]) => (
                    <button
                      type="button"
                      key={key}
                      aria-pressed={item.state === key}
                      onClick={() => patch({ state: key })}
                    >
                      {title}
                    </button>
                  ))}
                </div>
              </div>
              <p className="hint">
                已发生记录须晚于账户日终日期；预计与待核对均不入账。
              </p>
              <Field title="数值质量">
                <select
                  className="in"
                  value={item.quality}
                  onChange={(e) =>
                    patch({ quality: e.target.value as Occurrence["quality"] })
                  }
                >
                  <option value="verified">已核实</option>
                  <option value="estimated">估算</option>
                  <option value="unknown">未知（仅预计或待核对）</option>
                </select>
              </Field>
              <Field title="记录来源">
                <input
                  className="in"
                  value={item.source}
                  maxLength={200}
                  onChange={(e) => patch({ source: e.target.value })}
                />
              </Field>
              <Field title="关联预测或待核对记录">
                <select
                  className="in"
                  value={item.settles_id ?? ""}
                  onChange={(e) => {
                    const old = records.find((x) => x.id === e.target.value);
                    patch(
                      old
                        ? {
                            ...old.event,
                            client_key: item.client_key,
                            state: "confirmed",
                            date: day(),
                            settles_id: old.id,
                          }
                        : { settles_id: null },
                    );
                  }}
                >
                  <option value="">新记录</option>
                  {records
                    .filter((r) => ["forecast", "pending"].includes(r.status))
                    .map((r) => (
                      <option value={r.id} key={r.id}>
                        {r.event.date} · {kinds[r.event.kind]} ·{" "}
                        {r.event.amount ?? "未知金额"}
                      </option>
                    ))}
                </select>
              </Field>
              <Field title="备注">
                <textarea
                  className="in"
                  value={item.note}
                  maxLength={1000}
                  onChange={(e) => patch({ note: e.target.value })}
                />
              </Field>
            </div>
          </details>
        </fieldset>
      </form>
    </Modal>
  );
}

export function RecordDetails({
  household,
  record,
}: {
  household: Household;
  record: Event;
}) {
  return (
    <span className="eff">
      {effects(household, record.event).map((line) => (
        <span key={line}>{line}</span>
      ))}
    </span>
  );
}

function AccountPicker({
  title,
  accounts,
  value,
  change,
}: {
  title: string;
  accounts: Household["assets"];
  value: string;
  change: (id: string) => void;
}) {
  return accounts.length > 4 ? (
    <Field title={title}>
      <select
        className="in"
        value={value}
        onChange={(e) => change(e.target.value)}
      >
        <option value="">请选择账户</option>
        {accounts.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name} · {a.currency}
          </option>
        ))}
      </select>
    </Field>
  ) : (
    <div className="field">
      <span className="lab">{title}</span>
      <div
        className="choice-row account-options"
        role="group"
        aria-label={title}
      >
        {accounts.map((a) => (
          <button
            type="button"
            key={a.id}
            aria-pressed={value === a.id}
            onClick={() => change(a.id)}
          >
            {a.name}
            <small>{a.currency}</small>
          </button>
        ))}
        {!accounts.length && (
          <p className="hint">暂无可用账户，请先到资料页补充。</p>
        )}
      </div>
    </div>
  );
}
