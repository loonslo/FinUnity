import { ApiError } from "../apiError.ts";
import type { Api } from "../api.ts";
import type {
  Holding,
  HoldingImport,
  LedgerSummary,
  Locale,
  RiskBucket,
  Source,
  Transaction,
  User,
} from "../types.ts";
import {
  BUCKETS,
  buildLedgerSummary,
  publicHolding,
  type DemoHolding,
  type DemoTransaction,
} from "./ledger.ts";
import { DEMO_SAMPLE_FILE_NAME } from "./sample.ts";
import { createSeedState, type DemoState } from "./seed.ts";


export type DemoOptions = {
  now?: () => Date;
  locale?: () => Locale;
  /** How long the fake recognition takes. Tests pass 0. */
  parseDelayMs?: number;
};

const ASSET_TYPES = new Set(["stock", "etf", "fund", "lof", "reit", "bond", "cash", "crypto", "other"]);
const TRANSACTION_TYPES = new Set(["buy", "sell", "dividend", "deposit", "withdrawal", "fee", "interest", "transfer", "other"]);
const CURRENCY = /^[A-Z]{3,5}$/;
const IMPORT_ACTIONS = new Set(["ADD", "UPDATE", "SKIP"]);

type Importable = Record<string, unknown>;

export function createDemoApi(options: DemoOptions = {}): { api: Api; state: DemoState } {
  const clock = options.now ?? (() => new Date());
  const getLocale = options.locale ?? (() => "zh-CN" as Locale);
  const parseDelay = options.parseDelayMs ?? 900;
  const state = createSeedState(clock());

  const lang = () => (getLocale() === "en-US" ? "en" : "zh");
  const text = (zh: string, en: string) => (getLocale() === "en-US" ? en : zh);
  const stamp = () => clock().toISOString();
  const nextId = (prefix: string) => `${prefix}${(state.sequence += 1)}`;
  const monthOf = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
  const invalid = (zh: string, en: string, code = "VALIDATION_ERROR") => new ApiError(text(zh, en), 422, code);
  const conflict = () => new ApiError(text("数据已在其他设备更新，请刷新后重试", "Data changed elsewhere; refresh and retry"), 409, "VERSION_CONFLICT");
  const notFound = () => new ApiError(text("记录不存在或无权访问", "Record not found"), 404, "NOT_FOUND");
  const demoOnly = () =>
    new ApiError(
      text("示例模式不连接账号服务。注册后才能使用这个功能。", "The demo is not connected to the account service. Register to use this."),
      400,
      "DEMO_MODE",
    );
  const unavailable = () => Promise.reject(demoOnly());

  // ---- views -------------------------------------------------------------
  const holdingView = (row: DemoHolding): Holding =>
    publicHolding({ ...row, name: row.names ? row.names[lang()] : row.name });
  const sourceView = (row: DemoState["sources"][number]): Source => ({
    source_id: row.source_id,
    name: row.names ? row.names[lang()] : row.name,
    version: row.version,
    updated_at: row.updated_at,
  });
  const transactionView = (row: DemoTransaction): Transaction => {
    const { titles, categories, ...rest } = row;
    return { ...rest, title: titles ? titles[lang()] : row.title, category: categories ? categories[lang()] : row.category };
  };
  const user = (): User => ({
    user_id: "demo-user",
    email: null,
    username: "demo",
    requires_username: false,
    display_name: text("示例家庭", "Example household"),
    language: getLocale(),
    base_currency: state.baseCurrency,
    preferences_version: state.preferencesVersion,
  });

  // ---- holdings ----------------------------------------------------------
  type HoldingInput = Partial<Holding> & { name: string };
  function writeHolding(rows: DemoHolding[], item: HoldingInput): DemoHolding {
    const name = String(item.name ?? "").trim();
    if (!name || name.length > 200) throw invalid("持仓名称必填，且不超过 200 个字符", "A holding name is required (max 200 characters)");
    const assetType = String(item.asset_type ?? "other").toLowerCase();
    if (!ASSET_TYPES.has(assetType)) throw invalid("资产类型无效", "Invalid asset type");
    const quantity = Number(item.quantity);
    if (!Number.isFinite(quantity) || quantity < 0) throw invalid("数量必须是不小于 0 的数字", "Quantity must be a number ≥ 0");
    const currency = String(item.currency ?? "").trim().toUpperCase();
    if (!CURRENCY.test(currency)) throw invalid("币种必须是 3 到 5 位大写字母", "Currency must be 3–5 letters");
    const cost = item.average_cost == null ? null : Number(item.average_cost);
    const price = item.current_price == null ? null : Number(item.current_price);
    for (const value of [cost, price]) {
      if (value !== null && (!Number.isFinite(value) || value < 0)) throw invalid("成本和价格必须是不小于 0 的数字", "Cost and price must be numbers ≥ 0");
    }
    const bucket = (item.risk_bucket ?? "BALANCED") as RiskBucket;
    if (!BUCKETS.includes(bucket)) throw invalid("风险桶无效", "Invalid risk bucket");
    const instrument = item.instrument_id?.trim() || undefined;
    const now = stamp();
    const common = {
      source_id: item.source_id || undefined,
      snapshot_date: item.snapshot_date || undefined,
      instrument_id: instrument,
      asset_type: assetType,
      quantity,
      average_cost: cost,
      currency,
      current_price: price,
      risk_bucket: bucket,
      quantity_kind: item.quantity_kind ?? ("UNITS" as const),
      quantity_unit: item.quantity_unit || undefined,
      updated_at: now,
    };
    if (item.holding_id) {
      const existing = rows.find((row) => row.holding_id === item.holding_id);
      if (!existing) throw notFound();
      if (item.version !== undefined && item.version !== existing.version) throw conflict();
      // Editing a built-in row without touching its name keeps both translations.
      const unchanged = existing.names && (name === existing.names.zh || name === existing.names.en);
      // As on the server, a changed price (or instrument/currency) becomes a manual price stamped now.
      const samePrice =
        existing.instrument_id === instrument && existing.currency === currency && (existing.current_price ?? null) === price;
      const updated: DemoHolding = {
        ...existing,
        ...common,
        name: unchanged ? existing.name : name,
        names: unchanged ? existing.names : undefined,
        market_source: samePrice ? existing.market_source : "manual",
        price_as_of: samePrice ? existing.price_as_of : price !== null ? now : undefined,
        version: existing.version + 1,
      };
      rows[rows.indexOf(existing)] = updated;
      return updated;
    }
    const created: DemoHolding = {
      ...common,
      holding_id: nextId("demo-h"),
      name,
      market_source: "manual",
      price_as_of: price !== null ? now : undefined,
      version: 1,
    };
    rows.push(created);
    return created;
  }

  // ---- import batches ----------------------------------------------------
  const batchView = (batch: HoldingImport): HoldingImport => structuredClone(batch);
  const findBatch = (id: string) => {
    const batch = state.imports.get(id);
    if (!batch) throw notFound();
    return batch;
  };

  function planFor(rows: Importable[]) {
    return rows.map((row, index) => {
      const instrument = row.instrument_id as string | undefined;
      const currency = String(row.currency ?? "").toUpperCase();
      const candidates = state.holdings
        .filter((holding) => instrument && holding.instrument_id === instrument && holding.currency === currency)
        .map((holding) => holding.holding_id);
      return {
        row_id: (row.row_id as string) || `row-${index + 1}`,
        action: String(row.action ?? "ADD").toUpperCase(),
        candidate_holding_ids: candidates,
        needs_confirmation: candidates.length > 0,
        already_applied: false,
      };
    });
  }

  function importedHolding(row: Importable): HoldingInput {
    const metadata = (row.metadata ?? {}) as Record<string, unknown>;
    return {
      holding_id: row.action === "UPDATE" ? (row.holding_id as string) : undefined,
      version: row.action === "UPDATE" ? (row.version as number | undefined) : undefined,
      name: String(row.name ?? ""),
      instrument_id: row.instrument_id as string | undefined,
      asset_type: String(row.asset_type ?? "OTHER").toLowerCase(),
      quantity: Number(row.quantity),
      average_cost: row.average_cost == null ? undefined : Number(row.average_cost),
      current_price: row.current_price == null ? undefined : Number(row.current_price),
      currency: String(row.currency ?? ""),
      source_id: row.source_id as string | undefined,
      snapshot_date: row.snapshot_date as string | undefined,
      risk_bucket: (row.risk_bucket ?? metadata.risk_bucket ?? "BALANCED") as RiskBucket,
      quantity_kind: ((row.quantity_kind ?? metadata.quantity_kind) as "UNITS" | "VALUE" | undefined) ?? "UNITS",
      quantity_unit: (row.quantity_unit ?? metadata.quantity_unit) as string | undefined,
    };
  }

  // ---- fake recognition --------------------------------------------------
  type ParsedRow = Awaited<ReturnType<Api["parseImage"]>>["holdings"][number];
  function sampleRows(locale: Locale, documentId: string, day: string): ParsedRow[] {
    const en = locale === "en-US";
    const row = (
      fields: Pick<ParsedRow, "name" | "instrument_id" | "quantity" | "average_cost" | "current_price" | "asset_type" | "confidence"> &
        Partial<ParsedRow>,
    ): ParsedRow => ({
      document_id: documentId,
      quantity_unit: undefined,
      quantity_kind: undefined,
      currency: "CNY",
      source_name: en ? "Example broker" : "示例券商",
      snapshot_date: day,
      review_reasons: undefined,
      needs_confirmation: undefined,
      ...fields,
    });
    return [
      row({ name: en ? "Example broad-index fund" : "示例宽基指数基金", instrument_id: "DEMO-IDX", quantity: 32000, average_cost: 1.06, current_price: 1.12, asset_type: "etf", confidence: 0.98 }),
      row({ name: en ? "Example consumer-theme fund" : "示例消费主题基金", instrument_id: "DEMO-CONS", quantity: 10000, average_cost: 1, current_price: 1.03, asset_type: "fund", confidence: 0.93 }),
      row({
        name: en ? "Cash" : "现金",
        instrument_id: "CNY",
        quantity: 5000,
        average_cost: 1,
        current_price: 1,
        asset_type: "cash",
        confidence: 0.62,
        needs_confirmation: true,
        review_reasons: [en ? "The name is very short; confirm this row is a cash balance." : "名称很短，请确认这一行是现金余额。"],
      }),
    ];
  }

  const demoApi = {
    authConfig: async () => ({ email_auth_enabled: false, ocr_enabled: false }),
    registerUsername: unavailable,
    saveProfile: unavailable,
    recoverPassword: unavailable,
    completeAuth: (): never => {
      throw demoOnly();
    },
    changePassword: unavailable,
    regenerateRecovery: unavailable,
    setUsername: unavailable,
    deleteAccount: unavailable,
    exportData: unavailable,
    sendCode: unavailable,
    login: unavailable,
    register: unavailable,
    loginWithPassword: unavailable,
    setPassword: unavailable,
    logout: async () => undefined,
    me: async () => user(),
    savePreferences: async (language: Locale, baseCurrency: string, version?: number) => {
      if (!/^[A-Z]{3}$/.test(baseCurrency)) throw invalid("主币种必须是 3 位大写字母", "Base currency must be 3 letters");
      if (version !== undefined && version !== state.preferencesVersion) throw conflict();
      state.baseCurrency = baseCurrency;
      state.preferencesVersion += 1;
      return { language, base_currency: baseCurrency, version: state.preferencesVersion };
    },
    ledgerSummary: async (month?: string): Promise<LedgerSummary> => {
      const now = clock();
      const holdings = state.holdings.map(holdingView);
      const transactions = [...state.transactions]
        .sort((a, b) => b.transaction_date.localeCompare(a.transaction_date) || b.transaction_id.localeCompare(a.transaction_id))
        .map(transactionView);
      return structuredClone(
        buildLedgerSummary(
          { baseCurrency: state.baseCurrency, goals: state.goals, holdings, transactions, sources: state.sources.map(sourceView) },
          month ?? monthOf(now),
          now,
        ),
      );
    },
    refreshHoldings: async () => ({ status: "unconfigured" as const, updated_count: 0 }),
    saveGoals: async (values: number[], version: number) => {
      if (values.length !== 3 || values.some((value) => !Number.isInteger(value) || value < 0 || value > 100)) {
        throw invalid("三桶目标比例必须是 0 到 100 的整数", "Each target must be a whole number from 0 to 100");
      }
      if (values[0] + values[1] + values[2] !== 100) throw invalid("三桶目标比例合计必须为 100", "The three targets must add up to 100");
      if (version !== state.goals.version) throw conflict();
      state.goals = { aggressive_pct: values[0], balanced_pct: values[1], defensive_pct: values[2], version: state.goals.version + 1 };
      return { ...state.goals };
    },
    saveAccount: unavailable,
    archiveAccount: unavailable,
    listSources: async () => ({ items: state.sources.map(sourceView) }),
    saveSource: async (body: Partial<Source> & { name: string }) => {
      const name = body.name.trim();
      if (!name) throw invalid("来源名称不能为空", "A source name is required", "SOURCE_NAME_REQUIRED");
      if (!body.source_id) {
        const existing = state.sources.find((source) => sourceView(source).name === name || source.name === name);
        if (existing) return sourceView(existing);
        const created = { source_id: nextId("demo-s"), name, version: 1, updated_at: stamp() };
        state.sources.push(created);
        return sourceView(created);
      }
      const existing = state.sources.find((source) => source.source_id === body.source_id);
      if (!existing) throw notFound();
      if (body.version !== undefined && body.version !== existing.version) throw conflict();
      const updated = { source_id: existing.source_id, name, version: existing.version + 1, updated_at: stamp() };
      state.sources[state.sources.indexOf(existing)] = updated;
      return sourceView(updated);
    },
    createHoldingImport: async (body: { client_request_id: string; document_ids?: string[]; rows?: Array<Record<string, unknown>> }) => {
      const now = stamp();
      const batch: HoldingImport = {
        batch_id: nextId("demo-imp"),
        status: "DRAFT",
        documents: body.document_ids ?? [],
        rows: body.rows ?? [],
        plan: [],
        version: 1,
        created_at: now,
        updated_at: now,
      };
      state.imports.set(batch.batch_id, batch);
      return batchView(batch);
    },
    getHoldingImport: async (id: string) => batchView(findBatch(id)),
    previewHoldingImport: async (id: string, body: { version: number; rows: Array<Record<string, unknown>> }) => {
      const batch = findBatch(id);
      if (batch.version !== body.version) throw conflict();
      batch.rows = body.rows;
      batch.plan = planFor(body.rows);
      batch.status = "READY";
      batch.version += 1;
      batch.updated_at = stamp();
      return batchView(batch);
    },
    commitHoldingImport: async (id: string, body: { client_request_id: string; version: number; rows: Array<Record<string, unknown>> }) => {
      const earlier = state.committed.get(body.client_request_id);
      if (earlier) return { ...earlier, items: earlier.items.map(holdingView) };
      const batch = findBatch(id);
      if (batch.version !== body.version) throw conflict();
      const working = [...state.holdings];
      const written: DemoHolding[] = [];
      for (const row of body.rows) {
        const action = String(row.action ?? "ADD").toUpperCase();
        if (!IMPORT_ACTIONS.has(action)) throw invalid("导入行决定无效", "Invalid import decision", "IMPORT_DECISION_INVALID");
        if (action === "SKIP") continue;
        if (action === "UPDATE" && !row.holding_id) throw invalid("更新持仓必须指定目标记录", "Choose the holding to update", "IMPORT_DECISION_REQUIRED");
        try {
          written.push(writeHolding(working, importedHolding({ ...row, action })));
        } catch (error) {
          if (error instanceof ApiError && error.code === "VALIDATION_ERROR") {
            throw invalid("导入行信息不完整或格式无效", "An import row is incomplete or malformed", "IMPORT_ROW_INVALID");
          }
          throw error;
        }
      }
      state.holdings = working;
      if (!batch.documents.some(doc => state.sampleDocumentIds.has(doc)) &&
          !body.rows.some(row => state.sampleDocumentIds.has(String(row.document_id)))) {
        for (const row of written) {
          if (!body.rows.some(input => input.holding_id === row.holding_id)) state.visitorHoldingIds.add(row.holding_id);
        }
      }
      batch.status = "COMPLETED";
      batch.version += 1;
      batch.updated_at = stamp();
      const result = { batch_id: batch.batch_id, status: batch.status, version: batch.version, items: written };
      state.committed.set(body.client_request_id, result);
      return { ...result, items: written.map(holdingView) };
    },
    saveHoldings: async (items: Array<Partial<Holding> & { name: string }>) => {
      const working = [...state.holdings];
      const written = items.map((item) => writeHolding(working, item));
      state.holdings = working;
      written.forEach((row, index) => { if (!items[index].holding_id) state.visitorHoldingIds.add(row.holding_id); });
      return { items: written.map(holdingView) };
    },
    archiveHolding: async (id: string, version: number) => {
      const existing = state.holdings.find((row) => row.holding_id === id);
      if (!existing) throw notFound();
      if (version !== existing.version) throw conflict();
      state.holdings = state.holdings.filter((row) => row !== existing);
      return holdingView(existing);
    },
    saveTransactions: async (items: Array<Partial<Transaction> & { title: string }>) => {
      const working = [...state.transactions];
      const written = items.map((item) => {
        const title = String(item.title ?? "").trim();
        const type = String(item.transaction_type ?? "other").toLowerCase() as Transaction["transaction_type"];
        const date = String(item.transaction_date ?? "");
        const currency = String(item.currency ?? "CNY").toUpperCase();
        const amount = Math.round(Number(item.amount_minor ?? 0));
        if (!title) throw invalid("流水标题必填", "A title is required");
        if (!TRANSACTION_TYPES.has(type)) throw invalid("流水类型无效", "Invalid transaction type");
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw invalid("日期格式应为 YYYY-MM-DD", "Use the date format YYYY-MM-DD");
        if (!CURRENCY.test(currency) || !Number.isFinite(amount)) throw invalid("币种或金额无效", "Invalid currency or amount");
        const common = {
          source_id: item.source_id || undefined,
          transaction_date: date,
          transaction_type: type,
          category: item.category || undefined,
          title,
          amount_minor: amount,
          currency,
          note: item.note || undefined,
          updated_at: stamp(),
        };
        if (item.transaction_id) {
          const existing = working.find((row) => row.transaction_id === item.transaction_id);
          if (!existing) throw notFound();
          if (item.version !== undefined && item.version !== existing.version) throw conflict();
          const updated: DemoTransaction = { ...existing, ...common, titles: undefined, categories: undefined, version: existing.version + 1 };
          working[working.indexOf(existing)] = updated;
          return updated;
        }
        const created: DemoTransaction = { ...common, transaction_id: nextId("demo-t"), version: 1 };
        working.push(created);
        return created;
      });
      state.transactions = working;
      written.forEach((row, index) => { if (!items[index].transaction_id) state.visitorTransactionIds.add(row.transaction_id); });
      return { items: written.map(transactionView) };
    },
    archiveTransaction: async (id: string, version: number) => {
      const existing = state.transactions.find((row) => row.transaction_id === id);
      if (!existing) throw notFound();
      if (version !== existing.version) throw conflict();
      state.transactions = state.transactions.filter((row) => row !== existing);
      return transactionView(existing);
    },
    parseImage: async (file: File, locale: Locale) => {
      if (file.name !== DEMO_SAMPLE_FILE_NAME) {
        throw new ApiError(
          text("示例模式只能演示内置的示例截图，你自己的截图请注册后导入。", "The demo only walks through the built-in sample screenshot. Register to import your own."),
          400,
          "DEMO_MODE",
        );
      }
      if (parseDelay > 0) await new Promise((resolve) => setTimeout(resolve, parseDelay));
      const documentId = nextId("demo-doc");
      state.sampleDocumentIds.add(documentId);
      const day = clock().toISOString().slice(0, 10);
      return { document_id: documentId, warnings: [], holdings: sampleRows(locale, documentId, day) };
    },
  } satisfies Api;

  return { api: demoApi, state };
}
