import { useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  QueryClient,
  QueryClientProvider,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  BrowserRouter,
  Link,
  Navigate,
  NavLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  AlertCircle,
  ArrowDownRight,
  ArrowUpRight,
  Bell,
  BriefcaseBusiness,
  Building2,
  Check,
  ChevronRight,
  CircleDollarSign,
  CloudOff,
  Eye,
  EyeOff,
  FileImage,
  Globe2,
  LayoutDashboard,
  LoaderCircle,
  LogOut,
  Menu,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Target,
  Trash2,
  UploadCloud,
  WalletCards,
  X,
} from "lucide-react";
import { api, ApiError, createClientRequestId, session, type Api } from "./api";
import { ApiContext, useApi } from "./apiContext";
import { DemoContext, useDemo, type DemoControls } from "./demoContext";
import { GuestImportPanel } from "./GuestImportPanel";
import { guestRows, settingsTransfer, type GuestRow } from "./onboarding";
import type { DemoState } from "./demo/seed";
import { PORTFOLIO_URL, cameFromPortfolio } from "./portfolioOrigin";
import { languageFromSearch } from "./urlLanguage";
import { DEMO_SAMPLE_FILE_NAME, demoSampleImage } from "./demo/sample";
import { PublicHome, RegisterPage, UsernameMigration, AccountSecurity } from "./AuthPages";
import { policyHref, translate, type MessageKey } from "./i18n";
import {
  ThemeContext,
  useTheme,
  type Appearance,
  type ThemeApi,
  type ThemeColor,
} from "./theme";
import type {
  Account,
  Holding,
  HoldingImportAction,
  HoldingImport,
  LedgerSummary,
  Locale,
  ParsedHolding,
  Source,
  Transaction,
  User,
} from "./types";
import "./App.css";
import { englishImportTemplate, importTemplate, parseDelimited, parseHoldingRows, readHoldingFile } from "./holdingImport";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000, refetchOnWindowFocus: true },
  },
});
/* Risk-bucket colours are fixed semantic tokens (cobalt / mist blue / amber), never
   themed — the same reason gains and losses don't change with the theme.
   They are referenced as CSS variables so dark mode picks up its own tier. */
const buckets = [
  { key: "AGGRESSIVE", color: "var(--bucket-aggr)" },
  { key: "BALANCED", color: "var(--bucket-bal)" },
  { key: "DEFENSIVE", color: "var(--bucket-def)" },
] as const;

/* Flow categories have no fixed meaning, so they cycle through brand + bucket
   hues. Kept as variables so every theme renders them coherently. */
const categoryPalette = [
  "var(--brand-600)",
  "var(--bucket-bal)",
  "var(--brand-500)",
  "var(--bucket-def)",
];
type Goals = {
  aggressive_pct: number;
  balanced_pct: number;
  defensive_pct: number;
  version: number;
};
type Notice = {
  kind: "success" | "error" | "info";
  key?: MessageKey;
  text?: string;
  suffix?: string;
} | null;
type ModalName =
  | "account"
  | "holding"
  | "flow"
  | "import"
  | "goals"
  | "review"
  | "alerts"
  | "privacy"
  | "profile"
  | null;

function useLocale() {
  const [locale, setLocaleState] = useState<Locale>(
    () => {
      const fromLink = languageFromSearch(window.location.search) || (window.location.pathname === "/en/" ? "en-US" : null);
      if (fromLink) {
        localStorage.setItem("finunity-locale", fromLink);
        return fromLink;
      }
      const stored = localStorage.getItem("finunity-locale");
      return stored === "en-US" || stored === "zh-CN" ? stored : "zh-CN";
    },
  );
  const setLocale = useCallback((value: Locale) => {
    localStorage.setItem("finunity-locale", value);
    setLocaleState(value);
  }, []);
  useEffect(() => {
    document.documentElement.lang = locale;
    const chinese = locale === "zh-CN";
    const metadata = chinese
      ? {
          title: "衡仓 · 把分散资产整理成一份清楚的账本",
          socialTitle: "衡仓",
          description: "把分散资产整理成一份清楚的账本",
          socialDescription: "把分散资产整理成一份清楚的账本",
        }
      : {
          title: "FinUnity · One clear ledger for scattered assets",
          socialTitle: "FinUnity",
          description: "One clear ledger for scattered assets",
          socialDescription: "One clear ledger for scattered assets",
        };
    document.title = metadata.title;
    document
      .querySelector('meta[name="description"]')
      ?.setAttribute("content", metadata.description);
    document
      .querySelector('meta[property="og:title"]')
      ?.setAttribute("content", metadata.socialTitle);
    document
      .querySelector('meta[property="og:description"]')
      ?.setAttribute("content", metadata.socialDescription);
    document
      .querySelector('meta[name="twitter:title"]')
      ?.setAttribute("content", metadata.socialTitle);
    document
      .querySelector('meta[name="twitter:description"]')
      ?.setAttribute("content", metadata.socialDescription);
  }, [locale]);
  return { locale, setLocale, t: (key: MessageKey) => translate(locale, key) };
}

function ThemeProvider({ children }: { children: ReactNode }) {
  const theme = useTheme();
  return (
    <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>
  );
}

function useThemeSettings(): ThemeApi {
  const theme = useContext(ThemeContext);
  if (!theme) throw new Error("useThemeSettings requires <ThemeProvider>");
  return theme;
}

function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
  label: string;
}) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={option.value === value ? "active" : undefined}
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

function ThemeSettings({ t }: { t: (k: MessageKey) => string }) {
  const { themeColor, appearance, setThemeColor, setAppearance } =
    useThemeSettings();
  const colors: { value: ThemeColor; label: string }[] = [
    { value: "minimal", label: t("themeMinimal") },
    { value: "navy", label: t("themeNavy") },
  ];
  const appearances: { value: Appearance; label: string }[] = [
    { value: "system", label: t("appearanceSystem") },
    { value: "light", label: t("appearanceLight") },
    { value: "dark", label: t("appearanceDark") },
  ];
  return (
    <div className="theme-settings">
      <div className="theme-field">
        <span>{t("themeColor")}</span>
        <Segmented
          label={t("themeColor")}
          value={themeColor}
          options={colors}
          onChange={setThemeColor}
        />
      </div>
      <div className="theme-field">
        <span>{t("appearance")}</span>
        <Segmented
          label={t("appearance")}
          value={appearance}
          options={appearances}
          onChange={setAppearance}
        />
      </div>
    </div>
  );
}

function LanguageToggle({
  locale,
  setLocale,
  t,
  compact = false,
}: {
  locale: Locale;
  setLocale: (l: Locale) => void;
  t: (key: MessageKey) => string;
  compact?: boolean;
}) {
  return (
    <button
      className="language-toggle"
      type="button"
      onClick={() => setLocale(locale === "zh-CN" ? "en-US" : "zh-CN")}
      aria-label={t(locale === "zh-CN" ? "switchToEnglish" : "switchToChinese")}
    >
      <Globe2 />
      <span>
        {compact
          ? locale === "zh-CN"
            ? "EN"
            : "中"
          : locale === "zh-CN"
            ? "English"
            : "中文"}
      </span>
    </button>
  );
}

function ProtectedApp({
  user,
  onLogout,
  onUserUpdate,
  locale,
  setLocale,
  t,
}: ReturnType<typeof useLocale> & {
  user: User;
  onLogout: () => void;
  onUserUpdate: (user: User) => void;
}) {
  const [visible, setVisible] = useState(true);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [modal, setModal] = useState<ModalName>(null);
  const [selected, setSelected] = useState<
    Account | Holding | Transaction | null
  >(null);
  const [notice, setNotice] = useState<Notice>(null);
  const api = useApi();
  const demo = useDemo();
  const queryClient = useQueryClient();
  const ledger = useQuery({
    queryKey: ["ledger", localMonth()],
    queryFn: () => api.ledgerSummary(localMonth()),
    refetchInterval: 60_000,
  });
  const accounts = { data: ledger.data?.records.accounts };
  const holdings = { data: ledger.data?.records.holdings };
  const flows = { data: ledger.data?.records.transactions };
  const sources = { data: ledger.data?.records.sources || [] };
  const goals = { data: ledger.data?.goals };
  const open = (name: ModalName, item?: Account | Holding | Transaction) => {
    setSelected(item || null);
    setModal(name);
  };
  useEffect(() => {
    const online = () => setNotice(null);
    const offline = () => setNotice({ kind: "info", key: "offline" });
    addEventListener("online", online);
    addEventListener("offline", offline);
    if (!navigator.onLine) offline();
    return () => {
      removeEventListener("online", online);
      removeEventListener("offline", offline);
    };
  }, []);
  useEffect(() => {
    if (!notice || notice.kind !== "success") return;
    const timer = window.setTimeout(() => setNotice(null), 4000);
    return () => window.clearTimeout(timer);
  }, [notice]);
  const refreshing = useRef(false);
  const refresh = async () => {
    if (refreshing.current) return;
    refreshing.current = true;
    setNotice({ kind: "info", key: "loading" });
    try {
      const prices = await api.refreshHoldings();
      await queryClient.invalidateQueries({ queryKey: ["ledger"] }, { throwOnError: true });
      setNotice(prices.status === "success"
        ? { kind: "success", key: "marketFresh" }
        : { kind: "info", key: prices.status === "unconfigured" ? "pricesUnconfigured" : "pricesIncomplete" });
    } catch (error) {
      setNotice({ kind: "error", text: errorText(error, t) });
    } finally { refreshing.current = false; }
  };
  const failed = ledger.error;
  const alertCount = financialAlerts(
    accounts.data || [],
    holdings.data || [],
    goals.data,
    ledger.data,
    t,
  ).length;
  return (
    <div className="app-shell">
      <aside className={`side-nav ${mobileMenu ? "open" : ""}`}>
        <div className="side-head">
          <NavLink className="app-brand" to="/app">
            <span className="brand-mark">F</span>
            <span>{locale === "zh-CN" ? "衡仓" : "FinUnity"}</span>
          </NavLink>
          <button
            className="mobile-close"
            onClick={() => setMobileMenu(false)}
            aria-label={t("close")}
          >
            <X />
          </button>
        </div>
        <nav>
          <AppNav t={t} onNavigate={() => setMobileMenu(false)} />
        </nav>
        <div className="side-foot">
          <a href={locale === "zh-CN" ? "/about/" : "/en/about/"}>{locale === "zh-CN" ? "关于与隐私" : "About and privacy"}</a>
          {!demo && <Link to="/demo">{locale === "zh-CN" ? "回看示例账本" : "Explore the demo"}</Link>}
          {demo && <Link to="/login" state={{ background: { pathname: "/" } }}>{locale === "zh-CN" ? "登录" : "Sign in"}</Link>}
          <button onClick={() => open("privacy")}>
            <ShieldCheck />
            <span>
              <strong>{t("privacy")}</strong>
              <small>{t(demo ? "demoTrust" : "privacy")}</small>
            </span>
          </button>
          <button onClick={() => open("profile")}>
            <span className="avatar">
              {(user.display_name || user.username || user.email || "U")[0].toUpperCase()}
            </span>
            <span>
              <strong>{user.display_name || user.username || user.email?.split("@")[0] || "User"}</strong>
              <small>{user.username || user.email}</small>
            </span>
            <ChevronRight />
          </button>
        </div>
      </aside>
      {mobileMenu && (
        <button
          className="nav-scrim"
          aria-label={t("close")}
          onClick={() => setMobileMenu(false)}
        />
      )}
      <main className="main-content">
        <DemoBanner locale={locale} t={t} />
        <header className="app-topbar">
          <button
            className="menu-button"
            aria-label={t("menu")}
            onClick={() => setMobileMenu(true)}
          >
            <Menu />
          </button>
          <div className="app-title">
            <h1>{locale === "zh-CN" ? "衡仓" : "FinUnity"}</h1>
          </div>
          <div className="top-actions">
            <LanguageToggle locale={locale} setLocale={setLocale} t={t} compact />
            <button
              className="icon-button"
              aria-label={t(visible ? "hideAmounts" : "showAmounts")}
              aria-pressed={!visible}
              onClick={() => setVisible((x) => !x)}
            >
              {visible ? <Eye /> : <EyeOff />}
            </button>
            <button
              className="icon-button"
              aria-label={t("alerts")}
              onClick={() => open("alerts")}
            >
              <Bell />
              {alertCount > 0 && (
                <span className="alert-badge">{alertCount}</span>
              )}
            </button>
          </div>
        </header>
        {notice && (
          <InlineNotice notice={notice} t={t} onClose={() => setNotice(null)} />
        )}
        {ledger.isLoading ? (
          <DataState
            icon={<LoaderCircle className="spin" />}
            title={t("loading")}
          />
        ) : failed ? (
          <DataState
            icon={<AlertCircle />}
            title={errorText(failed, t)}
            action={
              <button className="primary-button" onClick={refresh}>
                {t("retry")}
              </button>
            }
          />
        ) : (
          <Routes>
            <Route
              index
              element={
                <OverviewPage
                  t={t}
                  locale={locale}
                  holdings={holdings.data || []}
                  flows={flows.data || []}
                  sources={sources.data || []}
                  goals={goals.data}
                  ledger={ledger.data}
                  visible={visible}
                  onOpen={open}
                  onRefresh={refresh}
                />
              }
            />
            <Route
              path="app/holdings"
              element={
                <HoldingsPage
                  t={t}
                  locale={locale}
                  holdings={holdings.data || []}
                  sources={sources.data || []}
                  visible={visible}
                  onOpen={open}
                />
              }
            />
            <Route
              path="app/flows"
              element={
                <FlowsPage
                  t={t}
                  locale={locale}
                  flows={flows.data || []}
                  sources={sources.data || []}
                  visible={visible}
                  onOpen={open}
                />
              }
            />
            <Route path="*" element={<OverviewPage t={t} locale={locale} holdings={holdings.data || []} flows={flows.data || []} sources={sources.data || []} goals={goals.data} ledger={ledger.data} visible={visible} onOpen={open} onRefresh={refresh} />} />
          </Routes>
        )}
      </main>
      <nav className="bottom-nav">
        <AppNav t={t} />
      </nav>
      {modal && (
        <AppModal
          visible={visible}
          name={modal}
          selected={selected}
          accounts={accounts.data || []}
          sources={sources.data || []}
          holdings={holdings.data || []}
          flows={flows.data || []}
          goals={goals.data}
          ledger={ledger.data}
          locale={locale}
          setLocale={setLocale}
          t={t}
          user={user}
          onClose={() => {
            setModal(null);
            setSelected(null);
          }}
          onNotice={setNotice}
          onLogout={onLogout}
          onUserUpdate={onUserUpdate}
        />
      )}
    </div>
  );
}

