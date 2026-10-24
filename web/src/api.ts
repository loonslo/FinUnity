import { validateLedger } from "./ledgerValidation";
import { decimalString } from "./decimal";
import { ApiError } from "./apiError";
import type {
  Account,
  Holding,
  HoldingImport,
  LedgerAccountSummary,
  LedgerSummary,
  Locale,
  ParseResult,
  Source,
  Transaction,
  User,
} from "./types";

const inferredApiBase = import.meta.env.DEV
  ? "http://127.0.0.1:8000"
  : window.location.origin;
const API_BASE = (import.meta.env.VITE_API_BASE_URL || inferredApiBase).replace(
  /\/$/,
  "",
);
const TOKEN_KEY = "finunity-session-token";
const REFRESH_TOKEN_KEY = "finunity-refresh-token";

export function createClientRequestId(): string {
  const webCrypto =
    typeof globalThis !== "undefined" ? globalThis.crypto : undefined;
  if (webCrypto && typeof webCrypto.randomUUID === "function")
    return webCrypto.randomUUID();
  if (webCrypto && typeof webCrypto.getRandomValues === "function") {
    const bytes = new Uint8Array(16);
    webCrypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0"));
    return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10).join("")}`;
  }
  return `request-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

type Envelope<T> = {
  data?: T;
  error?: { code?: string; message?: string; fields?: Record<string, string> };
  requestId?: string;
  request_id?: string;
};

export { ApiError };

export const session = {
  get: () => window.sessionStorage.getItem(TOKEN_KEY),
  refresh: () => window.localStorage.getItem(REFRESH_TOKEN_KEY),
  has: () =>
    Boolean(
      window.sessionStorage.getItem(TOKEN_KEY) ||
        window.localStorage.getItem(REFRESH_TOKEN_KEY),
    ),
  set: (token: string, refreshToken?: string) => {
    window.sessionStorage.setItem(TOKEN_KEY, token);
    if (refreshToken)
      window.localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear: () => {
    window.sessionStorage.removeItem(TOKEN_KEY);
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

let refreshRequest: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = session.refresh();
  if (!refreshToken) return false;
  if (refreshRequest) return refreshRequest;
  refreshRequest = (async () => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/auth/session:refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ refresh_token: refreshToken }),
      });
      const payload = (await response
        .json()
        .catch(() => ({}))) as Envelope<AuthResult>;
      if (response.status === 400 || response.status === 401) {
        session.clear();
        return false;
      }
      if (!response.ok) {
        throw new ApiError(
          payload.error?.message || `Request failed (${response.status})`,
          response.status,
          payload.error?.code || "REFRESH_FAILED",
        );
      }
      if (!payload.data?.access_token || !payload.data.refresh_token) {
        session.clear();
        return false;
      }
      session.set(payload.data.access_token, payload.data.refresh_token);
      return true;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError("Unable to reach FinUnity Server");
    } finally {
      refreshRequest = null;
    }
  })();
  return refreshRequest;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  retried = false,
): Promise<T> {
  const token = session.get();
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  headers.set("Accept", "application/json");
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError("Unable to reach FinUnity Server");
  }
  if (
    response.status === 401 &&
    !retried &&
    path !== "/api/v1/auth/session:refresh"
  ) {
    if (await refreshAccessToken()) return request<T>(path, init, true);
  }
  const payload = (await response.json().catch(() => ({}))) as Envelope<T> &
    Record<string, unknown>;
  if (!response.ok || payload.error) {
    const detail =
      payload.error ??
      (payload.detail as { code?: string; message?: string } | undefined);
    throw new ApiError(
      detail?.message || `Request failed (${response.status})`,
      response.status,
      detail?.code || "REQUEST_FAILED",
      payload.error?.fields,
    );
  }
  return (payload.data ?? payload) as T;
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
const get = <T>(path: string) => request<T>(path);

type GoalsPayload = Partial<LedgerSummary["goals"]> & {
  risk_profile?: string;
  horizon?: string;
};

type LedgerPayload = Omit<Partial<LedgerSummary>, "records" | "accounts" | "goals"> & {
  records?: Partial<LedgerSummary["records"]>;
  accounts?: Array<Partial<LedgerAccountSummary> & Pick<LedgerAccountSummary, "account_id" | "currency">>;
  goals?: GoalsPayload;
};

