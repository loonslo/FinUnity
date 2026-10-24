import { fmt } from "./format";
import { useCallback, useEffect, useState, useRef } from "react";
import ReactMarkdown from "react-markdown";
import {
  get,
  post,
  errorMessage,
  type Snapshot,
  type Plan,
  type Action,
  type Event,
  type Review,
  type Task,
  type PlanRequest,
  type Household,
  type Occurrence,
} from "./api";
import { day, example, yesterday } from "./sample";
import {
  changeKind,
  emptyOccurrence,
  forAction,
  isEmptyDraft,
  effects,
  kinds,
  atoms,
} from "./execution";
import {
  HOUSEHOLD_DRAFT_KEY,
  RECORD_DRAFT_KEY,
  diffCount,
  householdDraft,
  mergeHouseholdDraft,
  readLocal,
  recordDraft,
  storeLocal,
  type HouseholdDraft,
  type RecordDraft,
} from "./drafts";
import { HouseholdEditor } from "./HouseholdEditor";
import { Overview, ActionCard, ReviewCard } from "./Summary";
import { Scenarios, type Trials } from "./Scenarios";
import { RecordSheet, RecordDetails } from "./RecordSheet";
import {
  Button,
  Chip,
  Field,
  Icon,
  LedgerLine,
  Modal,
  PageHead,
  SectionHead,
} from "./ui";
import { chooseTheme, currentChoice, onChoiceChange } from "../themeRoot";
import { label } from "./i18n";
import { ReviewTrend } from "./ReviewTrend";

const routes = ["overview", "execution", "scenarios", "household"] as const;
type Route = (typeof routes)[number];
const routeNames = {
  overview: "概览",
  household: "资料",
  scenarios: "方案",
  execution: "本月",
};
const icons = ["home", "check", "sliders", "book"];
function monthKey() {
  return day().slice(0, 7);
}
function route(): Route {
  const value = window.location.hash.slice(2).split("?")[0];
  return routes.includes(value as Route) ? (value as Route) : "overview";
}
type Overlay =
  | "record"
  | "assistant"
  | "example"
  | "discard"
  | "void"
  | "reconcile"
  | null;

