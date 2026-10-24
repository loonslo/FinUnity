import type { DemoState } from "./demo/seed.ts";
import type { Holding, Transaction, User, Locale } from "./types.ts";

export type GuestRow = {
  id: string;
  requestId: string;
  label: string;
  kind: "holding" | "transaction";
  holding?: Partial<Holding> & { name: string };
  transaction?: Partial<Transaction> & { title: string };
};

/** Only explicitly visitor-created rows qualify. Seed edits and the sample image never do. */
export function guestRows(state: DemoState, requestId: () => string): GuestRow[] {
  return [
    ...state.holdings.filter(h => state.visitorHoldingIds.has(h.holding_id)).map(h => ({
      id: h.holding_id, requestId: requestId(), label: h.name, kind: "holding" as const,
      holding: { name: h.name, instrument_id: h.instrument_id, asset_type: h.asset_type,
        quantity: h.quantity, average_cost: h.average_cost ?? undefined,
        current_price: h.current_price ?? undefined, currency: h.currency, risk_bucket: h.risk_bucket,
        quantity_kind: h.quantity_kind, quantity_unit: h.quantity_unit, snapshot_date: h.snapshot_date },
    })),
    ...state.transactions.filter(t => state.visitorTransactionIds.has(t.transaction_id)).map(t => ({
      id: t.transaction_id, requestId: requestId(), label: t.title, kind: "transaction" as const,
      transaction: { title: t.title, transaction_type: t.transaction_type, transaction_date: t.transaction_date,
        amount_minor: t.amount_minor, currency: t.currency, category: t.category, note: t.note },
    })),
  ];
}

export type SettingsApi = {
  savePreferences: (language: Locale, currency: string, version?: number) => Promise<{ language: Locale; base_currency: string; version: number }>;
  ledgerSummary: () => Promise<{ goals: { version: number; aggressive_pct: number; balanced_pct: number; defensive_pct: number } }>;
  saveGoals: (values: number[], version: number) => Promise<unknown>;
};

/** Progress lives in memory; a retry after a partial failure never replays an acknowledged step. */
export function settingsTransfer(state: DemoState, locale: Locale) {
  const currency = state.baseCurrency;
  const goals = state.goals.version > 1 ? [state.goals.aggressive_pct, state.goals.balanced_pct, state.goals.defensive_pct] : null;
  let preferences: Awaited<ReturnType<SettingsApi["savePreferences"]>> | undefined;
  let goalsSaved = false;
  return async (api: SettingsApi, user: User): Promise<User> => {
    if (!preferences) {
      // Preferences are an absolute assignment; a lost response can safely be retried.
      preferences = await api.savePreferences(locale, currency);
    }
    if (goals && !goalsSaved) {
      const { goals: current } = await api.ledgerSummary();
      if (current.aggressive_pct !== goals[0] || current.balanced_pct !== goals[1] || current.defensive_pct !== goals[2]) {
        await api.saveGoals(goals, current.version);
      }
      goalsSaved = true;
    }
    return { ...user, language: preferences.language, base_currency: preferences.base_currency, preferences_version: preferences.version };
  };
}