type Collection<T> = { items?: T[] };

const legacyRiskBuckets = [
  { key: "AGGRESSIVE" as const, target: "aggressive_pct" as const },
  { key: "BALANCED" as const, target: "balanced_pct" as const },
  { key: "DEFENSIVE" as const, target: "defensive_pct" as const },
];

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function isLegacyLedgerPayload(value: unknown): value is LedgerPayload {
  return (
    isObject(value) &&
    !("records" in value) &&
    Array.isArray(value.accounts) &&
    isObject(value.base) &&
    typeof value.base_currency === "string"
  );
}

function normalizeCurrencySummary(
  value: Partial<LedgerSummary["base"]> | undefined,
): LedgerSummary["base"] {
  return {
    cash_balance_minor: value?.cash_balance_minor ?? 0,
    holdings_value_minor: value?.holdings_value_minor ?? 0,
    holdings_cost_minor: value?.holdings_cost_minor ?? 0,
    unrealized_profit_minor: value?.unrealized_profit_minor ?? 0,
    total_assets_minor: value?.total_assets_minor ?? 0,
    income_minor: value?.income_minor ?? 0,
    expense_minor: value?.expense_minor ?? 0,
    net_cashflow_minor: value?.net_cashflow_minor ?? 0,
  };
}

function normalizeAccountSummaries(
  summaries: LedgerPayload["accounts"],
  accounts: Account[],
): LedgerAccountSummary[] {
  const byId = new Map((summaries || []).map((item) => [item.account_id, item]));
  return accounts.map((account) => {
    const item = byId.get(account.account_id);
    return {
      account_id: account.account_id,
      currency: item?.currency || account.currency,
      cash_balance_minor: item?.cash_balance_minor ?? account.opening_balance_minor ?? 0,
      holdings_value_minor: item?.holdings_value_minor ?? 0,
      holdings_cost_minor: item?.holdings_cost_minor ?? 0,
      unrealized_profit_minor: item?.unrealized_profit_minor ?? 0,
      total_assets_minor: item?.total_assets_minor ?? account.opening_balance_minor ?? 0,
      missing_price_count: item?.missing_price_count ?? 0,
      missing_cost_count: item?.missing_cost_count ?? 0,
    };
  });
}

function fallbackGoals(goals: GoalsPayload | undefined): LedgerSummary["goals"] {
  return {
    aggressive_pct: goals?.aggressive_pct ?? 0,
    balanced_pct: goals?.balanced_pct ?? 0,
    defensive_pct: goals?.defensive_pct ?? 0,
    version: goals?.version ?? 0,
  };
}

function fallbackAllocation(
  goals: LedgerSummary["goals"],
): LedgerSummary["allocation"] {
  return {
    valid: false,
    items: legacyRiskBuckets.map(({ key, target }) => ({
      key,
      value: 0,
      share_pct: null,
      target_pct: goals[target],
      deviation_pct: null,
    })),
    drift_pct: null,
    score: null,
    largest_gap_pct: null,
  };
}

function fallbackCategories(
  transactions: Transaction[],
  month: string,
): LedgerSummary["categories"] {
  const totals = new Map<string, number>();
  for (const transaction of transactions) {
    if (!transaction.transaction_date.startsWith(month) || !transaction.category) continue;
    totals.set(
      transaction.category,
      (totals.get(transaction.category) || 0) + Math.abs(transaction.amount_minor),
    );
  }
  return [...totals.entries()].sort((left, right) => right[1] - left[1]);
}