/**
 * Two slim strips, both only inside the demo: where the visitor came from (only
 * for visitors who followed a link from the portfolio) and what "demo" means.
 */
function DemoBanner({ locale, t }: { locale: Locale; t: (k: MessageKey) => string }) {
  const demo = useDemo();
  if (!demo) return null;
  return (
    <>
      {demo.fromPortfolio && (
        <p className="demo-origin">
          <span>{t("demoOrigin")}</span>
          <a href={PORTFOLIO_URL}>{t("demoBack")} ↗</a>
        </p>
      )}
      <aside className="demo-banner" role="note" aria-label={t("demoLabel")} lang={locale}>
        <p>
          <strong>{t("demoLabel")}</strong>
          <span>{demo.readOnlyAccount ? locale === "zh-CN" ? "这是示例，不影响你的账本" : "This example does not affect your ledger" : t("demoBody")}</span>
          {demo.notice && (
            <span role="status">
              {demo.notice}{" "}
              {demo.onRetry && (
                <button className="text-button" type="button" onClick={demo.onRetry}>
                  {t("demoRetry")}
                </button>
              )}
            </span>
          )}
        </p>
        <div>
          {demo.readOnlyAccount ? <Link to="/">{locale === "zh-CN" ? "返回我的账本" : "Back to my ledger"}</Link> : <button className="text-button demo-login" onClick={demo.exit}>{locale === "zh-CN" ? "登录" : "Sign in"}</button>}
          {demo.dirty && <button className="text-button demo-reset" type="button" onClick={demo.reset}>
            {t("demoReset")}
          </button>}
          {!demo.readOnlyAccount && <Link className="primary-button demo-start" to="/register" state={{ background: { pathname: window.location.pathname, search: window.location.search } }}>
            <span className="desktop-label">{t("demoSave")}</span><span className="mobile-label">{locale === "zh-CN" ? "开始" : "Start"}</span>
          </Link>}
        </div>
      </aside>
    </>
  );
}

function AppNav({
  t,
  onNavigate,
}: {
  t: (k: MessageKey) => string;
  onNavigate?: () => void;
}) {
  const demo = useDemo();
  const location = useLocation();
  const base = demo?.readOnlyAccount ? "/demo" : "/app";
  const items = [
    { to: base, icon: LayoutDashboard, label: t("navOverview") },
    { to: `${base}/holdings`, icon: BriefcaseBusiness, label: t("navHoldings") },
    { to: `${base}/flows`, icon: WalletCards, label: t("navFlows") },
  ];
  return (
    <>
      {items.map((item, i) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.to}
            to={item.to}
            end={!i}
            onClick={onNavigate}
            className={({ isActive }) => (isActive || (demo?.readOnlyAccount && location.pathname.replace(/^\/app/, "/demo") === item.to) || (!i && ["/", "/en/"].includes(location.pathname)) ? "active" : "")}
          >
            <Icon />
            <span>{item.label}</span>
          </NavLink>
        );
      })}
    </>
  );
}

function OverviewPage({
  t,
  locale,
  holdings,
  flows,
  sources,
  goals,
  ledger,
  visible,
  onOpen,
  onRefresh,
}: {
  t: (k: MessageKey) => string;
  locale: Locale;
  holdings: Holding[];
  flows: Transaction[];
  sources: Source[];
  goals?: Goals;
  ledger?: LedgerSummary;
  visible: boolean;
  onOpen: (m: ModalName, i?: Holding) => void;
  onRefresh: () => void;
}) {
  const demo = useDemo();
  const baseCurrency = ledger?.base_currency || "CNY";
  const totals = portfolio(ledger);
  const month = ledger?.month || localMonth();
  const monthFlows = flows.filter((f) => f.transaction_date.startsWith(month));
  const net = ledger?.base.net_cashflow_minor;
  const totalAssets = ledger?.base.total_assets_minor;
  const unrealizedProfit = ledger?.base.unrealized_profit_minor ?? 0;
  return (
    <section className="page-stack">
      <article className="portfolio-hero">
        <div>
          <span>
            {t("totalAssets")} · {baseCurrency}
          </span>
          <strong>{money(totalAssets, locale, visible, baseCurrency)}</strong>
          <small className={deltaTone(unrealizedProfit)}>
            {unrealizedProfit > 0 ? <ArrowUpRight /> : unrealizedProfit < 0 ? <ArrowDownRight /> : null}
            {holdings.some((holding) => holding.average_cost == null) ? t("knownProfit") : t("unrealizedProfit")}{" "}
            {money(unrealizedProfit, locale, visible, baseCurrency)}
          </small>
          {ledger?.excluded_currencies.length ? (
            <small className="currency-warning">
              {t("excludedCurrencies")}: {ledger.excluded_currencies.join(", ")}
            </small>
          ) : null}
        </div>
        <div className="hero-buttons">
          <button className="ghost-button" onClick={onRefresh}>
            <RefreshCw />
            {t("refreshData")}
          </button>
          <button className="light-button" onClick={() => onOpen("import")}>
            <UploadCloud />
            {locale === "zh-CN" ? "截图导入持仓" : "Import a screenshot"}
          </button>
        </div>
        <footer>
          <span>
            {t("updated")}
            <b>{latestUpdate(holdings, locale)}</b>
          </span>
          <span>
            {t("dataSource")}
            <b>{demo ? t("demoDataSource") : locale === "zh-CN" ? "我的账本" : "My ledger"}</b>
          </span>
        </footer>
      </article>
      {!demo && !holdings.length && !flows.length && (
        <FirstUseChecklist t={t} locale={locale} goalsSet={(goals?.version ?? 0) > 0} onOpen={onOpen} />
      )}
      {demo && <DemoTour locale={locale} onOpen={onOpen} />}
      <div className="overview-grid">
        <article className="panel allocation-panel">
          <PanelTitle
            eyebrow={t("allocation")}
            title={t("allocationDesc")}
            action={
              <button className="text-button" onClick={() => onOpen("goals")}>
                {t("goals")}
                <ChevronRight />
              </button>
            }
          />
          {!ledger?.allocation.valid && <p className="form-warning">{t("allocationUnavailable")}</p>}
          <AllocationChart
            totals={totals}
            goals={goals}
            locale={locale}
            visible={visible}
            currency={baseCurrency}
            t={t}
          />
          <p className="allocation-note">{t("allocationBasis")}</p>
        </article>
        <article className="panel quick-panel">
          <PanelTitle
            eyebrow={t("review")}
            title={new Intl.DateTimeFormat(locale, {
              year: "numeric",
              month: "long",
            }).format(new Date())}
          />
          <div className="review-rows">
            <div>
              <span>{t("monthCashflow")}</span>
              <b>{money(net, locale, visible, baseCurrency)}</b>
            </div>
            <div>
              <span>{t("holdings")}</span>
              <b>{countLabel(holdings.length, t)}</b>
            </div>
            <div>
              <span>{t("flowRecords")}</span>
              <b>{countLabel(monthFlows.length, t)}</b>
            </div>
          </div>
          <button className="wide-button" onClick={() => onOpen("review")}>
            {t("startReview")}
            <ChevronRight />
          </button>
        </article>
      </div>
      <div className="ledger-status" role="status">
        <div><strong>{t("priceStatus")}</strong><span>{t(ledger?.price_refresh.status === "success" ? "pricesUpdated" : ledger?.price_refresh.status === "unconfigured" ? "pricesUnconfigured" : "pricesIncomplete")}</span></div>
        <div><span>{t("cashBalance")}</span><strong className="metric-value">{money(ledger?.base.cash_balance_minor, locale, visible, baseCurrency)}</strong></div>
        <div><span>{t("holdingsValue")}</span><strong className="metric-value">{money(ledger?.base.holdings_value_minor, locale, visible, baseCurrency)}</strong></div>
      </div>
      <article className="panel">
        <PanelTitle
          eyebrow={t("holdings")}
          title={t("marketFresh")}
          action={
            <Link className="text-button" to={demo?.readOnlyAccount ? "/demo/holdings" : "/app/holdings"}>
              {t("view")}
              <ChevronRight />
            </Link>
          }
        />
        {holdings.length ? (
          <div className="holding-rows">
            {[...holdings]
              .sort((a, b) => holdingValue(b) - holdingValue(a))
              .slice(0, 5)
              .map((h) => (
                <HoldingRow
                  key={h.holding_id}
                  holding={h}
                  source={sources.find((source) => source.source_id === h.source_id)?.name}
                  locale={locale}
                  visible={visible}
                  t={t}
                  onClick={() => onOpen("holding", h)}
                />
              ))}
          </div>
        ) : (
          <Empty
            title={t("emptyHoldings")}
            action={
              <button
                className="primary-button"
                onClick={() => onOpen("holding")}
              >
                {t("firstHolding")}
              </button>
            }
          />
        )}
      </article>
    </section>
  );
}

function DemoTour({ locale, onOpen }: { locale: Locale; onOpen: (name: ModalName) => void }) {
  const zh = locale === "zh-CN";
  const [steps, setSteps] = useState<number[]>(() => {
    try { const value: unknown = JSON.parse(sessionStorage.getItem("finunity-tour") || "[]"); return Array.isArray(value) ? value.filter(n => Number.isInteger(n) && n >= 0 && n < 4) : []; } catch { return []; }
  });
  const [collapsed, setCollapsed] = useState(() => sessionStorage.getItem("finunity-tour-collapsed") === "1");
  const [active, setActive] = useState<number | null>(null);
  useEffect(() => {
    const imported = () => setSteps(previous => {
      const next = [...new Set([...previous, 2])];
      sessionStorage.setItem("finunity-tour", JSON.stringify(next));
      return next;
    });
    addEventListener("finunity-demo-imported", imported);
    return () => removeEventListener("finunity-demo-imported", imported);
  }, []);
  useEffect(() => {
    if (active == null) return;
    const element = document.querySelector(active === 0 ? ".portfolio-hero" : ".allocation-panel");
    element?.classList.add("tour-highlight");
    element?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    return () => element?.classList.remove("tour-highlight");
  }, [active]);
  const labels = zh ? ["总资产怎么算", "三类资产差多少", "截图导入一次", "看本月复盘"] : ["How totals work", "Compare asset targets", "Import a screenshot", "Monthly review"];
  return <article className="demo-tour panel">
    <div><strong>{zh ? "看懂衡仓" : "Explore FinUnity"} · {steps.length}/4</strong><button className="text-button" onClick={() => { sessionStorage.setItem("finunity-tour-collapsed", collapsed ? "0" : "1"); setCollapsed(!collapsed); }}>{zh ? collapsed ? "展开" : "收起" : collapsed ? "Expand" : "Collapse"}</button></div>
    {!collapsed && <><nav>{labels.map((label, i) => <button key={label} className={steps.includes(i) ? "tour-done text-button" : "text-button"} onClick={() => {
      const next = i === 2 ? steps : [...new Set([...steps, i])]; setSteps(next); sessionStorage.setItem("finunity-tour", JSON.stringify(next)); setActive(i);
      if (i === 1) onOpen("goals");
      if (i === 2) { sessionStorage.setItem("finunity-tour-sample", "1"); onOpen("import"); }
      if (i === 3) onOpen("review");
    }}>{steps.includes(i) ? "✓ " : `${i + 1}. `}{label}</button>)}</nav>
    {active === 0 && <p>{zh ? "总资产只汇总主币种；美元、港币单列，不硬凑。" : "The total uses your base currency. USD and HKD stay separate without conversion."}</p>}
    {steps.length === 4 && <Link to="/register" state={{ background: { pathname: window.location.pathname } }}>{zh ? "换成你自己的数据" : "Start with your own data"}</Link>}</>}
  </article>;
}

/** Shown on a brand-new, empty ledger only; it disappears as soon as there is a holding or a flow. */
function FirstUseChecklist({
  t,
  locale,
  goalsSet,
  onOpen,
}: {
  t: (k: MessageKey) => string;
  locale: Locale;
  goalsSet: boolean;
  onOpen: (m: ModalName) => void;
}) {
  return (
    <article className="panel first-use">
      <PanelTitle eyebrow="" title={t("firstUseTitle")} />
      <ol className="first-use-steps">
        <li>
          <strong>{t("firstUseHoldings")}</strong>
          <span>
            <button className="secondary-button" onClick={() => onOpen("import")}>
              <UploadCloud />
              {t("importImage")}
            </button>
            <button className="text-button" onClick={() => onOpen("holding")}>
              {t("addHolding")}
            </button>
          </span>
        </li>
        <li>
          <strong>{t("firstUseGoals")}</strong>
          <span>
            {goalsSet && <em>{t("firstUseDone")}</em>}
            <button className="text-button" onClick={() => onOpen("goals")}>
              {t("goals")}
            </button>
          </span>
        </li>
        <li>
          <strong>{t("firstUseFlow")}</strong>
          <span>
            <button className="text-button" onClick={() => onOpen("flow")}>
              {t("addFlow")}
            </button>
          </span>
        </li>
      </ol>
      <figure className="first-use-preview">
        <img src={locale === "zh-CN" ? "/demo/overview.zh.png" : "/demo/overview.en.png"} alt={locale === "zh-CN" ? "导入后会这样显示（虚构示例）" : "How imported holdings appear (fictional example)"} loading="lazy" />
        <figcaption>{locale === "zh-CN" ? "导入后会这样显示 · 虚构示例" : "After import · Fictional example"}</figcaption>
      </figure>
      <p className="first-use-note">
        {t("firstUsePrivacy")}{" "}
        <a href={policyHref(locale, "privacy")} target="_blank" rel="noreferrer">
          {t("privacyPolicyLink")}
        </a>
      </p>
    </article>
  );
}