export default function PlannerApp() {
  const initialized = useRef(false);
  const clockMonth = useRef(monthKey());
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null),
    [draft, setDraft] = useState<HouseholdDraft | null>(() =>
      readLocal(HOUSEHOLD_DRAFT_KEY, householdDraft),
    );
  const [recordsDraft, setRecordsDraft] = useState<RecordDraft | null>(() =>
    readLocal(RECORD_DRAFT_KEY, recordDraft),
  );
  const [tab, setTab] = useState<Route>(route),
    [plans, setPlans] = useState<Plan[]>([]),
    [actions, setActions] = useState<Action[]>([]),
    [events, setEvents] = useState<Event[]>([]),
    [reviews, setReviews] = useState<Review[]>([]),
    [tasks, setTasks] = useState<Task[]>([]);
  const [configured, setConfigured] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [storageOK, setStorageOK] = useState(true),
    [overlay, setOverlay] = useState<Overlay>(null),
    [focusAction, setFocusAction] = useState<string | undefined>();
  const [trials, setTrials] = useState<Trials>({
      selected: 0,
      requests: [{ scenario: {}, monthly: {} }],
    }),
    [message, setMessage] = useState(""),
    [theme, setTheme] = useState(currentChoice),
    [ledgerMonth, setLedgerMonth] = useState(monthKey()),
    [filter, setFilter] = useState("all"),
    [voidRecord, setVoidRecord] = useState<Event | null>(null);
  const [recon, setRecon] = useState({
    assetId: "",
    value: "",
    source: "银行余额核对",
    key: "",
  });
  const month = day().slice(0, 7),
    dayNumber = Number(day().slice(8)),
    days = new Date(`${month}-01T12:00:00Z`);
  days.setUTCMonth(days.getUTCMonth() + 1, 0);
  const monthDays = days.getUTCDate();
  const running = tasks.some((t) => ["running", "queued"].includes(t.status));
  const close = useCallback(() => setOverlay(null), []);
  const refresh = useCallback(async () => {
    const [s, p, a, e, r, t, config] = await Promise.all([
      get<Snapshot>("/state"),
      get<Plan[]>("/plans"),
      get<Action[]>("/actions"),
      get<Event[]>("/occurrences"),
      get<Review[]>("/reviews"),
      get<Task[]>("/assistant/tasks"),
      get<{ configured: boolean }>("/assistant"),
    ]);
    if (s.revision === 0) s.household = { ...s.household, as_of: yesterday() };
    if (!initialized.current) {
      initialized.current = true;
      setTrials({
        selected: 0,
        requests: [
          p.find((plan) => plan.status === "active")?.request ?? {
            scenario: {},
            monthly: {},
          },
        ],
      });
    }
    const previousClockMonth = clockMonth.current;
    const nextClockMonth = monthKey();
    if (previousClockMonth !== nextClockMonth) {
      setLedgerMonth((old) =>
        old === previousClockMonth ? nextClockMonth : old,
      );
      clockMonth.current = nextClockMonth;
    }
    setSnapshot(s);
    setDraft((old) => mergeHouseholdDraft(old, s.household));
    setPlans(p);
    setActions(a);
    setEvents(e);
    setReviews(r);
    setTasks(t);
    setConfigured(config.configured);
  }, []);
  useEffect(() => {
    let alive = true;
    void Promise.resolve()
      .then(() => {
        if (alive) return refresh();
      })
      .catch((e) => {
        if (alive) setError(errorMessage(e));
      });
    return () => {
      alive = false;
    };
  }, [refresh]);
  useEffect(() => {
    const changed = () => {
      setTab(route());
      setError("");
    };
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  useEffect(() => {
    if (!running) return;
    let alive = true;
    const timer = setInterval(() => {
      void Promise.all([get<Task[]>("/assistant/tasks"), get<Plan[]>("/plans")])
        .then(([t, p]) => {
          if (alive) {
            setTasks(t);
            setPlans(p);
          }
        })
        .catch((e) => {
          if (alive) setError(errorMessage(e));
        });
    }, 1500);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [running]);
  useEffect(() => onChoiceChange(setTheme), []);
  useEffect(() => {
    let inflight = false;
    const focus = () => {
      if (busy || overlay || inflight || document.visibilityState === "hidden")
        return;
      inflight = true;
      void refresh()
        .catch((e) => setError(errorMessage(e)))
        .finally(() => {
          inflight = false;
        });
    };
    window.addEventListener("focus", focus);
    document.addEventListener("visibilitychange", focus);
    return () => {
      window.removeEventListener("focus", focus);
      document.removeEventListener("visibilitychange", focus);
    };
  }, [refresh, busy, overlay]);
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (
        e.ctrlKey ||
        e.metaKey ||
        e.altKey ||
        e.repeat ||
        busy ||
        overlay ||
        document.querySelector('[role="dialog"]')
      )
        return;
      if (
        (e.target as HTMLElement)?.closest(
          'input,textarea,select,[contenteditable="true"]',
        )
      )
        return;
      const r = routes[Number(e.key) - 1];
      if (r) {
        e.preventDefault();
        window.location.hash = "/" + r;
      } else if (e.key.toLowerCase() === "n" && snapshot?.revision) {
        e.preventDefault();
        document.querySelector<HTMLButtonElement>(".record-entry")?.click();
      }
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, [busy, overlay, snapshot?.revision]);
  const loaded = snapshot != null;
  useEffect(() => {
    const nodes = document.querySelectorAll<HTMLElement>(
      ".hero,.sec,.h-sec,.scen,.empty",
    );
    nodes.forEach((node, index) => {
      node.classList.add("rv");
      node.style.setProperty("--i", String(Math.min(index, 4)));
    });
  }, [tab, loaded]);
  const changes = draft ? diffCount(draft.base, draft.value) : 0;
  const recordChanges =
    recordsDraft?.items.some(
      (x) => snapshot && !isEmptyDraft(snapshot.household, x),
    ) ?? false;
  useEffect(() => {
    if (!changes && !recordChanges) return;
    const before = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", before);
    return () => window.removeEventListener("beforeunload", before);
  }, [changes, recordChanges]);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 6500);
    return () => clearTimeout(timer);
  }, [notice]);
  const navigate = (r: Route) => {
    window.location.assign(`#/${r}`);
    setTab(r);
    setError("");
  };
  function updateHousehold(value: Household) {
    if (!draft) return;
    const next = {
      ...draft,
      value: {
        ...value,
        goals: value.goals.map((g, i) => ({ ...g, priority: i + 1 })),
      },
    };
    setDraft(next);
    setStorageOK(storeLocal(HOUSEHOLD_DRAFT_KEY, next));
  }
  function updateRecords(value: RecordDraft) {
    setRecordsDraft(value);
    setStorageOK(storeLocal(RECORD_DRAFT_KEY, value));
  }
  async function act(
    work: () => Promise<unknown>,
    text = "已保存",
  ): Promise<boolean> {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await work();
      await refresh();
      setNotice(text);
      return true;
    } catch (e) {
      setError(errorMessage(e));
      return false;
    } finally {
      setBusy(false);
    }
  }
  function record(a?: Action, original?: Occurrence) {
    if (!snapshot) return;
    let next = recordsDraft ?? {
      items: [emptyOccurrence(snapshot.household)],
      batchKey: crypto.randomUUID(),
    };
    if (a) {
      const existing = next.items.find((x) => x.action_id === a.id);
      if (!existing) {
        const item = forAction(snapshot.household, a);
        next = {
          ...next,
          items:
            next.items.length === 1 &&
            isEmptyDraft(snapshot.household, next.items[0])
              ? [item]
              : next.items.length < 50
                ? [...next.items, item]
                : next.items,
        };
      }
      setFocusAction(a.id);
    } else if (original) {
      const item = {
        ...original,
        client_key: crypto.randomUUID(),
        action_id:
          actions.find(
            (a) =>
              a.can_record &&
              a.goal_id === original.goal_id &&
              original.date.startsWith(a.month),
          )?.id ?? null,
      };
      next = {
        ...next,
        items:
          next.items.length === 1 &&
          isEmptyDraft(snapshot.household, next.items[0])
            ? [item]
            : [...next.items, item],
      };
      setFocusAction(item.action_id ?? undefined);
    } else {
      if (
        next.items.length === 1 &&
        isEmptyDraft(snapshot.household, next.items[0])
      )
        next = { ...next, items: [changeKind(next.items[0], "expense")] };
      setFocusAction(undefined);
    }
    updateRecords(next);
    setOverlay("record");
    setError("");
  }
  const adjust = (request: PlanRequest) => {
    setTrials({ selected: 0, requests: [request] });
    navigate("scenarios");
  };
  if (!snapshot || !draft)
    return (
      <div className="card empty" style={{ margin: 32 }}>
        <h1>FinUnity</h1>
        <p>{error || "正在读取本机财务资料…"}</p>
        <Button
          onClick={() => void refresh().catch((e) => setError(errorMessage(e)))}
        >
          重新连接
        </Button>
      </div>
    );
  const h = snapshot.household,
    R = snapshot.active_result ?? snapshot.result,
    active = plans.find((p) => p.status === "active"),
    pending = plans.find((p) => p.status === "pending" || p.status === "stale");
  const currentActions = actions
    .filter((a) => a.plan_id === active?.id && a.month === month)
    .toSorted(
      (a, b) =>
        (({ in_progress: 0, pending: 1, completed: 2 })[a.status] ?? 3) -
        ({ in_progress: 0, pending: 1, completed: 2 }[b.status] ?? 3),
    );
  const completed = currentActions.filter(
      (a) => a.status === "completed",
    ).length,
    remaining = currentActions
      .filter((a) => a.requires_event && a.remaining_amount != null)
      .reduce((n, a) => n + Number(a.remaining_amount), 0);
  const currentReview = reviews.find(
      (r) => r.result.plan_id === active?.id && r.result.month === month,
    ),
    historicalReviews = reviews.filter((r) => r.id !== currentReview?.id),
    conflict = diffCount(draft.base, h) > 0;
  const previousDate = new Date(`${month}-01T12:00:00Z`);
  previousDate.setUTCDate(0);
  const previousMonth = previousDate.toISOString().slice(0, 7);
  const previousPlans = plans.filter(
    (p) =>
      ["active", "superseded"].includes(p.status) &&
      actions.some((a) => a.plan_id === p.id && a.month === previousMonth),
  );
  const changedHousehold =
      !!active &&
      (active.household_revision !== snapshot.household_revision ||
        diffCount(active.household, h) > 0),
    newRecords = active
      ? events.filter(
          (e) => e.revision > active.revision && e.status === "confirmed",
        ).length
      : 0;
  const meta = active ? (
    <>
      <Chip tone={changedHousehold ? "warn" : "up"}>
        {changedHousehold
          ? "建档已变更 · 建议重新测算"
          : "方案使用中 · 与建档同步"}
      </Chip>
      <span>
        {changedHousehold
          ? "建档在方案确认之后改过。"
          : newRecords
            ? `已有 ${newRecords} 笔新记录，不影响方案条件。`
            : "账目与方案一致。"}
      </span>
    </>
  ) : (
    <Chip tone="dashed">还没有使用中的方案</Chip>
  );
  const complete = (id: string) => {
    void act(() => post(`/actions/${id}/complete`, {}), "安排检查已保存");
  };
  const nextDestination: Route =
    snapshot.revision === 0 || R.missing.length
      ? "household"
      : pending || !active
        ? "scenarios"
        : "execution";
  function nextStep() {
    if (snapshot!.revision === 0)
      return (
        <>
          <p>
            还没有建档。先花几分钟说清账户、收支和目标。
            <small>想先看看效果？示例是一户虚构家庭，保存前都只是草稿。</small>
          </p>
          <div className="acts">
            <Button onClick={() => navigate("household")}>开始建档</Button>
            <Button onClick={() => setOverlay("example")}>用示例体验</Button>
          </div>
        </>
      );
    if (R.missing.length)
      return (
        <>
          <p>
            建档还差一点：{R.missing.join("；")}
            <small>资料不完整时不会替你当作 0 来算。</small>
          </p>
          <Button onClick={() => navigate("household")}>去补全建档</Button>
        </>
      );
    if (pending)
      return (
        <>
          <p>
            {pending.status === "stale"
              ? "方案草案已过期，请基于最新账目重新测算。"
              : "有一份方案草案等你确认。"}
            <small>确认前先看它和当前方案的差别。</small>
          </p>
          <Button onClick={() => navigate("scenarios")}>
            {pending.status === "stale" ? "查看过期草案" : "查看并确认"}
          </Button>
        </>
      );
    if (!active)
      return (
        <>
          <p>
            建档已保存。下一步：试算一下，看目标够不够。
            <small>试算不会改账；满意了再生成方案草案、确认。</small>
          </p>
          <Button onClick={() => navigate("scenarios")}>去试算</Button>
        </>
      );
    if (!currentActions.length)
      return (
        <>
          <p>
            进入 {month}：方案仍在使用，这个月还没有投入计划。
            <small>沿用方案需要你确认一次，不会交易或移动资金。</small>
          </p>
          <Button onClick={() => navigate("execution")}>
            沿用方案，生成本月计划
          </Button>
        </>
      );
    const open = currentActions.find((a) => a.status !== "completed");
    if (open)
      return (
        <>
          <p>
            本月还差 <b>{fmt(remaining, R.base_currency)}</b> 没有投入。
            <small>
              投入后在这里记一笔，进度会自动累计；FinUnity
              不会替你认定“已经交易”。
            </small>
          </p>
          <Button
            onClick={() =>
              open.requires_event ? record(open) : navigate("execution")
            }
          >
            {open.requires_event ? "记录本次投入" : "去检查安排"}
          </Button>
        </>
      );
    return (
      <>
        <p>
          {dayNumber >= monthDays - 3
            ? "这个月快结束了：生成复盘，对照计划与实际。"
            : "本月的计划投入都已完成。"}
          <small>
            月底前（{monthDays - 3}–{monthDays} 日）生成复盘即可。
          </small>
        </p>
        <Button onClick={() => navigate("execution")}>去看复盘</Button>
      </>
    );
  }
  async function voidAndRecord(reenter: boolean) {
    if (!voidRecord) return;
    const original = voidRecord.event;
    if (
      await act(
        () =>
          post(`/occurrences/${voidRecord.id}/void`, {
            expected_revision: snapshot!.revision,
          }),
        "记录已作废，余额与投入进度已重新计算",
      )
    ) {
      close();
      if (reenter) record(undefined, original);
    }
  }
  return (
    <>
      <a
        className="btn skip"
        href="#main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main")?.focus();
        }}
      >
        跳到主要内容
      </a>
      <div className="shell play">
        <aside className="rail" aria-label="导航">
          <a className="brand" href="#/overview">
            <span className="brand-mark">F</span>
            <span>
              <b>FinUnity</b>
              <small>个人财务规划 · 本机</small>
            </span>
          </a>
          <nav aria-label="主要导航" className="main-nav">
            {routes.map((r, i) => (
              <a
                key={r}
                href={`#/${r}`}
                aria-current={tab === r ? "page" : undefined}
              >
                <Icon name={icons[i]} />
                <span>
                  <b>{routeNames[r]}</b>
                  <small>
                    {r === "household"
                      ? snapshot.revision
                        ? changes
                          ? "有未保存修改"
                          : "资料已保存"
                        : "未开始"
                      : r === "execution"
                        ? active
                          ? `${completed}/${currentActions.length} 项已完成`
                          : "等待确认方案"
                        : r === "scenarios"
                          ? pending
                            ? pending.status === "stale"
                              ? "草案已过期"
                              : "待确认草案"
                            : active
                              ? "方案使用中"
                              : "试算与比较"
                          : "现金与目标"}
                  </small>
                </span>
              </a>
            ))}
          </nav>
          <div className="rail-foot">
            <p>
              数据只保存在这台电脑上。
              <br />
              地址 127.0.0.1，不连接银行，不自动交易。
            </p>
            <small className="hint">快捷键：1–4 切换页面 · N 记一笔</small>
          </div>
        </aside>
        <main className="main" id="main" tabIndex={-1}>
          <div className="wrap">
            <header className="topbar">
              <div className="who">
                <b>{h.name}</b>
                <span className="extra">
                  {" "}
                  · {h.base_currency} · 上海时间 · {day()}
                </span>
              </div>
              <span className="sp" />
              <button
                className="iconbtn"
                type="button"
                onClick={() => setOverlay("assistant")}
              >
                <Icon name="spark" size={18} />
                助手
                {configured && (
                  <span
                    className={`dotmark configured ${running ? "running" : ""}`}
                    title={running ? "生成中" : "已配置"}
                  />
                )}
              </button>
              <label className="theme-picker">
                <span className="sr-only">外观主题</span>
                <select
                  aria-label="外观主题"
                  value={theme}
                  onChange={(e) => {
                    const value = e.target.value as typeof theme;
                    chooseTheme(value);
                    setTheme(value);
                  }}
                >
                  <option value="system">系统</option>
                  <option value="light">浅色</option>
                  <option value="dark">深色</option>
                </select>
              </label>
              {snapshot.revision > 0 && (
                <Button
                  className="record-entry"
                  primary={
                    tab === "overview" ||
                    (tab === "execution" && currentActions.length > 0)
                  }
                  disabled={busy}
                  onClick={() => record()}
                >
                  ＋ 记一笔
                </Button>
              )}
            </header>
            {nextDestination !== tab && (
              <section
                className={`next ${pending?.status === "stale" ? "warn" : currentActions.length > 0 && completed === currentActions.length ? "ok" : ""}`}
                aria-label="下一步"
              >
                <span className="k">下一步</span>
                {nextStep()}
              </section>
            )}
            {changes > 0 && tab !== "household" && (
              <div className="review-banner">
                <span>
                  建档有 {changes} 处未保存修改。当前测算与记账使用已保存资料。
                </span>
                <button
                  className="linkbtn"
                  onClick={() => navigate("household")}
                >
                  回去保存
                </button>
              </div>
            )}
            {!storageOK && (
              <p className="summary-err">
                浏览器无法保存草稿到本机存储；当前页面内仍保留草稿，刷新前请先保存或导出。
              </p>
            )}
            {error &&
              overlay == null &&
              tab !== "household" &&
              tab !== "scenarios" && (
                <p className="summary-err" role="alert">
                  {error}
                </p>
              )}
            {tab === "overview" && (
              <>
                <PageHead
                  eyebrow={
                    snapshot.revision
                      ? `${month} · 资料已保存`
                      : "未开始 · 约 5 分钟完成建档"
                  }
                  title={"概览"}
                  lede={
                    snapshot.revision
                      ? "让每一笔钱，都有下一步。"
                      : "先说清现状，再谈目标。每个月只做四件事。"
                  }
                  meta={snapshot.revision ? meta : undefined}
                />
                {snapshot.revision ? (
                  <Overview
                    s={snapshot}
                    R={R}
                    actions={currentActions}
                    events={events}
                    record={record}
                    busy={busy}
                    complete={complete}
                    adjust={adjust}
                    reconcile={(id) => {
                      setRecon({
                        assetId: id,
                        value: R.assets.find((a) => a.id === id)?.balance ?? "",
                        source: "银行余额核对",
                        key: crypto.randomUUID(),
                      });
                      setOverlay("reconcile");
                      setError("");
                    }}
                  />
                ) : (
                  <section className="card empty">
                    <h2>从建档开始</h2>
                    <ol>
                      {[
                        "建档：账户、收支、目标（约 5 分钟）",
                        "试算：看目标够不够，缺口怎么补",
                        "执行：每月记下真实投入",
                        "复盘：月底对照计划，再调整",
                      ].map((x, i) => (
                        <li key={x}>
                          <b>{i + 1}</b>
                          <span>{x}</span>
                        </li>
                      ))}
                    </ol>
                    <div className="row">
                      <Button onClick={() => navigate("household")}>
                        开始建档
                      </Button>
                      <Button onClick={() => setOverlay("example")}>
                        用示例体验
                      </Button>
                    </div>
                    <p className="hint">
                      所有数据只保存在这台电脑上；示例是虚构家庭，保存前可检查修改。
                    </p>
                  </section>
                )}
              </>
            )}
            {tab === "household" && (
              <HouseholdEditor
                h={draft.value}
                set={updateHousehold}
                changes={changes}
                conflict={conflict}
                busy={busy}
                revision={snapshot.household_revision}
                error={error}
                example={() => setOverlay("example")}
                discard={() => setOverlay("discard")}
                save={() => {
                  if (!changes || conflict) return;
                  void act(async () => {
                    const response = await post<Snapshot>("/household", {
                      expected_revision: snapshot.revision,
                      household: draft.value,
                    });
                    const clean = {
                      base: response.household,
                      value: response.household,
                    };
                    setDraft(clean);
                    storeLocal(HOUSEHOLD_DRAFT_KEY, null);
                  }, "建档已保存，可以进行测算");
                }}
              />
            )}
            <div hidden={tab !== "scenarios"}>
              <Scenarios
                h={h}
                baseline={R}
                plans={plans}
                revision={snapshot.revision}
                trials={trials}
                setTrials={setTrials}
                visible={tab === "scenarios"}
                busy={busy}
                act={act}
                error={tab === "scenarios" ? error : ""}
                meta={meta}
              />
            </div>
            {tab === "execution" && (
              <>
                <PageHead
                  eyebrow={`${month} · ${completed}/${currentActions.length} 项已完成`}
                  title="本月"
                  lede="记录实际发生的事，再与已确认方案比较。"
                />
                <section className="card ribbon" aria-label="本月进度">
                  <div className="ribbon-top">
                    <b>{month}</b>
                    <span className="muted">
                      第 {dayNumber} 天 / 共 {monthDays} 天 · 距月末还有{" "}
                      {monthDays - dayNumber} 天
                    </span>
                    <span className="sp" />
                    <Chip tone="warn">
                      复盘建议期 {monthDays - 3}–{monthDays} 日
                    </Chip>
                  </div>
                  <div className="ribbon-track">
                    <span
                      className="zone"
                      style={{
                        left: `${((monthDays - 4) / monthDays) * 100}%`,
                        width: `${(4 / monthDays) * 100}%`,
                      }}
                    />
                    <span
                      className="past"
                      style={{ width: `${(dayNumber / monthDays) * 100}%` }}
                    />
                    <span
                      className="now"
                      style={{ left: `${(dayNumber / monthDays) * 100}%` }}
                    />
                  </div>
                  <div className="ribbon-scale">
                    <span>1 日</span>
                    <span>边做边记 · 月底复盘</span>
                    <span>{monthDays} 日</span>
                  </div>
                </section>
                <section className="sec">
                  <SectionHead no="01" title="本月投入计划" />
                  {active && !currentActions.length && (
                    <section className="card renew sec">
                      <h3>{month} 还没有投入计划</h3>
                      <p>
                        方案仍在使用。请确认沿用本月投入金额；不会自动交易或移动资金。
                      </p>
                      {active.result.goals.map((g) => (
                        <LedgerLine
                          key={g.id}
                          title={g.name}
                          value={fmt(g.affordable_monthly, R.base_currency)}
                        />
                      ))}
                      <details>
                        <summary>核对沿用条件与缺口</summary>
                        <p>
                          应急金缺口{" "}
                          {fmt(
                            active.result.reserve_shortfall,
                            R.base_currency,
                          )}
                        </p>
                        {active.result.goals.map((g) => (
                          <p key={g.id}>
                            {g.name} · 到期 {g.due} · 缺口{" "}
                            {fmt(g.shortfall, R.base_currency)}
                          </p>
                        ))}
                      </details>
                      {changedHousehold && (
                        <p className="errmsg">
                          建档已变更，请先重新测算并确认方案。
                        </p>
                      )}
                      <div className="row">
                        <Button
                          primary
                          disabled={busy || changedHousehold}
                          onClick={() =>
                            void act(
                              () =>
                                post(`/plans/${active.id}/renew`, {
                                  expected_revision: snapshot.revision,
                                  accept_shortfalls: true,
                                }),
                              "本月计划已生成",
                            )
                          }
                        >
                          接受现有方案与缺口，生成本月计划
                        </Button>
                        <Button onClick={() => navigate("scenarios")}>
                          先去试算调整
                        </Button>
                      </div>
                    </section>
                  )}
                  {currentActions.map((a) => (
                    <ActionCard
                      key={a.id}
                      action={a}
                      h={h}
                      events={events}
                      record={record}
                      busy={busy}
                      complete={complete}
                    />
                  ))}
                  {!active && (
                    <div className="card pad">
                      确认方案后显示本月计划。
                      <a className="btn add-button" href="#/scenarios">
                        去试算并确认
                      </a>
                    </div>
                  )}
                </section>
                <section className="sec">
                  <SectionHead
                    no="02"
                    title="流水与预测"
                    aside={
                      <div className="filters">
                        {[
                          ["all", "全部"],
                          ["confirmed", "已发生"],
                          ["forecast", "预计 / 待核对"],
                          ["voided", "已作废"],
                        ].map(([key, title]) => (
                          <button
                            type="button"
                            key={key}
                            aria-pressed={filter === key}
                            onClick={() => setFilter(key)}
                          >
                            {title}
                          </button>
                        ))}
                      </div>
                    }
                  />
                  <label className="ledger-month">
                    月份{" "}
                    <select
                      className="in"
                      aria-label="流水月份"
                      value={ledgerMonth}
                      onChange={(e) => setLedgerMonth(e.target.value)}
                    >
                      {[
                        ...new Set([
                          month,
                          ledgerMonth,
                          ...events.map((e) => e.event.date.slice(0, 7)),
                        ]),
                      ]
                        .sort()
                        .reverse()
                        .map((m) => (
                          <option key={m}>{m}</option>
                        ))}
                    </select>
                  </label>
                  <div className="card ledger-wrap">
                    <table className="ledger">
                      <thead>
                        <tr>
                          <th>日期 / 状态</th>
                          <th>记录内容</th>
                          <th>账户变化</th>
                          <th>操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {events
                          .filter(
                            (e) =>
                              e.event.date.startsWith(ledgerMonth) &&
                              (filter === "all" ||
                                (filter === "forecast"
                                  ? ["forecast", "pending"].includes(e.status)
                                  : e.status === filter)),
                          )
                          .map((e) => (
                            <tr
                              key={e.id}
                              className={e.status === "voided" ? "void" : ""}
                            >
                              <td>
                                {e.event.date}
                                <div>
                                  <Chip
                                    tone={
                                      e.status === "confirmed"
                                        ? "up"
                                        : e.status === "voided"
                                          ? "down"
                                          : "plain"
                                    }
                                  >
                                    {e.status === "pending"
                                      ? "待核对"
                                      : label(e.status)}
                                  </Chip>
                                </div>
                              </td>
                              <td>
                                <b>{kinds[e.event.kind]}</b>
                                <p className="hint">
                                  {e.event.source} · {e.event.note}
                                </p>
                              </td>
                              <td>
                                <RecordDetails household={h} record={e} />
                              </td>
                              <td>
                                {["confirmed", "forecast", "pending"].includes(
                                  e.status,
                                ) && (
                                  <details className="row-menu">
                                    <summary>操作</summary>
                                    <Button
                                      className="ghost sm"
                                      disabled={busy}
                                      onClick={() => {
                                        setVoidRecord(e);
                                        setOverlay("void");
                                        setError("");
                                      }}
                                    >
                                      作废
                                    </Button>
                                  </details>
                                )}
                              </td>
                            </tr>
                          ))}
                        {!events.some(
                          (e) =>
                            e.event.date.startsWith(ledgerMonth) &&
                            (filter === "all" ||
                              (filter === "forecast"
                                ? ["forecast", "pending"].includes(e.status)
                                : e.status === filter)),
                        ) && (
                          <tr>
                            <td colSpan={4}>这个月份和筛选条件下没有记录。</td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </section>
                <section className="sec">
                  <SectionHead
                    no="03"
                    title={`本月复盘 · ${month}`}
                    aside={
                      <Button
                        disabled={busy || !active}
                        onClick={() =>
                          active &&
                          void act(
                            () =>
                              post(`/plans/${active.id}/reviews`, { month }),
                            "本月复盘已更新",
                          )
                        }
                      >
                        {currentReview ? "更新本月复盘" : "生成本月复盘"}
                      </Button>
                    }
                  />
                  {dayNumber <= 10 && previousPlans.length > 0 && (
                    <section className="card renew sec">
                      <h3>补做 {previousMonth} 收官复盘</h3>
                      <p>本月 1–10 日可补做上月复盘；不会改变当月投入计划。</p>
                      {previousPlans.map((p) => (
                        <Button
                          key={p.id}
                          disabled={busy}
                          onClick={() =>
                            void act(
                              () =>
                                post(`/plans/${p.id}/reviews`, {
                                  month: previousMonth,
                                }),
                              "上月收官复盘已更新",
                            )
                          }
                        >
                          补做上月复盘 · 修订 {p.revision}
                        </Button>
                      ))}
                    </section>
                  )}
                  {currentReview ? (
                    <>
                      <ReviewCard review={currentReview} events={events} />
                      {currentReview.revision !== snapshot.revision && (
                        <p className="errmsg">记录已变化，请更新复盘。</p>
                      )}
                      <Button
                        onClick={() =>
                          adjust(
                            active?.request ?? { scenario: {}, monthly: {} },
                          )
                        }
                      >
                        调整方案 →
                      </Button>
                    </>
                  ) : (
                    <div className="card pad">
                      <p className="muted">
                        {active
                          ? "生成复盘后显示已记录的收支与投入；未记录不会当作零。"
                          : "确认方案后可复盘。"}
                      </p>
                    </div>
                  )}
                  <ReviewTrend
                    reviews={reviews}
                    events={events}
                    h={h}
                    month={month}
                  />
                  {historicalReviews.length > 0 && (
                    <details className="sec">
                      <summary>历史复盘（{historicalReviews.length}）</summary>
                      {historicalReviews.map((r) => (
                        <ReviewCard key={r.id} review={r} events={events} />
                      ))}
                    </details>
                  )}
                </section>
              </>
            )}
          </div>
        </main>
      </div>
      <nav className="tabbar" aria-label="底部导航">
        {routes.map((r, i) => (
          <a
            key={r}
            href={`#/${r}`}
            aria-current={tab === r ? "page" : undefined}
          >
            <Icon name={icons[i]} />
            {routeNames[r]}
          </a>
        ))}
      </nav>
      {snapshot.revision > 0 && (
        <button
          className="fab record-entry-mobile"
          type="button"
          onClick={() => record()}
        >
          <Icon name="plus" />
          记一笔
        </button>
      )}
      {notice && (
        <div className="toasts">
          <div className="toast" role="status">
            <p>{notice}</p>
            <button
              type="button"
              aria-label="关闭提示"
              onClick={() => setNotice("")}
            >
              ×
            </button>
          </div>
        </div>
      )}
      {overlay === "record" && recordsDraft && (
        <RecordSheet
          h={h}
          result={R}
          actions={currentActions}
          records={events}
          draft={recordsDraft}
          setDraft={updateRecords}
          storageOK={storageOK}
          focusAction={focusAction}
          close={close}
          submit={async (items, key) => {
            setBusy(true);
            try {
              await post("/occurrences/batch", {
                items,
                batch_key: key,
                expected_revision: snapshot.revision,
              });
              await refresh();
              setNotice(`已保存 ${items.length} 条记录，进度已更新`);
            } finally {
              setBusy(false);
            }
          }}
        />
      )}
      {overlay === "example" && (
        <Modal
          title="填入虚构示例？"
          close={close}
          footer={
            <>
              <Button onClick={close}>取消</Button>
              <Button
                primary
                onClick={() => {
                  updateHousehold(example());
                  navigate("household");
                  close();
                }}
              >
                填入草稿
              </Button>
            </>
          }
        >
          <p>
            {changes
              ? "这会替换未保存的建档草稿。"
              : "示例只填入草稿，检查并保存后才成为建档。"}
          </p>
          <p className="hint">
            现有流水不会被删除。已有记录时请保留对应账户与目标 ID。
          </p>
        </Modal>
      )}
      {overlay === "discard" && (
        <Modal
          title="放弃未保存的建档修改？"
          close={close}
          footer={
            <>
              <Button onClick={close}>继续编辑</Button>
              <Button
                onClick={() => {
                  setDraft({ base: h, value: h });
                  storeLocal(HOUSEHOLD_DRAFT_KEY, null);
                  close();
                }}
              >
                放弃修改
              </Button>
            </>
          }
        >
          <p>这会放弃 {changes} 处草稿修改，恢复到最近保存的建档。</p>
        </Modal>
      )}
      {overlay === "void" && voidRecord && (
        <Modal
          title="作废这条记录？"
          close={close}
          busy={busy}
          footer={
            <>
              <Button disabled={busy} onClick={close}>
                取消
              </Button>
              {voidRecord.event.kind !== "adjustment" && (
                <Button
                  disabled={busy || (recordsDraft?.items.length ?? 0) >= 50}
                  onClick={() => void voidAndRecord(true)}
                >
                  作废并重新录入
                </Button>
              )}
              <Button
                className="danger"
                disabled={busy}
                onClick={() => void voidAndRecord(false)}
              >
                确认作废
              </Button>
            </>
          }
        >
          <p>
            {voidRecord.event.date} · {kinds[voidRecord.event.kind]}
          </p>
          <div className="impact">
            {effects(h, voidRecord.event).map((x) => (
              <p key={x}>{x}</p>
            ))}
            <p>
              作废后撤销以上余额变化，并重新计算关联目标进度；原记录与审计保留。
            </p>
          </div>
          {error && <p className="summary-err">{error}</p>}
        </Modal>
      )}
      {overlay === "reconcile" && (
        <Modal
          title="核对账户余额"
          close={close}
          busy={busy}
          footer={
            <>
              <Button disabled={busy} onClick={close}>
                取消
              </Button>
              <Button
                primary
                disabled={busy || atoms(recon.value) == null}
                onClick={() =>
                  void act(
                    () =>
                      post("/reconciliations", {
                        expected_revision: snapshot.revision,
                        client_key: recon.key,
                        asset_id: recon.assetId,
                        balance: recon.value,
                        source: recon.source,
                      }),
                    "对账调整已保存，基准日保持不变",
                  ).then((ok) => {
                    if (ok) close();
                  })
                }
              >
                确认差额调整
              </Button>
            </>
          }
        >
          <p>
            {h.assets.find((a) => a.id === recon.assetId)?.name} · 当前已记余额{" "}
            {fmt(
              R.assets.find((a) => a.id === recon.assetId)?.balance,
              h.assets.find((a) => a.id === recon.assetId)?.currency,
            )}
          </p>
          <Field title="银行 / 机构实际余额（原币）">
            <input
              className="in"
              inputMode="decimal"
              value={recon.value}
              onChange={(e) => setRecon({ ...recon, value: e.target.value })}
            />
          </Field>
          <Field title="核对来源">
            <input
              className="in"
              value={recon.source}
              onChange={(e) => setRecon({ ...recon, source: e.target.value })}
            />
          </Field>
          <div className="impact">
            将生成差额{" "}
            {fmt(
              Number(recon.value) -
                Number(R.assets.find((a) => a.id === recon.assetId)?.balance),
              h.assets.find((a) => a.id === recon.assetId)?.currency,
            )}{" "}
            的调整记录。可作废，不计入收入、支出或目标投入。
          </div>
          <p className="hint">
            对账以今天为日期；原日终基准不变，不会补写遗漏流水。
          </p>
          {error && <p className="summary-err">{error}</p>}
        </Modal>
      )}
      {overlay === "assistant" && (
        <Modal
          sheet
          title="规划助手"
          close={close}
          footer={
            <p className="hint">
              当前建档与问题会发送给所配置的 AI 服务。确认方案前不会执行交易。
            </p>
          }
        >
          <p className="muted">理解目标 · 调用测算 · 提议调整</p>
          {!configured && (
            <div className="assist-state">
              <b>AI 未配置，可先手动测算与复盘。</b>
              <ol>
                <li>在原型后端本机配置 OPENROUTER_API_KEY。</li>
                <li>设置支持工具调用的 OPENROUTER_MODEL 并重启本机服务。</li>
                <li>供应商费用以实际账单为准，缺失费用显示“待核对”。</li>
              </ol>
            </div>
          )}
          {running && (
            <div className="summary-err">
              正在生成。可以查看其它页面；请先别改建档、记账、作废或对账，否则本次结果会过期，已发生的调用仍可能收费。
            </div>
          )}
          <p className="hint">
            今日剩余{" "}
            {Math.max(
              0,
              10 -
                tasks.filter(
                  (t) =>
                    t.created_at.slice(0, 10) ===
                    new Date().toISOString().slice(0, 10),
                ).length,
            )}{" "}
            / 10 个任务，北京时间 08:00 重置。每任务最多 4 次请求、每次最多 2048
            输出 token。
          </p>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              void act(async () => {
                await post("/assistant/tasks", {
                  expected_revision: snapshot.revision,
                  message,
                });
                setMessage("");
              }, "AI 任务已创建；生成期间请先别改账");
            }}
          >
            <textarea
              className="in"
              aria-label="向规划助手提问"
              rows={4}
              value={message}
              disabled={!configured}
              maxLength={4000}
              placeholder="说说你的目标或疑问…"
              onChange={(e) => setMessage(e.target.value)}
            />
            <p className="hint">
              发送会向 OpenRouter
              和所选模型服务提供当前建档与测算上下文。费用以供应商为准。
            </p>
            <Button
              primary
              type="submit"
              disabled={!configured || running || busy || !message.trim()}
            >
              {running ? "正在生成…" : "发送给规划助手"}
            </Button>
          </form>
          {error && <p className="summary-err">{error}</p>}
          {tasks.map((t) => (
            <article className="card pad stack" key={t.id}>
              <b>{t.message}</b>
              <div className="row">
                <Chip>{label(t.status)}</Chip>
                <span className="hint">
                  {t.cost_status === "unknown"
                    ? "费用待核对"
                    : t.cost_status === "known"
                      ? "用量已记录"
                      : "尚未调用"}
                </span>
              </div>
              {t.answer && (
                <>
                  <ReactMarkdown>{t.answer.message}</ReactMarkdown>
                  {t.answer.questions.map((x) => (
                    <p key={x}>待补充：{x}</p>
                  ))}
                </>
              )}
              {t.error && <p className="errmsg">{t.error}</p>}
              {t.proposal_id && (
                <Button
                  onClick={() => {
                    navigate("scenarios");
                    close();
                  }}
                >
                  查看方案草案 →
                </Button>
              )}
            </article>
          ))}
        </Modal>
      )}
    </>
  );
}