async function loadLedgerSummary(month?: string): Promise<LedgerSummary> {
  const path = `/api/v1/ledger/summary${month ? `?month=${encodeURIComponent(month)}` : ""}`;
  const raw = await get<unknown>(path);
  try {
    validateLedger(raw);
    return raw;
  } catch (error) {
    if (!isLegacyLedgerPayload(raw)) {
      throw new ApiError((error as Error).message, 502, "INCOMPLETE_LEDGER");
    }
  }

  const summary = raw;
  const records = summary.records;
  const needsHoldings = !Array.isArray(records?.holdings);
  const needsTransactions = !Array.isArray(records?.transactions);
  const needsGoals = !summary.goals;
  const [holdingResponse, transactionResponse, goalsResponse, sourceResponse] =
    await Promise.all([
      needsHoldings ? get<Collection<Holding>>("/api/v1/holdings") : Promise.resolve(undefined),
      needsTransactions ? get<Collection<Transaction>>("/api/v1/transactions") : Promise.resolve(undefined),
      needsGoals ? get<GoalsPayload>("/api/v1/me/goals") : Promise.resolve(undefined),
      !Array.isArray(records?.sources) ? get<Collection<Source>>("/api/v1/sources") : Promise.resolve(undefined),
    ]);
  const accounts = records?.accounts || [];
  const holdings = records?.holdings || holdingResponse?.items || [];
  const transactions = records?.transactions || transactionResponse?.items || [];
  const sources = records?.sources || sourceResponse?.items || [];
  const normalizedGoals = fallbackGoals(summary.goals || goalsResponse);
  const resolvedMonth = summary.month || month || new Date().toISOString().slice(0, 7);
  const base = normalizeCurrencySummary(summary.base);
  const byCurrency = Object.fromEntries(
    Object.entries(summary.by_currency || {}).map(([currency, value]) => [
      currency,
      normalizeCurrencySummary(value),
    ]),
  );
  const normalized: LedgerSummary = {
    month: resolvedMonth,
    base_currency: summary.base_currency || "CNY",
    base,
    by_currency: byCurrency,
    accounts: normalizeAccountSummaries(summary.accounts, accounts),
    excluded_currencies: summary.excluded_currencies || [],
    generated_at: summary.generated_at || new Date().toISOString(),
    price_refresh: summary.price_refresh || { status: "unconfigured", updated_count: 0 },
    records: { accounts, holdings, transactions, sources },
    goals: normalizedGoals,
    allocation: summary.allocation || fallbackAllocation(normalizedGoals),
    categories: summary.categories || fallbackCategories(transactions, resolvedMonth),
  };
  try {
    validateLedger(normalized);
    return normalized;
  } catch (error) {
    throw new ApiError((error as Error).message, 502, "INCOMPLETE_LEDGER");
  }
}

type CodePurpose = "login" | "register" | "passwordReset";
const codePurposes: Record<
  CodePurpose,
  "LOGIN" | "REGISTER" | "PASSWORD_RESET"
> = {
  login: "LOGIN",
  register: "REGISTER",
  passwordReset: "PASSWORD_RESET",
};
export type AuthResult = {
  access_token?: string;
  refresh_token?: string;
  token?: string;
  user: User;
  recovery_code?: string;
};

async function authPost(
  path: string,
  body: Record<string, string | undefined>,
): Promise<User> {
  const result = await post<AuthResult>(
    path,
    Object.fromEntries(Object.entries(body).filter(([, v]) => v != null)),
  );
  session.set(result.access_token || result.token || "", result.refresh_token);
  return result.user;
}