function HoldingsPage({
  t,
  locale,
  holdings,
  sources,
  visible,
  onOpen,
}: {
  t: (k: MessageKey) => string;
  locale: Locale;
  holdings: Holding[];
  sources: Source[];
  visible: boolean;
  onOpen: (m: ModalName, i?: Holding) => void;
}) {
  const [search, setSearch] = useState("");
  const [sourceId, setSourceId] = useState("");
  const [type, setType] = useState("");
  const filtered = holdings.filter(
    (h) =>
      (!search ||
        `${h.name} ${h.instrument_id || ""}`
          .toLowerCase()
          .includes(search.toLowerCase())) &&
      (!sourceId || h.source_id === sourceId) &&
      (!type || h.asset_type === type),
  );
  const types = [...new Set(holdings.map((h) => h.asset_type))];
  const clear = () => {
    setSearch("");
    setSourceId("");
    setType("");
  };
  return (
    <section className="page-stack">
      <PageHeading title={t("holdings")} body={t("marketFresh")}>
        <button className="secondary-button" onClick={() => onOpen("import")}>
          <UploadCloud />
          {t("importImage")}
        </button>
        <button className="primary-button" onClick={() => onOpen("holding")}>
          <Plus />
          {t("addHolding")}
        </button>
      </PageHeading>
      <div className="filter-bar">
        <label className="search-box">
          <Search />
          <input
            aria-label={t("search")}
            placeholder={t("search")}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <select
          aria-label={t("sourceLabel")}
          value={sourceId}
          onChange={(e) => setSourceId(e.target.value)}
        >
          <option value="">
            {t("all")} · {t("sourceLabel")}
          </option>
          {sources.map((source) => (
            <option key={source.source_id} value={source.source_id}>
              {source.name}
            </option>
          ))}
        </select>
        <select
          aria-label={t("type")}
          value={type}
          onChange={(e) => setType(e.target.value)}
        >
          <option value="">
            {t("all")} · {t("type")}
          </option>
          {types.map((x) => (
            <option key={x} value={x}>
              {assetTypeLabel(x, t)}
            </option>
          ))}
        </select>
        {(search || sourceId || type) && (
          <button className="text-button" onClick={clear}>
            {t("clearFilters")}
          </button>
        )}
      </div>
      {filtered.length ? (
        <div className="holdings-grid">
          {filtered.map((h) => (
            <HoldingCard
              key={h.holding_id}
              holding={h}
              source={sources.find((source) => source.source_id === h.source_id)?.name}
              locale={locale}
              visible={visible}
              t={t}
              onClick={() => onOpen("holding", h)}
            />
          ))}
        </div>
      ) : (
        <Empty
          title={holdings.length ? t("noResults") : t("emptyHoldings")}
          action={
            <button
              className="primary-button"
              onClick={() => (holdings.length ? clear() : onOpen("holding"))}
            >
              {holdings.length
                ? t("showAllResults")
                : t("firstHolding")}
            </button>
          }
        />
      )}
    </section>
  );
}

function AccountsPage({
  t,
  locale,
  accounts,
  holdings,
  flows,
  ledger,
  visible,
  onOpen,
}: {
  t: (k: MessageKey) => string;
  locale: Locale;
  accounts: Account[];
  holdings: Holding[];
  flows: Transaction[];
  ledger?: LedgerSummary;
  visible: boolean;
  onOpen: (m: ModalName, i?: Account) => void;
}) {
  return (
    <section className="page-stack">
      <PageHeading title={t("accounts")} body={t("accountSummary")}>
        <button className="primary-button" onClick={() => onOpen("account")}>
          <Plus />
          {t("addAccount")}
        </button>
      </PageHeading>
      {accounts.length ? (
        <div className="accounts-layout">
          <div className="accounts-grid">
            {accounts.map((a) => {
              const hs = holdings.filter((h) => h.account_id === a.account_id);
              const accountSummary = ledger?.accounts.find(
                (item) => item.account_id === a.account_id,
              );
              const total =
                accountSummary?.total_assets_minor;
              return (
                <button
                  className="account-card"
                  aria-label={`${t("edit")} ${a.name}`}
                  key={a.account_id}
                  onClick={() => onOpen("account", a)}
                >
                  <span className="account-icon">
                    <Building2 />
                  </span>
                  <span className="tag">
                    {accountTypeLabel(a.account_type, t)}
                  </span>
                  <h2>{a.name}</h2>
                  <strong>{money(total, locale, visible, a.currency)}</strong>
                  {accountSummary && (
                    <p className="account-breakdown">
                      {t("cashBalance")}{" "}
                      {money(
                        accountSummary.cash_balance_minor,
                        locale,
                        visible,
                        a.currency,
                      )}{" "}
                      · {t("holdingsValue")}{" "}
                      {money(
                        accountSummary.holdings_value_minor,
                        locale,
                        visible,
                        a.currency,
                      )}
                    </p>
                  )}
                  <p>
                    {hs.length} {t("holdings")} ·{" "}
                    {flows.filter((f) => f.account_id === a.account_id).length}{" "}
                    {t("flows")}
                  </p>
                  <span className="card-link">
                    {t("edit")}
                    <ChevronRight />
                  </span>
                </button>
              );
            })}
          </div>
          <aside className="panel account-aside">
            <PanelTitle eyebrow={t("marketStatus")} title={t("marketFresh")} />
            <div className="sync-orbit">
              <RefreshCw />
              <strong>{accounts.length}</strong>
              <span>{t("accounts")}</span>
            </div>
            <p>{t("accountSummary")}</p>
          </aside>
        </div>
      ) : (
        <Empty
          title={t("emptyAccounts")}
          action={
            <button
              className="primary-button"
              onClick={() => onOpen("account")}
            >
              {t("firstAccount")}
            </button>
          }
        />
      )}
    </section>
  );
}

void AccountsPage;

function FlowsPage({
  t,
  locale,
  sources,
  visible,
  onOpen,
}: {
  t: (k: MessageKey) => string;
  locale: Locale;
  flows: Transaction[];
  sources: Source[];
  visible: boolean;
  onOpen: (m: ModalName, i?: Transaction) => void;
}) {
  const [month, setMonth] = useState(localMonth());
  const api = useApi();
  const monthLedger = useQuery({
    queryKey: ["ledger", month],
    queryFn: () => api.ledgerSummary(month),
    refetchInterval: 60_000,
  });
  const filtered = (monthLedger.data?.records.transactions || []).filter((f) => f.transaction_date.startsWith(month));
  const summary = monthLedger.data?.base;
  const baseCurrency = monthLedger.data?.base_currency || "CNY";
  const income = summary?.income_minor;
  const expense = summary?.expense_minor;
  if (monthLedger.isPending) return <DataState icon={<LoaderCircle className="spin" />} title={t("loading")} />;
  if (monthLedger.isError) return <DataState icon={<AlertCircle />} title={errorText(monthLedger.error, t)}
    action={<button className="primary-button" onClick={() => monthLedger.refetch()}>{t("retry")}</button>} />;
  return (
    <section className="page-stack">
      <PageHeading title={t("flows")} body={t("marketFresh")}>
        <input
          className="month-input"
          aria-label={t("date")}
          type="month"
          value={month}
          onChange={(e) => { if (e.target.value) setMonth(e.target.value); }}
        />
        <button className="primary-button" onClick={() => onOpen("flow")}>
          <Plus />
          {t("addFlow")}
        </button>
      </PageHeading>
      <div className="flow-summary">
        <article>
          <span>{t("income")}</span>
          <strong className="positive">
            {money(income, locale, visible, baseCurrency)}
          </strong>
        </article>
        <article>
          <span>{t("expense")}</span>
          <strong>{money(expense, locale, visible, baseCurrency)}</strong>
        </article>
        <article className="featured">
          <span>{t("flows")}</span>
          <strong className={(summary?.net_cashflow_minor ?? 0) >= 0 ? "positive" : "negative"}>
            {money(summary?.net_cashflow_minor, locale, visible, baseCurrency)}
          </strong>
        </article>
      </div>
      {monthLedger.data?.excluded_currencies.length ? (
        <p className="currency-warning panel-warning">
          {t("excludedCurrencies")}:{" "}
          {monthLedger.data.excluded_currencies.join(", ")}
        </p>
      ) : null}
      <div className="flow-layout">
        <article className="panel flow-list">
          <PanelTitle eyebrow={t("flows")} title={formatMonth(month, locale)} />
          {filtered.length ? (
            filtered.map((f) => (
              <button
                className="flow-row"
                aria-label={`${t("edit")} ${f.title}`}
                key={f.transaction_id}
                onClick={() => onOpen("flow", f)}
              >
                <span
                  className={`flow-icon ${transactionDirection(f.transaction_type)}`}
                >
                  {isCashIncrease(f.transaction_type) ? (
                    <ArrowDownRight />
                  ) : isCashDecrease(f.transaction_type) ? (
                    <ArrowUpRight />
                  ) : (
                    <RefreshCw />
                  )}
                </span>
                <span>
                  <strong>{f.title}</strong>
                  <small>
                    {transactionTypeLabel(f.transaction_type, t)} ·{" "}
                    {f.transaction_date} ·{" "}
                    {sources.find((source) => source.source_id === f.source_id)?.name || "—"}
                  </small>
                </span>
                <b
                  className={
                    isCashIncrease(f.transaction_type) ? "positive" : ""
                  }
                >
                  {money(
                    isCashDecrease(f.transaction_type)
                      ? -Math.abs(f.amount_minor)
                      : f.amount_minor,
                    locale,
                    visible,
                    f.currency,
                  )}
                </b>
              </button>
            ))
          ) : (
            <Empty
              compact
              title={t("emptyFlows")}
              action={
                <button className="text-button" onClick={() => onOpen("flow")}>
                  {t("firstFlow")}
                </button>
              }
            />
          )}
        </article>
        <aside className="panel categories">
          <PanelTitle eyebrow={t("category")} title={t("flows")} />
          {(monthLedger.data?.categories || []).map(([name, value], index) => (
            <div className="category-row" key={name}>
              <span
                style={{
                  background: categoryPalette[index % categoryPalette.length],
                }}
              />
              <strong>{name}</strong>
              <b>{money(value, locale, visible, baseCurrency)}</b>
            </div>
          ))}
        </aside>
      </div>
    </section>
  );
}

function AppModal({
  visible,
  name,
  selected,
  accounts,
  sources,
  holdings,
  flows,
  goals,
  ledger,
  locale,
  setLocale,
  t,
  user,
  onClose,
  onNotice,
  onLogout,
  onUserUpdate,
}: {
  visible: boolean;
  name: Exclude<ModalName, null>;
  selected: Account | Holding | Transaction | null;
  accounts: Account[];
  sources: Source[];
  holdings: Holding[];
  flows: Transaction[];
  goals?: Goals;
  ledger?: LedgerSummary;
  locale: Locale;
  setLocale: (l: Locale) => void;
  t: (k: MessageKey) => string;
  user: User;
  onClose: () => void;
  onNotice: (n: Notice) => void;
  onLogout: () => void;
  onUserUpdate: (user: User) => void;
}) {
  const demo = useDemo();
  if (name === "account")
    return (
      <AccountDialog
        account={selected as Account | null}
        holdings={holdings}
        flows={flows}
        t={t}
        onClose={onClose}
        onNotice={onNotice}
      />
    );
  if (name === "holding")
    return (
      <HoldingDialog
        holding={selected as Holding | null}
        accounts={accounts}
        sources={sources}
        t={t}
        onClose={onClose}
        onNotice={onNotice}
      />
    );
  if (name === "flow")
    return (
      <FlowDialog
        flow={selected as Transaction | null}
        sources={sources}
        t={t}
        onClose={onClose}
        onNotice={onNotice}
      />
    );
  if (name === "import")
    return (
      <ImportDialog
        holdings={holdings}
        sources={sources}
        locale={locale}
        t={t}
        onClose={onClose}
        onNotice={onNotice}
      />
    );
  if (name === "goals")
    return <GoalsDialog t={t} onClose={onClose} onNotice={onNotice} />;
  if (name === "review")
    return (
      <ReviewDialog
        visible={visible}
        holdings={holdings}
        flows={flows}
        goals={goals}
        ledger={ledger}
        locale={locale}
        t={t}
        onClose={onClose}
      />
    );
  if (name === "alerts") {
    const alerts = financialAlerts(accounts, holdings, goals, ledger, t);
    return (
      <InfoDialog title={t("alerts")} closeLabel={t("close")} onClose={onClose}>
        {alerts.length ? (
          <div className="alert-list">
            {alerts.map((alert) => (
              <article
                key={alert.key}
                className={`financial-alert ${alert.kind}`}
              >
                <AlertCircle />
                <span>
                  <strong>{alert.title}</strong>
                  <small>{alert.body}</small>
                </span>
              </article>
            ))}
          </div>
        ) : (
          <Empty compact title={t("noAlerts")} />
        )}
      </InfoDialog>
    );
  }
  if (name === "privacy")
    return (
      <InfoDialog
        title={t("privacy")}
        closeLabel={t("close")}
        onClose={onClose}
      >
        <div className="privacy-copy">
          <ShieldCheck />
          <a href={policyHref(locale, "privacy")} target="_blank" rel="noreferrer">
            {locale === "zh-CN" ? "查看完整隐私政策" : "Read the full privacy policy"}
          </a>
          {demo ? (
            <p>{t("demoTrust")}.</p>
          ) : (
            <>
              <p>
                {t("dataIsolation")}. {t("accountSummary")}.
              </p>
              <p>
                {locale === "zh-CN"
                  ? "原始图片仅在服务器内存中识别；结构化结果会保存用于审计，持仓仍需你确认后才写入。API 密钥只配置在服务端。"
                  : "Raw images are parsed in server memory; structured results are retained for audit, and holdings are saved only after your confirmation. API keys stay on the server."}
              </p>
            </>
          )}
        </div>
      </InfoDialog>
    );
  return (
    <ProfileDialog
      user={user}
      locale={locale}
      setLocale={setLocale}
      t={t}
      onClose={onClose}
      onNotice={onNotice}
      onLogout={onLogout}
      onUserUpdate={onUserUpdate}
    />
  );
}

