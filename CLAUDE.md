# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FinUnity is an Android portfolio tracker app for managing multi-currency investments. It aggregates accounts (broker, bank, fund, insurance), tracks stock/fund/ETF positions, syncs daily prices from Yahoo Finance, and provides rebalancing alerts with four-quadrant asset allocation. UI is in Chinese.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run tests with verbose output
./gradlew test --info

# Run a specific test class
./gradlew test --tests "com.finunity.data.model.PortfolioCalculationTest"

# Tests are pure JUnit 4 (no Android dependencies needed)
# PortfolioCalculationTest — average cost, profit/loss, multi-currency math
# CurrencyTest — exchange rate conversions
# TransactionTest — transaction recording and reconciliation
# MainViewModelTest — ViewModel logic (uses Mockito)
# RebalanceTest — rebalancing threshold logic
# AssetRecordTest — AssetRecord CRUD and calculations
# TransactionAuditTest — transaction audit/reconciliation
# RiskBucketDetailConsistencyTest — risk bucket calculation consistency
```

## Architecture

**Pattern**: MVVM + Repository. All data reactive via Room Flows + `combine()`.

**Data Flow**:
1. UI (Compose Screens) → ViewModel (MainViewModel) → Repository (PriceRepository) → DAO/API
2. PriceSyncWorker (WorkManager, every 24h) → Repository → DAO
3. SnapshotWorker (WorkManager, daily at 9 AM) → HistoryRepository → DAO

**Navigation**: Manual stack-based navigation in MainActivity (sealed class `Screen` with 20+ variants). Three bottom tabs: 总览 (Overview), 资产 (Assets), 账户 (Accounts).

## Dual Data Model (Migration in progress)

The app is migrating from `Position` → `AssetRecord`. Both coexist:

- **`Position`** (legacy): `id, accountId, symbol, shares, totalCost, currency`. Always STOCK type, always AGGRESSIVE risk bucket. Created by older code paths.
- **`AssetRecord`** (new): Supports `AssetType` (STOCK/ETF/FUND/CASH/TIME_DEPOSIT/REAL_ESTATE/VEHICLE/INSURANCE_POLICY) and `RiskBucket` (CONSERVATIVE/AGGRESSIVE/INSURANCE/CASH). Has explicit `name, quantity, cost, currentPrice, currency`.

The `PortfolioCalculator` handles both. `UnifiedAsset` interface bridges them. Cash is managed as AssetRecord(CASH, CASH bucket). The `adjustCashAsset()` method in MainViewModel auto-creates/updates/deletes CASH records when buying/selling.

## Risk Bucket System (四象限)

Four risk dimensions for asset allocation:
- **AGGRESSIVE** (进取/生钱的钱): Stocks, ETFs, equity funds
- **CONSERVATIVE** (稳健/保本的钱): Bonds, time deposits, money market funds, real estate, vehicles
- **INSURANCE** (保命/保命的钱): Insurance policies, emergency funds
- **CASH** (防守/要花的钱): Cash, demand deposits, Yu'ebao-type products

UI shows a donut chart with green (AGGRESSIVE), blue (CONSERVATIVE), purple (INSURANCE), gold (CASH). Target allocation string format: `"CONSERVATIVE:0.4,AGGRESSIVE:0.3,INSURANCE:0.2,CASH:0.1"`.

## Database Schema (Room, version 9)

- `accounts` — id, name, type (BROKER/BANK/FUND/CASH_MANAGEMENT/BOND/INSURANCE/LIABILITY/OTHER), currency, balance. **Non-LIABILITY accounts don't use balance for asset totals** — cash is tracked via AssetRecord(CASH).
- `positions` (legacy) — id, accountId, symbol, shares, totalCost, currency
- `asset_records` (new) — id, accountId, assetType, riskBucket, name, quantity, cost, currentPrice, currency, createdAt, updatedAt
- `prices` — symbol (PK), price, currency, updatedAt, isFallback. 12h staleness threshold.
- `price_history` — id, recordId, price, cost, timestamp. Per-record price/cost tracking.
- `transactions` — id, accountId, symbol, type (BUY/SELL/DIVIDEND/FEE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW), shares, price, amount, currency, timestamp, note, recordId, balanceAfter
- `settings` — id=1 singleton, baseCurrency (default CNY), targetAllocation, rebalanceThreshold (default 0.05)
- `asset_snapshots` — id, timestamp, totalAssets, cashAssets, stockAssets, stockRatio, baseCurrency, totalCost, notes

**Migrations**: v3→v4 (no-op), v4→v5 (add asset_records), v5→v6 (add price_history), v6→v7 (add recordId to transactions), v7→v8 (add onboarded to settings), v8→v9 (add amountsVisible to settings). Destructive fallback allowed from v1, v2 only.

## Key Design Decisions

- **Average Cost Method**: `totalCost` is proportionally reduced when selling (shares and cost both decrease, unit cost unchanged)
- **Multi-Currency**: All values converted to `baseCurrency` (CNY default) via Yahoo Finance exchange rates. Exchange rate symbol format: `"USDCNY=X"`.
- **Explicit Currency**: Each position/record has explicit `currency`; do not infer from symbol
- **Liability Handling**: LIABILITY accounts reduce total assets (balance is subtracted). All other account balances are ignored — assets tracked via AssetRecord.
- **Cash Auto-Management**: `adjustCashAsset()` creates/updates/deletes CASH AssetRecords automatically when buying/selling non-cash assets
- **Price Cache**: Price entity has 12h staleness, 30-sec connect/read timeouts, circuit breaker (5 failures → 5-min open)
- **Batch Refresh**: PriceSyncWorker refreshes in batches of 5, exponential backoff (1min/5min/15min), max 3 attempts
- **Offline Support**: Prices cached in Room; stale cache returned as `isFallback=true` when network unavailable
- **Rebalancing**: Configurable target allocation per risk bucket; drift > threshold (default 5%) triggers recommendations
- **CSV Import**: Supports importing accounts, positions, transactions from CSV files in assets/ directory
- **Currency Formatting**: `formatCurrency()` in MainScreen.kt (single source) — `¥` for CNY, `$` for USD, `HK$` for HKD

## Tech Stack

- Kotlin 1.9.20, compileSdk 34, minSdk 26, jvmTarget 17
- Jetpack Compose with Material 3 (BOM 2023.10.01)
- Room 2.6.1 with KSP, WorkManager 2.9.0
- Retrofit 2.9.0 + OkHttp 4.12.0 + Gson
- Navigation Compose 2.7.5, Lifecycle ViewModel Compose 2.6.2
- Testing: JUnit 4.13.2, Mockito 5.8.0 + mockito-kotlin 5.2.1

## UI Component Kit

Design system defined in `ui/theme/Theme.kt` (green primary `#166B45`, gray-based text hierarchy). Custom components in `ui/components/FinUi.kt`: FinCard (no-elevation card), FinTextField (rounded), FinPill (toggle pill), FinSoftButton (green button), profitColor/profitText helpers.

## Workers

- **PriceSyncWorker**: PeriodicWorkRequest every 24h (requires network). Gets all Position symbols + AssetRecord tickers, batch-refreshes prices and exchange rates, saves PriceHistory. Called from `PriceSyncWorker.schedule()` in MainActivity.onCreate().
- **SnapshotWorker**: PeriodicWorkRequest daily at 9 AM (no network required). Computes total assets/cost, saves AssetSnapshot, cleans up snapshots >2 years old.