export const api = {
  authConfig: () => get<{ email_auth_enabled: boolean; ocr_enabled: boolean }>("/api/v1/auth/config"),
  registerUsername: (username: string, password: string, password_confirmation: string,
                     display_name: string, consent: boolean) =>
    post<AuthResult>("/api/v1/auth/register", { username, password, password_confirmation,
      display_name: display_name || undefined, accept_privacy: consent, accept_terms: consent }),
  recoverPassword: (username: string, recovery_code: string, password: string,
                    password_confirmation: string) =>
    post<AuthResult>("/api/v1/auth/recovery/reset", { username, recovery_code, password,
      password_confirmation }),
  completeAuth: (result: AuthResult) => {
    if (!result.access_token || !result.refresh_token) throw new Error("会话响应不完整");
    session.set(result.access_token, result.refresh_token);
    return result.user;
  },
  changePassword: async (old_password: string, password: string, password_confirmation: string) => {
    const result = await post<AuthResult>("/api/v1/me/password/change", {
      old_password, password, password_confirmation });
    api.completeAuth(result);
    return result;
  },
  regenerateRecovery: async (password: string) => {
    const result = await post<AuthResult>("/api/v1/me/recovery/regenerate", { password });
    api.completeAuth(result);
    return result;
  },
  setUsername: (username: string, password: string) =>
    post<AuthResult>("/api/v1/me/username", { username, password }),
  deleteAccount: async (password: string) => {
    await post("/api/v1/me:delete", { password });
    session.clear();
  },
  exportData: () => get<Record<string, unknown>>("/api/v1/me/export"),
  sendCode: (email: string, purpose: CodePurpose, locale: Locale) =>
    post<{ expires_in?: number; dev_code?: string }>(
      "/api/v1/auth/email/code",
      { email, purpose: codePurposes[purpose], locale },
    ),
  login: (email: string, code: string, client_request_id: string) =>
    authPost("/api/v1/auth/login", { email, code, client_request_id }),
  register: (
    email: string,
    code: string,
    display_name: string,
    client_request_id: string,
    password?: string,
  ) =>
    authPost("/api/v1/auth/email/register", {
      email,
      code,
      display_name,
      client_request_id,
      password,
    }),
  loginWithPassword: (
    username: string,
    password: string,
    client_request_id: string,
  ) =>
    authPost("/api/v1/auth/password/login", {
      ...(username.includes("@") ? { email: username } : { username }),
      password,
      client_request_id,
    }),
  setPassword: (
    email: string,
    code: string,
    password: string,
    client_request_id: string,
  ) =>
    authPost("/api/v1/auth/password/set", {
      email,
      code,
      password,
      client_request_id,
    }),
  logout: async () => {
    try {
      await post("/api/v1/auth/logout");
    } finally {
      session.clear();
    }
  },
  me: () => get<User>("/api/v1/me"),
  saveProfile: (display_name: string) => post<User>("/api/v1/me/profile", { display_name }),
  savePreferences: (language: Locale, baseCurrency: string, version?: number) =>
    post<{ language: Locale; base_currency: string; version: number }>(
      "/api/v1/me/preferences",
      {
        language,
        base_currency: baseCurrency,
        version,
      },
    ),
  ledgerSummary: (month?: string) => loadLedgerSummary(month),
  refreshHoldings: () => post<LedgerSummary["price_refresh"]>("/api/v1/holdings:refresh"),
  saveGoals: (values: number[], version: number) =>
    post("/api/v1/me/goals", {
      risk_profile: "BALANCED",
      horizon: "LONG",
      aggressive_pct: values[0],
      balanced_pct: values[1],
      defensive_pct: values[2],
      version,
    }),
  saveAccount: (
    body: Partial<Account> & { name: string; client_request_id: string },
  ) =>
    post<Account>("/api/v1/accounts", {
      ...body,
      account_type: body.account_type?.toUpperCase(),
      opening_balance:
        body.opening_balance_minor == null
          ? undefined
          : String(body.opening_balance_minor / 100),
      opening_balance_minor: undefined,
      client_request_id: undefined,
    }),
  archiveAccount: (id: string, version: number) =>
    post<Account>(`/api/v1/accounts/${id}:archive`, { version }),
  listSources: () => get<Collection<Source>>("/api/v1/sources"),
  saveSource: (body: Partial<Source> & { name: string }) =>
    post<Source>("/api/v1/sources", body),
  createHoldingImport: (body: { client_request_id: string; document_ids?: string[]; rows?: Array<Record<string, unknown>> }) =>
    post<HoldingImport>("/api/v1/holding-imports", body),
  getHoldingImport: (id: string) => get<HoldingImport>(`/api/v1/holding-imports/${id}`),
  previewHoldingImport: (id: string, body: { version: number; rows: Array<Record<string, unknown>> }) =>
    post<HoldingImport>(`/api/v1/holding-imports/${id}:preview`, body),
  commitHoldingImport: (id: string, body: { client_request_id: string; version: number; rows: Array<Record<string, unknown>> }) =>
    post<{ batch_id: string; status: string; version: number; items: Holding[] }>(`/api/v1/holding-imports/${id}:commit`, body),
  saveHoldings: (
    items: Array<Partial<Holding> & { name: string }>,
    clientRequestId = createClientRequestId(),
  ) =>
    post<{ items: Holding[] }>("/api/v1/holdings:batch-upsert", {
      client_request_id: clientRequestId,
      items: items.map((x) => ({
        holding_id: x.holding_id,
        account_id: x.account_id,
        source_id: x.source_id,
        snapshot_date: x.snapshot_date,
        name: x.name,
        instrument_id: x.instrument_id,
        currency: x.currency,
        version: x.version,
        asset_type: x.asset_type?.toUpperCase(),
        quantity: decimalString(x.quantity!),
        average_cost: x.average_cost == null ? null : decimalString(x.average_cost),
        current_price: undefined,
        risk_bucket: undefined,
        metadata: {
          current_price: x.current_price ?? null,
          risk_bucket: x.risk_bucket,
          quantity_unit: x.quantity_unit,
          quantity_kind: x.quantity_kind,
        },
      })),
    }),
  archiveHolding: (id: string, version: number) =>
    post<Holding>(`/api/v1/holdings/${id}:archive`, { version }),
  saveTransactions: (
    items: Array<Partial<Transaction> & { title: string }>,
    clientRequestId = createClientRequestId(),
  ) =>
    post<{ items: Transaction[] }>("/api/v1/transactions:batch-upsert", {
      client_request_id: clientRequestId,
      items: items.map((x) => ({
        transaction_id: x.transaction_id,
        account_id: x.account_id,
        to_account_id:
          x.transaction_type === "transfer" ? x.to_account_id : undefined,
        transaction_type: x.transaction_type?.toUpperCase() || "OTHER",
        amount: String((x.amount_minor || 0) / 100),
        currency: x.currency || "CNY",
        occurred_at: x.transaction_date,
        note: JSON.stringify({
          title: x.title,
          category: x.category,
          note: x.note,
        }),
        version: x.version,
      })),
    }),
  archiveTransaction: (id: string, version: number) =>
    post<Transaction>(`/api/v1/transactions/${id}:archive`, { version }),
  parseImage: async (file: File, locale: Locale, defaultCurrency?: string) => {
    const body = new FormData();
    body.append("image", file);
    body.append("client_request_id", createClientRequestId());
    body.append("locale", locale);
    if (defaultCurrency) body.append("default_currency", defaultCurrency);
    const parsed = await request<{
      document_id: string;
      as_of_date?: string;
      broker?: { name?: string };
      rows: Array<{
        name: string;
        asset_type: string;
        quantity?: string;
        current_price?: string;
        market_value?: string;
        total_cost?: string;
        currency?: string;
        quantity_unit?: string;
        quantity_kind?: "UNITS" | "VALUE";
        confidence?: number;
        review_reasons?: string[];
        security_code?: {
          instrument_id?: string;
          normalized?: string;
          needs_confirmation?: boolean;
        };
      }>;
      warnings?: string[];
    }>("/api/v1/ocr/holdings:parse", { method: "POST", body });
    return {
      document_id: parsed.document_id,
      warnings: parsed.warnings,
      holdings: parsed.rows.map((row) => {
        const quantity = Number(row.quantity || 0);
        const totalCost =
          row.total_cost == null ? undefined : Number(row.total_cost);
        const marketValue =
          row.market_value == null ? undefined : Number(row.market_value);
        return {
          document_id: parsed.document_id,
          name: row.name,
          instrument_id:
            row.security_code?.instrument_id || row.security_code?.normalized,
          quantity,
          quantity_unit: row.quantity_unit,
          quantity_kind: row.quantity_kind,
          average_cost:
            totalCost != null && quantity ? totalCost / quantity : undefined,
          current_price:
            row.current_price != null
              ? Number(row.current_price)
              : marketValue != null && quantity
                ? marketValue / quantity
                : undefined,
          currency: row.currency || defaultCurrency,
          source_name: parsed.broker?.name || undefined,
          snapshot_date: parsed.as_of_date || undefined,
          asset_type: row.asset_type?.toLowerCase() || "other",
          confidence: row.confidence,
          review_reasons: row.review_reasons,
          needs_confirmation: row.security_code?.needs_confirmation,
        };
      }),
    } satisfies ParseResult;
  },
};

export type Api = typeof api;