function ProfileDialog({
  user,
  locale,
  setLocale,
  t,
  onClose,
  onNotice,
  onLogout,
  onUserUpdate,
}: {
  user: User;
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: MessageKey) => string;
  onClose: () => void;
  onNotice: (notice: Notice) => void;
  onLogout: () => void;
  onUserUpdate: (user: User) => void;
}) {
  const qc = useQueryClient();
  const api = useApi();
  const demo = useDemo();
  const [baseCurrency, setBaseCurrency] = useState(user.base_currency || "CNY");
  const [displayName, setDisplayName] = useState(user.display_name || "");
  const profile = useMutation({
    mutationFn: () => api.saveProfile(displayName),
    onSuccess: updated => { onUserUpdate(updated); onNotice({ kind: "success", key: "saved" }); },
    onError: error => onNotice({ kind: "error", text: errorText(error, t) }),
  });
  const preferences = useMutation({
    mutationFn: () =>
      api.savePreferences(locale, baseCurrency, user.preferences_version || 0),
    onSuccess: (saved) => {
      onUserUpdate({
        ...user,
        language: saved.language,
        base_currency: saved.base_currency,
        preferences_version: saved.version,
      });
      qc.invalidateQueries({ queryKey: ["ledger"] });
      onNotice({ kind: "success", key: "saved" });
      onClose();
    },
    onError: (error) => onNotice({ kind: "error", text: errorText(error, t) }),
  });
  return (
    <InfoDialog title={t("profile")} closeLabel={t("close")} onClose={onClose}>
      <div className="profile-card">
        <span className="avatar large">
          {(user.display_name || user.username || user.email || "U")[0].toUpperCase()}
        </span>
        <h3>{user.display_name || user.username || user.email?.split("@")[0] || "User"}</h3>
        {demo ? (
          <section className="demo-profile">
            <h3>{t("demoProfileTitle")}</h3>
            <p>{t("demoProfileBody")}</p>
            <Link className="primary-button" to="/register" onClick={onClose} state={{ background: { pathname: window.location.pathname } }}>
              {t("demoSave")}
            </Link>
          </section>
        ) : (
          <>
            <p>{user.username || user.email}</p>
            <form onSubmit={event => { event.preventDefault(); profile.mutate(); }}>
              <Field label={locale === "zh-CN" ? "显示名称（可选）" : "Display name (optional)"}><input maxLength={100} value={displayName} onChange={event => setDisplayName(event.target.value)} /></Field>
              <button className="secondary-button" disabled={profile.isPending}>{locale === "zh-CN" ? "保存名称" : "Save name"}</button>
            </form>
            <AccountSecurity locale={locale} onLogout={onLogout} />
          </>
        )}
        <ThemeSettings t={t} />
        <LanguageToggle locale={locale} setLocale={setLocale} t={t} />
        <Field label={t("baseCurrency")} hint={t("baseCurrencyHint")}>
          <select
            value={baseCurrency}
            onChange={(event) => setBaseCurrency(event.target.value)}
          >
            <option value="CNY">CNY</option>
            <option value="USD">USD</option>
            <option value="HKD">HKD</option>
          </select>
        </Field>
        <button
          className="primary-button"
          disabled={preferences.isPending}
          onClick={() => preferences.mutate()}
        >
          {preferences.isPending ? (
            <LoaderCircle className="spin" />
          ) : (
            <Check />
          )}
          {t("savePreferences")}
        </button>
        <button
          className="danger-button"
          onClick={async () => {
            if (demo) return demo.exit();
            await api.logout();
            onLogout();
          }}
        >
          <LogOut />
          {t(demo ? "demoExit" : "logout")}
        </button>
      </div>
    </InfoDialog>
  );
}

function Dialog({
  title,
  closeLabel,
  onClose,
  children,
  wide = false,
  dismissOnBackdrop = true,
}: {
  title: string;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
  dismissOnBackdrop?: boolean;
}) {
  const ref = useRef<HTMLElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "Tab" && ref.current) {
        const focusable = [
          ...ref.current.querySelectorAll<HTMLElement>(
            "button,input,select,textarea,a[href]",
          ),
        ].filter((x) => !x.hasAttribute("disabled"));
        if (!focusable.length) return;
        const first = focusable[0],
          last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", key);
    ref.current?.querySelector<HTMLElement>("input,button,select")?.focus();
    return () => {
      document.removeEventListener("keydown", key);
      previous?.focus();
    };
  }, [onClose]);
  return (
    <div
      className="dialog-backdrop"
      onMouseDown={
        dismissOnBackdrop
          ? (e) => e.target === e.currentTarget && onClose()
          : undefined
      }
    >
      <section
        ref={ref}
        className={`dialog ${wide ? "wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        <header>
          <h2 id="dialog-title">{title}</h2>
          <button onClick={onClose} aria-label={closeLabel}>
            <X />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
const InfoDialog = ({
  title,
  closeLabel,
  onClose,
  children,
}: {
  title: string;
  closeLabel: string;
  onClose: () => void;
  children: ReactNode;
}) => (
  <Dialog title={title} closeLabel={closeLabel} onClose={onClose}>
    {children}
  </Dialog>
);

function ReviewDialog({
  visible,
  holdings,
  ledger,
  locale,
  t,
  onClose,
}: {
  visible: boolean;
  holdings: Holding[];
  flows: Transaction[];
  goals?: Goals;
  ledger?: LedgerSummary;
  locale: Locale;
  t: (k: MessageKey) => string;
  onClose: () => void;
}) {
  const baseCurrency = ledger?.base_currency || "CNY";
  const baseHoldings = holdings.filter(
    (holding) => holding.currency === baseCurrency,
  );
  const totals = portfolio(ledger);
  const shares = totals.buckets.map((bucket) => bucket.share_pct ?? 0);
  const drift = ledger?.allocation.drift_pct;
  const score = ledger?.allocation.score;
  const net = ledger?.base.net_cashflow_minor;
  return (
    <InfoDialog
      title={t("monthlyReview")}
      closeLabel={t("close")}
      onClose={onClose}
    >
      <div className="review-dialog">
        <span
          className="score"
          style={{
            background: `conic-gradient(var(--brand-500) ${score ?? 0}%, var(--bg-sunken) 0)`,
          }}
        >
          {/* Inner disc keeps the number off the gradient so it stays legible
              at any score, in any theme. */}
          <b>{score ?? "—"}</b>
        </span>
        <h3>{t("reviewScore")}</h3>
        {ledger?.allocation.valid ? (
          <ul className="review-details">
            <li>
              <strong>{t("reviewTargetGap")}</strong>
              <span>{drift?.toFixed(1)} pp</span>
            </li>
            <li>
              <strong>{t("reviewConcentration")}</strong>
              <span>{Math.max(...shares).toFixed(1)}%</span>
            </li>
            <li>
              <strong>{t("reviewNetFlow")}</strong>
              <span className={(net ?? 0) >= 0 ? "positive" : "negative"}>
                {money(net, locale, visible, baseCurrency)}
              </span>
            </li>
            <li>
              <strong>{t("reviewHoldings")}</strong>
              <span>{baseHoldings.length}</span>
            </li>
          </ul>
        ) : (
          <p>{t("reviewNoData")}</p>
        )}
      </div>
    </InfoDialog>
  );
}

function AccountDialog({
  account,
  holdings,
  flows,
  t,
  onClose,
  onNotice,
}: {
  account: Account | null;
  holdings: Holding[];
  flows: Transaction[];
  t: (k: MessageKey) => string;
  onClose: () => void;
  onNotice: (n: Notice) => void;
}) {
  const qc = useQueryClient();
  const api = useApi();
  const hasDependents = Boolean(
    account &&
      (holdings.some((holding) => holding.account_id === account.account_id) ||
        flows.some(
          (flow) =>
            flow.account_id === account.account_id ||
            flow.to_account_id === account.account_id,
        )),
  );
  const mutation = useMutation({
    mutationFn: (
      body: Partial<Account> & { name: string; client_request_id: string },
    ) => api.saveAccount(body),
    onSuccess: () => done(qc, "accounts", t, onClose, onNotice),
    onError: (e) => onNotice({ kind: "error", text: errorText(e, t) }),
  });
  const archive = useMutation({
    mutationFn: () => api.archiveAccount(account!.account_id, account!.version),
    onSuccess: () => done(qc, "accounts", t, onClose, onNotice, true),
  });
  return (
    <Dialog
      title={account ? `${t("edit")} · ${account.name}` : t("addAccount")}
      closeLabel={t("close")}
      onClose={onClose}
    >
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          mutation.mutate({
            account_id: account?.account_id,
            name: String(f.get("name")),
            account_type: String(f.get("type")),
            // Account balances and provider details are imported from screenshots,
            // tables, or transactions rather than entered during account setup.
            // Preserve existing values when editing an account without exposing
            // those fields in the creation form.
            institution: account?.institution || undefined,
            currency: account?.currency || "CNY",
            opening_balance_minor: account?.opening_balance_minor,
            version: account?.version,
            client_request_id: createClientRequestId(),
          });
        }}
      >
        <Field label={t("accountName")}>
          <input name="name" required defaultValue={account?.name} />
        </Field>
        <Field label={t("accountType")}>
          <select
            name="type"
            defaultValue={account?.account_type?.toLowerCase() || "brokerage"}
          >
            {["brokerage", "bank", "fund", "crypto", "other"].map((type) => (
              <option value={type} key={type}>
                {accountTypeLabel(type, t)}
              </option>
            ))}
          </select>
        </Field>
        <p className="form-warning" role="note">
          {t("accountBalanceImportHint")}
        </p>
        {hasDependents && (
          <p className="form-warning" role="note">
            {t("accountArchiveBlocked")}
          </p>
        )}
        <FormActions
          t={t}
          pending={mutation.isPending || archive.isPending}
          onClose={onClose}
          archive={
            account && !hasDependents ? () => archive.mutate() : undefined
          }
        />
      </form>
    </Dialog>
  );
}

function HoldingDialog({
  holding,
  sources,
  t,
  onClose,
  onNotice,
}: {
  holding: Holding | null;
  accounts: Account[];
  sources: Source[];
  t: (k: MessageKey) => string;
  onClose: () => void;
  onNotice: (n: Notice) => void;
}) {
  const qc = useQueryClient();
  const api = useApi();
  const [sourceId, setSourceId] = useState(holding?.source_id || "");
  const [sourceName, setSourceName] = useState(
    sources.find((source) => source.source_id === holding?.source_id)?.name || "",
  );
  const mutation = useMutation({
    mutationFn: async (item: Partial<Holding> & { name: string }) => {
      let resolvedSourceId = sourceId || undefined;
      const normalizedSource = sourceName.trim();
      if (normalizedSource && !resolvedSourceId) {
        const existing = sources.find((source) => source.name === normalizedSource);
        resolvedSourceId = existing?.source_id || (await api.saveSource({ name: normalizedSource })).source_id;
      }
      return api.saveHoldings([{ ...item, source_id: resolvedSourceId }]);
    },
    onSuccess: () => done(qc, "holdings", t, onClose, onNotice),
    onError: (e) => onNotice({ kind: "error", text: errorText(e, t) }),
  });
  const archive = useMutation({
    mutationFn: () => api.archiveHolding(holding!.holding_id, holding!.version),
    onSuccess: () => done(qc, "holdings", t, onClose, onNotice, true),
  });
  return (
    <Dialog
      title={holding ? `${t("edit")} · ${holding.name}` : t("addHolding")}
      closeLabel={t("close")}
      onClose={onClose}
      wide
    >
      <form
        className="form-grid two"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          mutation.mutate({
            holding_id: holding?.holding_id,
            name: String(f.get("name")),
            instrument_id: String(f.get("code")),
            asset_type: String(f.get("type")),
            quantity: Number(f.get("quantity")),
            average_cost: f.get("cost") ? Number(f.get("cost")) : undefined,
            current_price: f.get("price") ? Number(f.get("price")) : undefined,
            currency: String(f.get("currency")),
            risk_bucket: String(f.get("bucket")) as Holding["risk_bucket"],
            version: holding?.version,
          });
        }}
      >
        <Field label={t("sourceLabel")} hint={t("sourceOptionalHint")}>
          <input
            name="source"
            list="holding-sources"
            value={sourceName}
            onChange={(event) => {
              setSourceName(event.target.value);
              setSourceId(sources.find((source) => source.name === event.target.value)?.source_id || "");
            }}
          />
          <datalist id="holding-sources">
            {sources.map((source) => <option key={source.source_id} value={source.name} />)}
          </datalist>
        </Field>
        <Field label={t("holdingName")}>
          <input name="name" required defaultValue={holding?.name} />
        </Field>
        <Field label={t("codeLabel")}>
          <input name="code" defaultValue={holding?.instrument_id} />
        </Field>
        <Field label={t("type")}>
          <select
            name="type"
            defaultValue={holding?.asset_type?.toLowerCase() || "fund"}
          >
            {["stock", "etf", "fund", "cash", "bond", "crypto", "other"].map(
              (type) => (
                <option value={type} key={type}>
                  {assetTypeLabel(type, t)}
                </option>
              ),
            )}
          </select>
        </Field>
        <Field label={t("quantity")} hint={t("quantityHint")}>
          <input
            name="quantity"
            type="number"
            min="0"
            step="any"
            required
            defaultValue={holding?.quantity}
          />
        </Field>
        <Field label={t("averageCost")} hint={t("costHint")}>
          <input
            name="cost"
            type="number"
            min="0"
            step="any"
            defaultValue={holding?.average_cost}
          />
        </Field>
        <Field label={t("currentPrice")} hint={t("currentPriceHint")}>
          <input
            name="price"
            type="number"
            min="0"
            step="any"
            defaultValue={holding?.current_price}
          />
        </Field>
        <Field label={t("currency")}>
          <input name="currency" required defaultValue={holding?.currency || ""} />
        </Field>
        <Field label={t("riskBucket")} hint={t("riskBucketHint")}>
          <select
            name="bucket"
            defaultValue={holding?.risk_bucket || "BALANCED"}
          >
            {buckets.map((b) => (
              <option value={b.key} key={b.key}>
                {bucketLabel(b.key, t)}
              </option>
            ))}
          </select>
        </Field>
        <FormActions
          t={t}
          pending={mutation.isPending || archive.isPending}
          onClose={onClose}
          archive={holding ? () => archive.mutate() : undefined}
        />
      </form>
    </Dialog>
  );
}

function FlowDialog({
  flow,
  sources,
  t,
  onClose,
  onNotice,
}: {
  flow: Transaction | null;
  sources: Source[];
  t: (k: MessageKey) => string;
  onClose: () => void;
  onNotice: (n: Notice) => void;
}) {
  const qc = useQueryClient();
  const api = useApi();
  const [kind, setKind] = useState(flow?.transaction_type || "withdrawal");
  const [sourceId, setSourceId] = useState(flow?.source_id || "");
  const [sourceName, setSourceName] = useState(
    sources.find((source) => source.source_id === flow?.source_id)?.name || "",
  );
  const mutation = useMutation({
    mutationFn: async (item: Partial<Transaction> & { title: string }) => {
      let resolvedSourceId = sourceId || undefined;
      const normalizedSource = sourceName.trim();
      if (normalizedSource && !resolvedSourceId) {
        const existing = sources.find((source) => source.name === normalizedSource);
        resolvedSourceId = existing?.source_id || (await api.saveSource({ name: normalizedSource })).source_id;
      }
      return api.saveTransactions([{ ...item, source_id: resolvedSourceId }]);
    },
    onSuccess: () => done(qc, "transactions", t, onClose, onNotice),
    onError: (e) => onNotice({ kind: "error", text: errorText(e, t) }),
  });
  const archive = useMutation({
    mutationFn: () =>
      api.archiveTransaction(flow!.transaction_id, flow!.version),
    onSuccess: () => done(qc, "transactions", t, onClose, onNotice, true),
  });
  return (
    <Dialog
      title={flow ? `${t("edit")} · ${flow.title}` : t("addFlow")}
      closeLabel={t("close")}
      onClose={onClose}
      wide
    >
      <form
        className="form-grid two"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          mutation.mutate({
            transaction_id: flow?.transaction_id,
            title: String(f.get("title")),
            transaction_type: kind,
            transaction_date: String(f.get("date")),
            category: String(f.get("category")),
            amount_minor: Math.round(Number(f.get("amount")) * 100),
            currency: String(f.get("currency")).toUpperCase(),
            note: String(f.get("note")),
            version: flow?.version,
          });
        }}
      >
        <Field label={t("transactionTitle")}>
          <input name="title" required defaultValue={flow?.title} />
        </Field>
        <Field
          label={t("transactionType")}
          hint={
            kind === "buy" || kind === "sell"
              ? t("tradeHoldingHint")
              : t("flowSnapshotHint")
          }
        >
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as Transaction["transaction_type"]);
            }}
          >
            <option value="deposit">{t("transactionDeposit")}</option>
            <option value="withdrawal">{t("transactionWithdrawal")}</option>
            <option value="buy">{t("transactionBuy")}</option>
            <option value="sell">{t("transactionSell")}</option>
            <option value="dividend">{t("transactionDividend")}</option>
            <option value="interest">{t("transactionInterest")}</option>
            <option value="fee">{t("transactionFee")}</option>
            <option value="other">{t("otherTransaction")}</option>
          </select>
        </Field>
        <Field label={t("sourceLabel")} hint={t("sourceOptionalHint")}>
          <input
            name="source"
            list="flow-sources"
            value={sourceName}
            onChange={(event) => {
              setSourceName(event.target.value);
              setSourceId(sources.find((source) => source.name === event.target.value)?.source_id || "");
            }}
          />
          <datalist id="flow-sources">
            {sources.map((source) => <option key={source.source_id} value={source.name} />)}
          </datalist>
        </Field>
        <Field label={t("date")}>
          <input
            type="date"
            name="date"
            required
            defaultValue={
              flow?.transaction_date || new Date().toISOString().slice(0, 10)
            }
          />
        </Field>
        <Field label={t("amount")} hint={t("amountHint")}>
          <input
            type="number"
            min="0.01"
            step="0.01"
            name="amount"
            required
            defaultValue={flow ? Math.abs(flow.amount_minor) / 100 : undefined}
          />
        </Field>
        <Field label={t("category")}>
          <input name="category" defaultValue={flow?.category} />
        </Field>
        <Field label={t("currency")}>
          <input name="currency" required defaultValue={flow?.currency || "CNY"} />
        </Field>
        <Field label={t("note")}>
          <textarea name="note" defaultValue={flow?.note} />
        </Field>
        <FormActions
          t={t}
          pending={mutation.isPending || archive.isPending}
          onClose={onClose}
          archive={flow ? () => archive.mutate() : undefined}
        />
      </form>
    </Dialog>
  );
}

function ImportDialog({
  holdings: existingHoldings,
  sources,
  locale,
  t,
  onClose,
  onNotice,
}: {
  holdings: Holding[];
  sources: Source[];
  locale: Locale;
  t: (k: MessageKey) => string;
  onClose: () => void;
  onNotice: (n: Notice) => void;
}) {
  const holdingCurrencyOptions = ["CNY", "USD", "HKD", "USDT", "EUR", "GBP", "JPY"];
  const qc = useQueryClient();
  const api = useApi();
  const demo = useDemo();
  const [submitted, setSubmitted] = useState(false);
  const [mode, setMode] = useState("image");
  const [text, setText] = useState("");
  const requestId = useRef(createClientRequestId());
  const [file, setFile] = useState<File | null>(null);
  const [files, setFiles] = useState<File[]>(() => demo && sessionStorage.getItem("finunity-tour-sample") === "1" ? [new File([""], DEMO_SAMPLE_FILE_NAME, { type: "image/svg+xml" })] : []);
  useEffect(() => { sessionStorage.removeItem("finunity-tour-sample"); }, []);
  const [parsed, setParsed] = useState<ParsedHolding[]>([]);
  const [batch, setBatch] = useState<HoldingImport | null>(null);
  const [batchSourceId, setBatchSourceId] = useState("");
  const [failedFiles, setFailedFiles] = useState<File[]>([]);
  const [pendingCommit, setPendingCommit] = useState<{
    batchId: string;
    version: number;
    rows: Array<Record<string, unknown>>;
    clientRequestId: string;
  } | null>(null);
  const updateParsed = (index: number, patch: Partial<ParsedHolding>) =>
    setParsed((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item,
      ),
    );
  const importRows = (items: ParsedHolding[]) =>
    items.map((item, index) => ({
      row_id: `row-${index + 1}`,
      action: item.action || "ADD",
      holding_id: item.action === "UPDATE" ? item.holding_id : undefined,
      version: item.action === "UPDATE" ? item.holding_version : undefined,
      name: item.name,
      instrument_id: item.instrument_id,
      asset_type: (item.asset_type || "other").toUpperCase(),
      quantity: String(item.quantity),
      average_cost: item.average_cost,
      current_price: item.current_price,
      currency: item.currency,
      source_id: batchSourceId === "__clear__"
        ? undefined
        : batchSourceId || item.source_id || undefined,
      snapshot_date: item.snapshot_date,
      document_id: item.document_id,
      metadata: {
        risk_bucket: "BALANCED",
        quantity_unit: item.quantity_unit,
        quantity_kind: item.quantity_kind,
      },
    }));
  const parse = useMutation({
    mutationFn: async () => {
      let holdings: ParsedHolding[] = [];
      let documentIds: string[] = [];
      let failedFilesForResult: File[] = [];
      if (mode === "image") {
        const selectedFiles = failedFiles.length ? failedFiles : files;
        const uniqueFiles = [...new Map(selectedFiles.map((item) => [`${item.name}:${item.size}:${item.lastModified}`, item])).values()];
        const failed: File[] = [];
        for (let index = 0; index < uniqueFiles.length; index += 2) {
          const results = await Promise.all(uniqueFiles.slice(index, index + 2).map(async (item) => {
            try {
              return await api.parseImage(item, locale);
            } catch {
              failed.push(item);
              return null;
            }
          }));
          for (const result of results) {
            if (!result) continue;
            documentIds.push(result.document_id);
            holdings.push(...result.holdings);
          }
        }
        if (!holdings.length && failed.length) throw new Error(locale === "zh-CN" ? "图片识别失败，请重试失败图片。" : "Image recognition failed; retry the failed images.");
        failedFilesForResult = failed;
      } else {
        holdings = mode === "text"
          ? parseHoldingRows(parseDelimited(text, locale), undefined, locale)
          : await readHoldingFile(file!, undefined, locale);
      }
      const allHoldings = failedFiles.length ? [...parsed, ...holdings] : holdings;
      const created = await api.createHoldingImport({
        client_request_id: requestId.current,
        document_ids: [...(batch?.documents || []), ...documentIds],
        rows: importRows(allHoldings),
      });
      return api.previewHoldingImport(created.batch_id, {
        version: created.version,
        rows: importRows(allHoldings),
      }).then((preview) => ({
        holdings: allHoldings.map((item, index) => {
          const plan = preview.plan[index] || {};
          const candidates = Array.isArray(plan.candidate_holding_ids)
            ? plan.candidate_holding_ids.filter((value): value is string => typeof value === "string")
            : [];
          const action: HoldingImportAction = plan.action === "UPDATE" || plan.action === "SKIP"
            ? plan.action
            : "ADD";
          const target = existingHoldings.find((holding) => holding.holding_id === candidates[0]);
          return {
            ...item,
            action,
            candidate_holding_ids: candidates,
            needs_confirmation: item.needs_confirmation || Boolean(plan.needs_confirmation),
            holding_id: action === "UPDATE" ? target?.holding_id : undefined,
            holding_version: action === "UPDATE" ? target?.version : undefined,
          };
        }),
        batch: preview,
        failedFiles: failedFilesForResult,
      }));
    },
    onSuccess: (d) => { requestId.current = createClientRequestId(); setBatch(d.batch); setPendingCommit(null); setParsed(d.holdings); setFailedFiles(d.failedFiles); },
    onError: (e) => { setFailedFiles(failedFiles.length ? failedFiles : files); onNotice({ kind: "error", text: errorText(e, t) }); },
  });
  const save = useMutation({
    mutationFn: async () => {
      setSubmitted(true);
      if (pendingCommit) {
        return api.commitHoldingImport(pendingCommit.batchId, {
          client_request_id: pendingCommit.clientRequestId,
          version: pendingCommit.version,
          rows: pendingCommit.rows,
        });
      }
      const sourceIds = new Map<string, string>();
      const items = [];
      for (const h of parsed) {
        let sourceId = batchSourceId === "__clear__"
          ? undefined
          : batchSourceId || h.source_id || undefined;
        const sourceName = batchSourceId === "__clear__" ? undefined : h.source_name?.trim();
        if (!sourceId && sourceName) {
          sourceId = sourceIds.get(sourceName);
          if (!sourceId) {
            sourceId = sources.find((source) => source.name === sourceName)?.source_id;
            if (!sourceId) sourceId = (await api.saveSource({ name: sourceName })).source_id;
            sourceIds.set(sourceName, sourceId);
          }
        }
        items.push({
          ...h,
          name: h.name,
          asset_type: h.asset_type || "other",
          source_id: sourceId,
          risk_bucket: "BALANCED" as const,
        });
      }
      if (!batch) return api.saveHoldings(items, requestId.current);
      const rows = [];
      for (const item of items) {
        let sourceId = batchSourceId === "__clear__" ? undefined : item.source_id;
        const sourceName = batchSourceId === "__clear__" ? undefined : item.source_name?.trim();
        if (!sourceId && sourceName) {
          sourceId = sourceIds.get(sourceName) || sources.find((source) => source.name === sourceName)?.source_id;
          if (!sourceId) sourceId = (await api.saveSource({ name: sourceName })).source_id;
          sourceIds.set(sourceName, sourceId);
        }
        rows.push({
          ...item,
          source_id: sourceId,
          action: item.action || "ADD",
          holding_id: item.action === "UPDATE" ? item.holding_id : undefined,
          version: item.action === "UPDATE" ? item.holding_version : undefined,
        });
      }
      const preview = await api.previewHoldingImport(batch.batch_id, { version: batch.version, rows });
      setPendingCommit({
        batchId: batch.batch_id,
        version: preview.version,
        rows,
        clientRequestId: requestId.current,
      });
      return api.commitHoldingImport(batch.batch_id, {
        client_request_id: requestId.current,
        version: preview.version,
        rows,
      });
    },
    onSuccess: () => {
      if (demo && mode === "image") dispatchEvent(new Event("finunity-demo-imported"));
      return done(qc, "holdings", t, onClose, onNotice);
    },
    onError: (e) => {
      // Validation responses guarantee no write; uncertain responses must retry unchanged.
      if (e instanceof ApiError && [400, 422].includes(e.status)) {
        setSubmitted(false); setPendingCommit(null); requestId.current = createClientRequestId();
      }
      onNotice({ kind: "error", text: errorText(e, t) });
    },
  });
  return (
    <Dialog
      title={t("uploadTitle")}
      closeLabel={t("close")}
      dismissOnBackdrop={false}
      onClose={() => { if (!parse.isPending && !save.isPending && (!submitted || window.confirm(locale === "zh-CN" ? "保存结果尚未确认，建议先重试当前批次。仍要关闭？" : "Save is unconfirmed. Retry this batch first. Close anyway?"))) onClose(); }}
      wide
    >
      <div className="import-dialog">
        <fieldset disabled={parse.isPending || save.isPending} className="import-fields">
        <fieldset disabled={submitted} className="import-fields">
        <div className="auth-tabs import-tabs">
          {[["image", locale === "zh-CN" ? "截图" : "Image"], ["table", locale === "zh-CN" ? "表格" : "Table"], ["text", locale === "zh-CN" ? "文本" : "Text"]].map(([value, label]) => <button key={value} className={mode === value ? "active" : ""} onClick={() => { setMode(value); setFile(null); setFiles([]); setFailedFiles([]); setBatch(null); setPendingCommit(null); setParsed([]); }}>{label}</button>)}
        </div>
        {mode !== "image" && <p className="form-warning">{locale === "zh-CN" ? "支持 XLSX 首张工作表、UTF-8 CSV/TSV/TXT 和粘贴 Excel 单元格。首行为表头，最多 500 条。每行可使用不同币种和来源；币种缺失时请在预览中补选。" : "Import the first XLSX sheet, UTF-8 CSV/TSV/TXT, or pasted Excel cells. Use a header row, up to 500 records. Each row may use its own currency and source; fill in missing currencies in the preview."}<br /><button className="secondary-button" onClick={() => { const template = locale === "zh-CN" ? importTemplate : englishImportTemplate; const url = URL.createObjectURL(new Blob(["\uFEFF" + template], { type: "text/csv;charset=utf-8" })); const link = document.createElement("a"); link.href = url; link.download = "holdings-template.csv"; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }}>{locale === "zh-CN" ? "下载 CSV 模板" : "Download CSV template"}</button></p>}
        {mode === "text" && <Field label={locale === "zh-CN" ? "粘贴带表头的文本" : "Paste data with headers"}><textarea rows={7} value={text} placeholder={locale === "zh-CN" ? importTemplate : englishImportTemplate} onChange={e => { setText(e.target.value); setParsed([]); }} /></Field>}
        {mode === "image" && !demo && <p>{t("uploadBody")}</p>}
        {mode === "image" && !demo && <p className="form-warning">{t("importPrivacyNote")} <a href={policyHref(locale, "privacy")} target="_blank" rel="noreferrer">{t("privacyPolicyLink")}</a></p>}
        <Field label={t("sourceLabel")} hint={t("sourceOptionalHint")}>
          <select
            value={batchSourceId}
            onChange={(e) => setBatchSourceId(e.target.value)}
          >
            <option value="">{locale === "zh-CN" ? "保留识别来源" : "Keep detected source"}</option>
            <option value="__clear__">{locale === "zh-CN" ? "清除本批来源" : "Clear source for this batch"}</option>
            {sources.map((source) => (
              <option key={source.source_id} value={source.source_id}>
                {source.name}
              </option>
            ))}
          </select>
        </Field>
        {demo && mode === "image" ? (
          <section className="demo-sample">
            <img src={demoSampleImage(locale)} alt={t("demoSampleAlt")} width="300" height="320" />
            <div>
              <h3>{t("demoSampleTitle")}</h3>
              <p>{t("demoSampleBody")}</p>
              <Link className="text-link" to="/register" onClick={onClose} state={{ reason: "screenshot", background: { pathname: window.location.pathname } }}>{locale === "zh-CN" ? "识别我自己的截图" : "Recognize my screenshot"}</Link>
              <button
                type="button"
                className="secondary-button"
                disabled={files.length > 0}
                onClick={() => {
                  setFile(null); setFailedFiles([]); setBatch(null); setPendingCommit(null); setParsed([]);
                  setFiles([new File([""], DEMO_SAMPLE_FILE_NAME, { type: "image/svg+xml" })]);
                }}
              >
                <FileImage />
                {files.length ? t("demoSampleChosen") : t("demoSampleUse")}
              </button>
            </div>
          </section>
        ) : mode !== "text" && <label className="upload-drop">
          <input
            type="file"
            key={mode}
            accept={mode === "image" ? "image/jpeg,image/png,image/webp" : ".xlsx,.csv,.tsv,.txt"}
            multiple={mode === "image"}
            onChange={(e) => {
              const selectedFiles = Array.from(e.target.files || []);
              setFile(null); setFiles([]); setFailedFiles([]); setBatch(null); setPendingCommit(null); setParsed([]);
              if (selectedFiles.some((next) => next.size > 10 * 1024 * 1024) || selectedFiles.reduce((total, next) => total + next.size, 0) > 50 * 1024 * 1024) {
                onNotice({ kind: "error", key: "imageTooLarge" });
                return;
              }
              if (mode === "image") setFiles(selectedFiles.slice(0, 20));
              else setFile(selectedFiles[0] || null);
              setParsed([]);
            }}
          />
          <FileImage />
          <strong>{mode === "image" ? (files.length ? `${files.length} ${locale === "zh-CN" ? "张图片" : "images"}` : t("chooseImage")) : (file?.name || (locale === "zh-CN" ? "选择表格文件" : "Choose a file"))}</strong>
          <small>{mode === "image" ? "PNG, JPG, WEBP · 20 max · 10 MB each" : "XLSX, CSV, TSV, TXT"} · 10 MB max</small>
        </label>}
        {((mode === "image" && files.length > 0) || (mode !== "image" && (file || (mode === "text" && text.trim())))) && !parsed.length && (
          <button
            className="primary-button"
            disabled={parse.isPending}
            onClick={() => parse.mutate()}
          >
            {parse.isPending ? (
              <LoaderCircle className="spin" />
            ) : (
              <UploadCloud />
            )}
            {parse.isPending ? t("parsing") : failedFiles.length ? t("retry") : t("uploadTitle")}
          </button>
        )}
        </fieldset>
        {submitted && !save.isPending && <p className="form-warning" role="status">{locale === "zh-CN" ? "尚未确认保存结果，已锁定此批次。请重试原批次，不要重新导入；重试不会重复写入。" : "Save result is uncertain. This batch is locked; retry it unchanged to avoid duplicate records."}</p>}
        {parsed.length > 0 && (
          <div className="parsed-list">
            {failedFiles.length > 0 && <p className="form-warning" role="status">{locale === "zh-CN" ? `${failedFiles.length} 张图片识别失败，可单独重试。` : `${failedFiles.length} image(s) failed and can be retried.`}<button className="text-button" type="button" onClick={() => parse.mutate()}>{t("retry")}</button></p>}
            <p className="import-preview-legend">{locale === "zh-CN" ? "逐行核对：名称、数量、成本价、现价、类型；下方显示代码与币种。" : "Review each row: name, quantity, cost, price, type; code and currency appear below."}</p>
            {parsed.map((h, i) => (
              <fieldset disabled={submitted} className="parsed-row" key={`${i}`}>
                <Field label={t("holdingName")}>
                <input
                  aria-label={`${t("holdingName")} ${i + 1}`}
                  value={h.name}
                  onChange={(event) =>
                    updateParsed(i, { name: event.target.value })
                  }
                />
                </Field>
                <Field
                  label={
                    h.quantity_kind === "VALUE"
                      ? `${t("quantity")} (${h.quantity_unit || h.currency || (locale === "zh-CN" ? "报价币种" : "quote currency")} ${locale === "zh-CN" ? "金额" : "value"})`
                      : t("quantity")
                  }
                >
                <input
                  aria-label={`${t("quantity")} ${i + 1}`}
                  type="number"
                  min="0"
                  step="any"
                  value={h.quantity}
                  onChange={(event) =>
                    updateParsed(i, { quantity: Number(event.target.value) })
                  }
                />
                </Field>
                <Field label={t("averageCost")}>
                <input
                  aria-label={`${t("averageCost")} ${i + 1}`}
                  type="number"
                  min="0"
                  step="any"
                  value={h.average_cost ?? ""}
                  onChange={(event) =>
                    updateParsed(i, {
                      average_cost: event.target.value
                        ? Number(event.target.value)
                        : undefined,
                    })
                  }
                />
                </Field>
                <Field label={t("currentPrice")}>
                <input
                  aria-label={`${t("currentPrice")} ${i + 1}`}
                  type="number"
                  min="0"
                  step="any"
                  value={h.current_price ?? ""}
                  onChange={(event) =>
                    updateParsed(i, {
                      current_price: event.target.value
                        ? Number(event.target.value)
                        : undefined,
                    })
                  }
                />
                </Field>
                <Field label={t("type")}>
                <select
                  aria-label={`${t("type")} ${i + 1}`}
                  value={h.asset_type || "other"}
                  onChange={(event) =>
                    updateParsed(i, { asset_type: event.target.value })
                  }
                >
                  {[
                    "stock",
                    "etf",
                    "fund",
                    "cash",
                    "bond",
                    "crypto",
                    "other",
                  ].map((type) => (
                    <option key={type} value={type}>
                      {assetTypeLabel(type, t)}
                    </option>
                  ))}
                </select>
                </Field>
                <Field label={t("currency")}>
                  <select
                    aria-label={`${t("currency")} ${i + 1}`}
                    value={h.currency || ""}
                    onChange={(event) =>
                      updateParsed(i, { currency: event.target.value })
                    }
                  >
                    {[...new Set([
                      ...holdingCurrencyOptions,
                      h.currency,
                    ].filter(Boolean))].map((currency) => (
                      <option key={currency} value={currency}>
                        {currency}
                      </option>
                    ))}
                  </select>
                </Field>
                {h.candidate_holding_ids?.length ? (
                  <Field label={locale === "zh-CN" ? "导入决定" : "Import decision"}>
                    <select
                      aria-label={`${locale === "zh-CN" ? "导入决定" : "Import decision"} ${i + 1}`}
                      value={h.action || "ADD"}
                      onChange={(event) => {
                        const action = event.target.value as HoldingImportAction;
                        const target = existingHoldings.find((holding) => holding.holding_id === h.candidate_holding_ids?.[0]);
                        updateParsed(i, {
                          action,
                          holding_id: action === "UPDATE" ? target?.holding_id : undefined,
                          holding_version: action === "UPDATE" ? target?.version : undefined,
                        });
                      }}
                    >
                      <option value="ADD">{locale === "zh-CN" ? "另存一笔" : "Save separately"}</option>
                      {h.candidate_holding_ids.length === 1 && <option value="UPDATE">{locale === "zh-CN" ? "更新已有持仓" : "Update existing"}</option>}
                      <option value="SKIP">{locale === "zh-CN" ? "暂不导入" : "Skip for now"}</option>
                    </select>
                    {h.candidate_holding_ids.length > 1 && <small className="form-hint">{locale === "zh-CN" ? "发现多个候选，请另存或暂不导入。" : "Multiple candidates found; save separately or skip."}</small>}
                  </Field>
                ) : null}
                {h.action === "UPDATE" && h.candidate_holding_ids?.length === 1 && (
                  <Field label={locale === "zh-CN" ? "更新目标" : "Update target"}>
                    <select
                      value={h.holding_id || ""}
                      onChange={(event) => {
                        const target = existingHoldings.find((holding) => holding.holding_id === event.target.value);
                        updateParsed(i, { holding_id: target?.holding_id, holding_version: target?.version });
                      }}
                    >
                      {h.candidate_holding_ids.map((id) => {
                        const target = existingHoldings.find((holding) => holding.holding_id === id);
                        return <option key={id} value={id}>{target ? `${target.name} · ${target.currency} · ${target.quantity}` : id}</option>;
                      })}
                    </select>
                  </Field>
                )}
                {mode === "image" && <small
                  className={
                    (h.confidence ?? 1) < 0.7 || h.needs_confirmation
                      ? "low-confidence"
                      : ""
                  }
                >
                  {Math.round((h.confidence ?? 1) * 100)}% ·{" "}
                  {h.needs_confirmation
                    ? t("needsConfirmation")
                    : t("recognized")}
                </small>}
                {h.review_reasons?.length ? (
                  <small className="form-warning import-review-reasons">
                    {h.review_reasons.join("；")}
                  </small>
                ) : null}
                <span className="import-row-meta">
                  {h.instrument_id || "—"} · {h.currency || t("currencyRequired")}
                  {h.quantity_kind === "VALUE"
                    ? ` · ${locale === "zh-CN" ? "数量按金额" : "value quantity"} (${h.quantity_unit || h.currency || "—"})`
                    : ""}
                </span>
                <button type="button" className="secondary-button" aria-label={`${locale === "zh-CN" ? "移除此行" : "Remove row"} ${h.name}`} onClick={() => setParsed(current => current.filter((_, index) => index !== i))}><Trash2 /></button>
              </fieldset>
            ))}
            <p className="form-warning" role="note">
              {t("reviewBeforeImport")}
            </p>
            <button
              className="primary-button"
              disabled={
                save.isPending ||
                parsed.some((item) => item.action !== "SKIP" && (!item.name.trim() || !item.currency || !Number.isFinite(item.quantity) || item.quantity <= 0 || (item.action === "UPDATE" && (!item.holding_id || !item.holding_version)) || [item.average_cost, item.current_price].some(value => value != null && (!Number.isFinite(value) || value < 0))))
              }
              onClick={() => save.mutate()}
            >
              {save.isPending ? <LoaderCircle className="spin" /> : <Check />}
              {t("importSelected")}
            </button>
          </div>
        )}
        </fieldset>
      </div>
    </Dialog>
  );
}

function GoalsDialog({
  t,
  onClose,
  onNotice,
}: {
  t: (k: MessageKey) => string;
  onClose: () => void;
  onNotice: (n: Notice) => void;
}) {
  const api = useApi();
  const goals = useQuery({ queryKey: ["ledger", localMonth()], queryFn: () => api.ledgerSummary(localMonth()), select: (data) => data.goals });
  const [dirty, setDirty] = useState(false);
  const requestClose = () => {
    if (!dirty || window.confirm(t("discardChangesConfirm"))) onClose();
  };
  return (
    <Dialog title={t("goals")} closeLabel={t("close")} onClose={requestClose}>
      {goals.isLoading ? (
        <DataState
          icon={<LoaderCircle className="spin" />}
          title={t("loading")}
        />
      ) : goals.isError || !goals.data ? (
        <DataState
          icon={<AlertCircle />}
          title={errorText(goals.error, t)}
          action={
            <button className="primary-button" onClick={() => goals.refetch()}>
              {t("retry")}
            </button>
          }
        />
      ) : (
        <GoalsForm
          initial={goals.data}
          t={t}
          onCancel={requestClose}
          onSaved={onClose}
          onDirty={() => setDirty(true)}
          onNotice={onNotice}
        />
      )}
    </Dialog>
  );
}

function GoalsForm({
  initial,
  t,
  onCancel,
  onSaved,
  onDirty,
  onNotice,
}: {
  initial: Goals;
  t: (k: MessageKey) => string;
  onCancel: () => void;
  onSaved: () => void;
  onDirty: () => void;
  onNotice: (n: Notice) => void;
}) {
  const api = useApi();
  const queryClient = useQueryClient();
  const [values, setValues] = useState([
    initial.aggressive_pct,
    initial.balanced_pct,
    initial.defensive_pct,
  ]);
  const total = values.reduce((a, b) => a + b, 0);
  const mutation = useMutation({
    mutationFn: () => api.saveGoals(values, initial.version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["goals"] });
      queryClient.invalidateQueries({ queryKey: ["ledger"] });
      onNotice({ kind: "success", key: "saved" });
      onSaved();
    },
    onError: (e) => onNotice({ kind: "error", text: errorText(e, t) }),
  });
  return (
    <div className="goals-form">
      <p>{t("goalsHelp")}</p>
      {buckets.map((b, i) => (
        <label key={b.key}>
          <span style={{ background: b.color }} />
          <strong>{bucketLabel(b.key, t)}</strong>
          <input
            aria-label={`${bucketLabel(b.key, t)} %`}
            type="number"
            min="0"
            max="100"
            value={values[i]}
            onChange={(e) => {
              onDirty();
              setValues(
                values.map((v, j) => (j === i ? Number(e.target.value) : v)),
              );
            }}
          />
          <b>%</b>
        </label>
      ))}
      <div
        className={total === 100 ? "goal-total valid" : "goal-total invalid"}
      >
        <span>{t("total")}</span>
        <strong>{total}%</strong>
      </div>
      <div className="goal-actions">
        <button className="secondary-button" type="button" onClick={onCancel}>
          {t("cancel")}
        </button>
        <button
          className="primary-button"
          disabled={total !== 100 || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          {mutation.isPending ? <LoaderCircle className="spin" /> : <Target />}
          {t("save")}
        </button>
      </div>
    </div>
  );
}

function FormActions({
  t,
  pending,
  onClose,
  archive,
}: {
  t: (k: MessageKey) => string;
  pending: boolean;
  onClose: () => void;
  archive?: () => void;
}) {
  const [confirming, setConfirming] = useState(false);
  if (confirming && archive)
    return (
      <div className="archive-confirm" role="alert">
        <p>{t("archiveConfirm")}</p>
        <button
          className="secondary-button"
          type="button"
          onClick={() => setConfirming(false)}
        >
          {t("cancel")}
        </button>
        <button
          className="danger-button"
          type="button"
          onClick={archive}
          disabled={pending}
        >
          {pending ? <LoaderCircle className="spin" /> : <Trash2 />}
          {t("confirmArchive")}
        </button>
      </div>
    );
  return (
    <div className="form-actions">
      {archive && (
        <button
          className="danger-button"
          type="button"
          onClick={() => setConfirming(true)}
          disabled={pending}
        >
          <Trash2 />
          {t("archive")}
        </button>
      )}
      <span />
      <button className="secondary-button" type="button" onClick={onClose}>
        {t("cancel")}
      </button>
      <button className="primary-button" disabled={pending}>
        {pending ? <LoaderCircle className="spin" /> : <Check />}
        {pending ? t("saving") : t("save")}
      </button>
    </div>
  );
}
const Field = ({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) => (
  <label className="field">
    <span>{label}</span>
    {children}
    {hint && <small>{hint}</small>}
  </label>
);
function InlineNotice({
  notice,
  t,
  onClose,
}: {
  notice: NonNullable<Notice>;
  t: (k: MessageKey) => string;
  onClose?: () => void;
}) {
  const demo = useDemo();
  return (
    <div
      className={`inline-notice ${notice.kind}`}
      role={notice.kind === "error" ? "alert" : "status"}
    >
      {notice.kind === "success" ? (
        <Check />
      ) : notice.kind === "error" ? (
        <AlertCircle />
      ) : (
        <CloudOff />
      )}
      <span>
        {notice.key ? t(demo && notice.key === "saved" ? "demoSaved" : notice.key) : notice.text}
        {notice.suffix}
      </span>
      {demo && notice.kind === "success" && <Link to="/register" state={{ background: { pathname: window.location.pathname } }}>{t("demoSave")}</Link>}
      {onClose && (
        <button aria-label={t("closeNotice")} onClick={onClose}>
          <X />
        </button>
      )}
    </div>
  );
}
const DataState = ({
  icon,
  title,
  action,
}: {
  icon: ReactNode;
  title: string;
  action?: ReactNode;
}) => (
  <section className="data-state">
    {icon}
    <h2>{title}</h2>
    {action}
  </section>
);
const Empty = ({
  title,
  action,
  compact = false,
}: {
  title: string;
  action?: ReactNode;
  compact?: boolean;
}) => (
  <div className={`empty ${compact ? "compact" : ""}`}>
    <span>
      <CircleDollarSign />
    </span>
    <h3>{title}</h3>
    {action}
  </div>
);
const PageHeading = ({
  title,
  body,
  children,
}: {
  title: string;
  body: string;
  children: ReactNode;
}) => (
  <header className="page-heading">
    <div>
      <p className="eyebrow">FINUNITY</p>
      <h2>{title}</h2>
      <p>{body}</p>
    </div>
    <div>{children}</div>
  </header>
);
const PanelTitle = ({
  eyebrow,
  title,
  action,
}: {
  eyebrow: string;
  title: string;
  action?: ReactNode;
}) => (
  <header className="panel-title">
    <div>
      <p>{eyebrow}</p>
      <h2>{title}</h2>
    </div>
    {action}
  </header>
);
const HoldingRow = ({
  holding,
  source,
  locale,
  visible,
  t,
  onClick,
}: {
  holding: Holding;
  source?: string;
  locale: Locale;
  visible: boolean;
  t: (k: MessageKey) => string;
  onClick: () => void;
}) => {
  const bucket = holding.risk_bucket || "BALANCED";
  const profit = holding.unrealized_profit_minor;
  return (
    <button
      className="holding-row"
      data-bucket={bucket}
      onClick={onClick}
      aria-label={`${t("edit")} ${holding.name}`}
    >
      <span className="asset-avatar">{holding.name[0]}</span>
      <span>
        <span className="row-name">
          <strong>{holding.name}</strong>
          <span className="tag bucket-tag">{bucketLabel(bucket, t)}</span>
        </span>
        <small>
          {holding.instrument_id || assetTypeLabel(holding.asset_type, t)}
          {source ? ` · ${source}` : ""}
        </small>
      </span>
      <span className="row-amount">
        <b>{money(holdingValue(holding), locale, visible, holding.currency)}</b>
        {profit != null && (
          <span className={`row-delta ${deltaTone(profit)}`}>
            {profit > 0 && visible ? "+" : ""}
            {money(profit, locale, visible, holding.currency)}
          </span>
        )}
      </span>
    </button>
  );
};
function HoldingCard({
  holding,
  source,
  locale,
  visible,
  t,
  onClick,
}: {
  holding: Holding;
  source?: string;
  locale: Locale;
  visible: boolean;
  t: (k: MessageKey) => string;
  onClick: () => void;
}) {
  const profit = holding.unrealized_profit_minor;
  return (
    <button
      className="holding-card"
      data-bucket={holding.risk_bucket || "BALANCED"}
      aria-label={`${t("edit")} ${holding.name}`}
      onClick={onClick}
    >
      <header>
        <span className="asset-avatar">{holding.name[0]}</span>
        <span className="tag bucket-tag">{bucketLabel(holding.risk_bucket || "BALANCED", t)}</span>
      </header>
      <h2>{holding.name}</h2>
      <p>
        {holding.instrument_id || "—"}{source ? ` · ${source}` : ""}
      </p>
      <strong>
        {money(holdingValue(holding), locale, visible, holding.currency)}
      </strong>
      {/* Joined rather than interpolated: a holding with no recorded price
          source would otherwise render a trailing "基金 · " with nothing
          after the separator. */}
      <p className="price-meta">
        {[
          assetTypeLabel(holding.asset_type, t),
          holding.valuation_status === "estimated"
            ? t("estimatedPrice")
            : holding.market_source === "manual"
              ? t("manualPrice")
              : holding.market_source,
        ]
          .filter(Boolean)
          .join(" · ")}
      </p>
      <footer>
        <span className={deltaTone(profit)}>
          {t("unrealizedProfit")} {profit != null && profit > 0 && visible ? "+" : ""}
          {money(profit, locale, visible, holding.currency)}
        </span>
        <span>
          {t("priceDate")}: {holding.price_as_of ? new Intl.DateTimeFormat(locale, {
            month: "short", day: "numeric",
          }).format(new Date(holding.price_as_of)) : "—"}
        </span>
      </footer>
    </button>
  );
}
function AllocationChart({
  totals,
  locale,
  visible,
  currency,
  t,
}: {
  totals: ReturnType<typeof portfolio>;
  goals?: Goals;
  locale: Locale;
  visible: boolean;
  currency: string;
  t: (k: MessageKey) => string;
}) {
  const zh = locale === "zh-CN";
  return <div className="allocation-list allocation-bars" id="allocation-bars">
    {totals.buckets.map(x => {
      const gap = x.gap_minor ?? null;
      const aligned = x.share_pct != null && Math.abs(x.share_pct - x.target_pct) <= 5;
      const scale = Math.max(50, ...totals.buckets.map(b => Math.max(b.target_pct + 5, b.share_pct ?? 0)));
      return <div className="bucket-row" key={x.key}>
        <div className="bucket-label"><strong>{bucketLabel(x.key, t)}</strong><b>{x.share_pct == null || !visible ? "—" : `${x.share_pct.toFixed(1)}%`}</b><small>{t("target")} {x.target_pct}%</small></div>
        <div className="bucket-track" aria-label={`${bucketLabel(x.key, t)} ${visible ? x.share_pct?.toFixed(1) ?? "—" : "—"}%, ${t("target")} ${x.target_pct}%`}>
          <span className="target-zone" style={{ left: `${Math.max(0, x.target_pct - 5) / scale * 100}%`, width: `${(Math.min(scale, x.target_pct + 5) - Math.max(0, x.target_pct - 5)) / scale * 100}%` }} />
          <span className="bucket-fill" style={{ width: `${visible ? (x.share_pct ?? 0) / scale * 100 : 0}%`, background: x.color }} />
          <span className="target-tick" style={{ left: `${x.target_pct / scale * 100}%` }} />
        </div>
        <span className="bucket-gap">{gap == null ? "—" : aligned ? zh ? "无需调整" : "Within target" : `${zh ? gap > 0 ? "少约 " : "多约 " : gap > 0 ? "Below by " : "Above by "}${money(Math.abs(gap), locale, visible, currency, 0)}`}</span>
      </div>;
    })}
  </div>;
}

/** 中文的量词不分单复数，英文要分，所以单位走两个 key。 */
const countLabel = (n: number, t: (k: MessageKey) => string) =>
  `${n} ${t(n === 1 ? "countUnitOne" : "countUnit")}`;

/**
 * 零和未知都不是涨跌。把它们画成绿色上箭头会在空账本或刚建仓时
 * 谎报一笔并不存在的收益，所以单独给一个中性档。
 */
const deltaTone = (value: number | null | undefined) =>
  value == null || value === 0 ? "neutral" : value > 0 ? "positive" : "negative";

const holdingValue = (h: Holding) => h.market_value_minor;
function portfolio(ledger?: LedgerSummary) {
  return {
    value: ledger?.base.total_assets_minor,
    valid: ledger?.allocation.valid ?? false,
    buckets: buckets.map((bucket) => ({ ...bucket,
      value: 0, share_pct: null as number | null, target_pct: 0,
      ...ledger?.allocation.items.find((item) => item.key === bucket.key),
    })),
  };
}
function localMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}
function money(
  minor: number | null | undefined,
  locale: Locale,
  visible = true,
  currency = "CNY",
  /* 0 drops the cents — used inside the allocation donut, where the total is a
     glanceable figure rather than a number anyone reconciles against. */
  fractionDigits = 2,
) {
  if (!visible) return "••••••";
  if (minor == null || !Number.isFinite(minor)) return "—";
  const code = String(currency || "CNY").trim().toUpperCase();
  if (/^[A-Z]{3}$/.test(code)) {
    try {
      return new Intl.NumberFormat(locale, {
        style: "currency",
        currency: code,
        maximumFractionDigits: fractionDigits,
        // Each currency carries its own minimum (2 for CNY, 0 for JPY/KRW), and
        // leaving it alone is what keeps JP¥1,234 from becoming JP¥1,234.00.
        // Only asking for whole units needs an override, because CNY's default
        // minimum of 2 would out-range a maximum of 0 and throw.
        ...(fractionDigits === 0 ? { minimumFractionDigits: 0 } : {}),
      }).format(minor / 100);
    } catch {
      // Some runtimes reject otherwise valid-looking non-fiat codes.
    }
  }
  const amount = new Intl.NumberFormat(locale, {
    style: "decimal",
    maximumFractionDigits: fractionDigits,
  }).format(minor / 100);
  return `${amount} ${code}`;
}
function latestUpdate(items: Holding[], locale: Locale) {
  items = items.filter((item) => item.price_as_of);
  if (!items.length) return "—";
  return new Intl.DateTimeFormat(locale, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(
    new Date(
      [...items].sort((a, b) =>
        (b.price_as_of || "").localeCompare(a.price_as_of || ""),
      )[0].price_as_of!,
    ),
  );
}
function isIncome(type: Transaction["transaction_type"]) {
  return type === "deposit" || type === "dividend" || type === "interest";
}

function isExpense(type: Transaction["transaction_type"]) {
  return type === "withdrawal" || type === "fee";
}

function isCashIncrease(type: Transaction["transaction_type"]) {
  return isIncome(type) || type === "sell";
}

function isCashDecrease(type: Transaction["transaction_type"]) {
  return isExpense(type) || type === "buy" || type === "transfer";
}

function transactionDirection(type: Transaction["transaction_type"]) {
  return isCashIncrease(type)
    ? "income"
    : isCashDecrease(type)
      ? "expense"
      : "transfer";
}

function transactionTypeLabel(
  type: Transaction["transaction_type"],
  t: (k: MessageKey) => string,
) {
  const labels: Record<Transaction["transaction_type"], MessageKey> = {
    deposit: "transactionDeposit",
    withdrawal: "transactionWithdrawal",
    buy: "transactionBuy",
    sell: "transactionSell",
    dividend: "transactionDividend",
    fee: "transactionFee",
    interest: "transactionInterest",
    transfer: "transfer",
    other: "otherTransaction",
  };
  return t(labels[type]);
}

type FinancialAlert = {
  key: string;
  kind: "warning" | "danger";
  title: string;
  body: string;
};

function financialAlerts(
  _accounts: Account[],
  holdings: Holding[],
  _goals: Goals | undefined,
  ledger: LedgerSummary | undefined,
  t: (k: MessageKey) => string,
): FinancialAlert[] {
  if (!ledger) return [];
  const alerts: FinancialAlert[] = [];
  const missingCosts = holdings.filter((holding) => holding.average_cost == null).length;
  if (missingCosts) alerts.push({key: "missing-costs", kind: "warning", title: t("missingCosts"), body: t("missingCostsBody")});
  const missingPrices = holdings.filter((holding) => holding.valuation_status === "estimated").length;
  if (missingPrices) {
    alerts.push({
      key: "missing-prices",
      kind: "warning",
      title: t("alertMissingPrices"),
      body: `${missingPrices} ${t("alertMissingPricesBody")}`,
    });
  }
  const largestGap = ledger.allocation.largest_gap_pct;
  if (largestGap != null && largestGap >= 10) {
    alerts.push({key: "allocation-drift", kind: "warning",
      title: t("alertAllocationDrift"),
      body: `${t("alertAllocationDriftBody")} ${largestGap.toFixed(1)} pp`});
  }
  return alerts;
}
function errorText(error: unknown, t: (k: MessageKey) => string) {
  return error instanceof ApiError
    ? error.message
    : error instanceof Error
      ? error.message
      : t("errorGeneric");
}
async function done(
  qc: ReturnType<typeof useQueryClient>,
  key: string,
  t: (k: MessageKey) => string,
  close: () => void,
  notice: (n: Notice) => void,
  archived = false,
) {
  try {
    await Promise.all([
      qc.invalidateQueries({ queryKey: [key] }, { throwOnError: true }),
      qc.invalidateQueries({ queryKey: ["ledger"] }, { throwOnError: true }),
    ]);
    notice({ kind: "success", key: archived ? "archived" : "saved" });
  } catch {
    notice({ kind: "info", text: t("savedRefreshFailed") });
  }
  // The write succeeded even when the subsequent read failed. Never offer a second write.
  close();
}
function bucketLabel(bucket: string, t: (k: MessageKey) => string) {
  const key = bucket.toUpperCase();
  return key === "AGGRESSIVE"
    ? t("bucketAggressive")
    : key === "DEFENSIVE"
      ? t("bucketDefensive")
      : t("bucketBalanced");
}
function assetTypeLabel(type: string, t: (k: MessageKey) => string) {
  const key = type.toLowerCase();
  const labels: Record<string, MessageKey> = {
    stock: "assetStock",
    etf: "assetEtf",
    fund: "assetFund",
    cash: "assetCash",
    bond: "assetBond",
    crypto: "assetCrypto",
    other: "assetOther",
  };
  return t(labels[key] || "assetOther");
}
function accountTypeLabel(type: string, t: (k: MessageKey) => string) {
  const key = type.toLowerCase();
  const labels: Record<string, MessageKey> = {
    brokerage: "accountBrokerage",
    bank: "accountBank",
    fund: "accountFund",
    crypto: "accountCrypto",
    other: "accountOther",
  };
  return t(labels[key] || "accountBrokerage");
}
function formatMonth(month: string, locale: Locale) {
  const [year, value] = month.split("-").map(Number);
  return new Intl.DateTimeFormat(locale, {
    year: "numeric",
    month: "long",
  }).format(new Date(year, value - 1, 1));
}

const newDemoQueries = () =>
  new QueryClient({ defaultOptions: { queries: { retry: 0, staleTime: 30_000, refetchOnWindowFocus: false } } });

/**
 * The workspace for visitors without an account: the real ProtectedApp running on
 * an in-memory ledger. It has its own query cache and its own API object, so no
 * request can reach the server and nothing it holds survives a reload.
 */
function DemoWorkspace({
  notice,
  onRetry,
  onState,
  readOnlyAccount = false,
  ...localeTools
}: ReturnType<typeof useLocale> & { notice?: string; onRetry?: () => void; onState?: (state: DemoState) => void; readOnlyAccount?: boolean }) {
  const { locale, setLocale } = localeTools;
  const navigate = useNavigate();
  const stateRef = useRef<DemoState | null>(null);
  const baseline = useRef("");
  const fingerprint = (state: DemoState) => JSON.stringify([state.holdings, state.transactions, state.sources, state.baseCurrency, state.goals]);
  const [dirty, setDirty] = useState(false);
  const [run, setRun] = useState(0);
  const [demoQueries, setDemoQueries] = useState(newDemoQueries);
  const [demoApi, setDemoApi] = useState<Api | null>(null);
  const [prefs, setPrefs] = useState({ base_currency: "CNY", preferences_version: 0 });
  const fromPortfolio = useMemo(() => cameFromPortfolio(), []);
  const localeRef = useRef(locale);
  useEffect(() => {
    // The example's names come in both languages; refetch so a language switch shows them.
    localeRef.current = locale;
    demoQueries.invalidateQueries();
  }, [locale, demoQueries]);
  useEffect(() => {
    let cancelled = false;
    import("./demo/api").then(({ createDemoApi }) => {
      if (cancelled) return;
      setPrefs({ base_currency: "CNY", preferences_version: 0 });
      const demo = createDemoApi({ locale: () => localeRef.current });
      stateRef.current = demo.state;
      baseline.current = fingerprint(demo.state);
      onState?.(demo.state);
      setDirty(false);
      setDemoApi(demo.api);
    });
    return () => {
      cancelled = true;
    };
  }, [run, onState]);
  useEffect(() => {
    let active = true;
    const unsubscribe = demoQueries.getQueryCache().subscribe(() => {
      queueMicrotask(() => {
        const state = stateRef.current;
        if (active && state) setDirty(fingerprint(state) !== baseline.current);
      });
    });
    return () => { active = false; unsubscribe(); };
  }, [demoQueries]);
  const controls = useMemo<DemoControls>(() => ({
    fromPortfolio,
    // Reset = a freshly seeded ledger, a fresh query cache and a remounted workspace.
    reset: () => {
      setDemoApi(null);
      setDemoQueries(newDemoQueries());
      setRun((value) => value + 1);
    },
    exit: () => readOnlyAccount ? navigate("/") : navigate("/login", { state: { background: { pathname: window.location.pathname } } }),
    dirty,
    readOnlyAccount,
    notice,
    onRetry,
  }), [fromPortfolio, notice, onRetry, navigate, dirty, readOnlyAccount]);
  const user: User = {
    user_id: "demo-user",
    email: null,
    username: "demo",
    display_name: locale === "en-US" ? "Example household" : "示例家庭",
    language: locale,
    ...prefs,
  };
  if (!demoApi) {
    return <DataState icon={<LoaderCircle className="spin" />} title={localeTools.t("loading")} />;
  }
  return (
    <QueryClientProvider client={demoQueries}>
      <ApiContext.Provider value={demoApi}>
        <DemoContext.Provider value={controls}>
          <ProtectedApp
            key={run}
            {...localeTools}
            user={user}
            onLogout={controls.exit}
            onUserUpdate={(next) => {
              setPrefs({ base_currency: next.base_currency ?? "CNY", preferences_version: next.preferences_version ?? 0 });
              if (next.language) setLocale(next.language);
            }}
          />
        </DemoContext.Provider>
      </ApiContext.Provider>
    </QueryClientProvider>
  );
}

function Root() {
  const localeTools = useLocale();
  const { setLocale } = localeTools;
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(session.has());
  const [startupError, setStartupError] = useState<unknown>(null);
  const acceptUser = useCallback((nextUser: User | null) => {
    setUser(nextUser);
    if (nextUser?.language === "zh-CN" || nextUser?.language === "en-US") {
      setLocale(nextUser.language);
    }
  }, [setLocale]);
  const restoreSession = () => {
    setChecking(true);
    setStartupError(null);
    api
      .me()
      .then(acceptUser)
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401) session.clear();
        else setStartupError(error);
      })
      .finally(() => setChecking(false));
  };
  useEffect(() => {
    if (session.has())
      api
        .me()
        .then(acceptUser)
        .catch((error) => {
          if (error instanceof ApiError && error.status === 401)
            session.clear();
          else setStartupError(error);
        })
        .finally(() => setChecking(false));
  }, [acceptUser]);
  const location = useLocation();
  const navigate = useNavigate();
  const [pendingAuth, setPendingAuth] = useState<{ result: import("./api").AuthResult; register: boolean } | null>(null);
  const authPath = /^\/(login|register)\/?$/.test(location.pathname);
  const authBackground = location.state?.background;
  const draft = useRef<DemoState | null>(null);
  const receiveState = useCallback((state: DemoState) => { draft.current = state; }, []);
  const [transfer, setTransfer] = useState<GuestRow[]>([]);
  const carry = useRef<ReturnType<typeof settingsTransfer> | null>(null);
  const closeAuth = () => {
    if (authBackground) navigate(-1);
    else navigate("/", { replace: true });
  };
  const signedIn = (next: User) => {
    setPendingAuth(null); queryClient.clear(); acceptUser(next);
    const background = authBackground?.pathname || "/";
    navigate(background === "/demo" ? "/" : background, { replace: true });
  };
  const registered = async (next: User) => {
    if (!draft.current) return next;
    if (!carry.current) carry.current = settingsTransfer(draft.current, localeTools.locale);
    const updated = await carry.current(api, next);
    setTransfer(guestRows(draft.current, createClientRequestId));
    return updated;
  };
  const showDemo = /^\/demo(?:\/|$)/.test(location.pathname);
  const workspacePath = authPath ? authBackground?.pathname || "/" : location.pathname;
  const workspaceLocation = { ...location, pathname: workspacePath === "/en/" ? "/" : workspacePath.replace(/^\/demo(?=\/|$)/, "/app") };
  const workspace = checking ? <DataState icon={<LoaderCircle className="spin" />} title={localeTools.t("loading")} />
    : user?.requires_username ? <UsernameMigration {...localeTools} onSignedIn={signedIn} />
    : user && !showDemo ? <ProtectedApp {...localeTools} user={user} onUserUpdate={acceptUser} onLogout={() => { setUser(null); setTransfer([]); carry.current = null; queryClient.clear(); }} />
    : <DemoWorkspace {...localeTools} onState={receiveState} readOnlyAccount={Boolean(user)}
        notice={startupError ? errorText(startupError, localeTools.t) : undefined} onRetry={startupError ? restoreSession : undefined} />;
  return <ThemeProvider>
    <div inert={authPath || Boolean(pendingAuth) || transfer.length > 0}>
      <Routes location={workspaceLocation}>
        <Route path="/*" element={workspace} />
      </Routes>
    </div>
    {(authPath || pendingAuth) && !checking && !user && ((pendingAuth ? pendingAuth.register : location.pathname.startsWith("/register"))
      ? <RegisterPage {...localeTools} onSignedIn={signedIn} onRegistered={registered} initialResult={pendingAuth?.result} onPending={result => setPendingAuth({ result, register: true })} onClose={closeAuth} />
      : <PublicHome {...localeTools} onSignedIn={signedIn} initialResult={pendingAuth?.result} onPending={result => setPendingAuth({ result, register: false })} onClose={closeAuth} />)}
    {authPath && user && !user.requires_username && <Navigate to="/" replace />}
    {transfer.length > 0 && user && <GuestImportPanel rows={transfer} locale={localeTools.locale} onDone={() => { setTransfer([]); void queryClient.invalidateQueries({ queryKey: ["ledger"] }); }} />}
  </ThemeProvider>;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Root />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
