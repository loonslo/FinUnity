# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

FinUnity is an Android portfolio tracker app for managing multi-currency investments. It aggregates accounts (broker, bank, fund, insurance), tracks stock/fund/ETF positions, syncs market data through the dedicated FinUnity service, and provides rebalancing alerts with three-bucket asset allocation. UI is in Chinese.

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

- **`Position`** (legacy): `id, accountId, symbol, shares, totalCost, currency`. Always STOCK type, always AGGRESSIVE risk bucket. Retained only for backup and historical migration compatibility.
- **`AssetRecord`** (current): Supports `AssetType` (STOCK/ETF/FUND/CASH/TIME_DEPOSIT/REAL_ESTATE/VEHICLE/INSURANCE_POLICY) and the formal three-bucket `RiskBucket` (DEFENSIVE/BALANCED/AGGRESSIVE). Has explicit `name, quantity, cost, currentPrice, currency`.

The `PortfolioCalculator` handles both. `UnifiedAsset` interface bridges them. Cash is managed as AssetRecord(CASH, CASH bucket). The `adjustCashAsset()` method in MainViewModel auto-creates/updates/deletes CASH records when buying/selling.

## Risk Bucket System（三桶）

Three risk dimensions for asset allocation:
- **AGGRESSIVE** (进取/生钱的钱): Stocks, ETFs, equity funds
- **BALANCED** (稳健/保障与保值的钱): Bonds, time deposits, insurance policies, real estate, vehicles, funds without a more specific subtype
- **DEFENSIVE** (防守/要花的钱): Cash, demand deposits, Yu'ebao-type products

UI distinguishes the three buckets by color — orange (AGGRESSIVE), gold (BALANCED), blue (DEFENSIVE); see `FinColors` in `ui/theme/Theme.kt` — via a stacked allocation bar (`StackedAllocationBar` in `TargetAllocationScreen.kt`), not a donut chart. The default target allocation is `"DEFENSIVE:0.1,BALANCED:0.6,AGGRESSIVE:0.3"`.

## Database Schema (Room, version 25)

- `accounts` — id, name, type (BROKER/BANK/FUND/CASH_MANAGEMENT/BOND/INSURANCE/LIABILITY/OTHER), currency, balance, createdAt, sourceType, externalSourceId, lastSyncedAt, syncState (source/sync provenance), initialPrincipal, annualInterestRate, dueDayOfMonth, minimumPayment (liability metadata, LIABILITY accounts only). **Non-LIABILITY accounts don't use balance for asset totals** — cash is tracked via AssetRecord(CASH).
- `positions` (legacy) — id, accountId, symbol, shares, totalCost, currency
- `asset_records` (new) — id, accountId, assetType, riskBucket, name, securityCode, instrumentId, quantity, cost, currentPrice, currency, subCategory, industryTag, purchaseRestricted, peRatio, dividendYield, premiumRate, locked, createdAt, updatedAt, sourceType, sourceAccountId, sourceRecordId, importBatchId, sourceFingerprint, syncedAt
- `allocation_targets` — subCategory (PK), riskBucket, targetAmount, capAmount, stopNote, updatedAt. Sub-bucket (落点) level target/cap/stop, joined to asset_records by subCategory to produce the 落点表 (LandingPoint).
- `prices` — symbol (PK), price, previousClose, currency, updatedAt, isFallback, instrumentId, source, sourceTime, receivedAt, quality, valueType, errorCode. 12h staleness threshold.
- `price_history` — id, recordId, price, cost, timestamp. Per-record price/cost tracking.
- `transactions` — id, accountId, symbol, type (BUY/SELL/DIVIDEND/FEE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW/LIABILITY_PAYMENT), shares, price, amount, currency, timestamp, note, recordId, balanceAfter, origin, sourceFingerprint, category, importBatchId
- `settings` — id=1 singleton, baseCurrency (default CNY), targetAllocation, rebalanceThreshold (default 0.05), onboarded, amountsVisible, maxAggressiveRatio (default 0.70)
- `asset_snapshots` — id, timestamp, totalAssets, cashAssets, stockAssets, stockRatio, baseCurrency, totalCost, notes
- `recurring_rules` — id, accountId, type (INCOME/EXPENSE), amount, currency, category, note, dayOfMonth, enabled, lastGeneratedAt, createdAt. Local-only recurring income/expense rules; never syncs to a bank or broker.

**Migrations**: v3→v4 (no-op), v4→v5 (add asset_records), v5→v6 (add price_history), v6→v7 (add recordId to transactions), v7→v8 (add onboarded to settings), v8→v9 (add amountsVisible to settings), v9→v10 (add subCategory+locked to asset_records, add allocation_targets table), v10→v11 (add maxAggressiveRatio to settings), v11→v12 (add industryTag to asset_records), v12→v13 (add purchaseRestricted to asset_records), v13→v14 (add peRatio/dividendYield/premiumRate to asset_records), v14→v15 (add previousClose to prices), v15→v16 (add securityCode to asset_records), v16→v17 (add source/sync metadata to accounts/asset_records/transactions; migrate positions into asset_records and clear positions), v17→v18 (add sourceFingerprint to transactions), v18→v19 (add category to transactions), v19→v20 (add liability metadata to accounts), v20→v21 (add recurring_rules table), v21→v22 (add importBatchId to transactions), v22→v23 (canonicalize legacy buckets and targets), v23→v24 (extend snapshots with three-bucket totals), v24→v25 (add FinUnity service identity, provenance, and data-quality fields). Destructive fallback allowed from v1, v2 only.

## Landing Points (落点表 · 子桶配置)

The 落点 system sits *below* the three risk buckets, implementing the 加仓落点表 (accumulation landing-point table) from the product's 资产配置SOP plan (`对应方案第三章` in source comments). Assets carry a `subCategory` tag; `AllocationTarget` rows hold per-落点 `targetAmount`/`capAmount`/`stopNote`. `PortfolioCalculator.computeLandingPoints()` joins them into `LandingPoint` rows (`currentValue` vs `targetAmount`, plus computed `gap`/`progress`/`overCap`/`reachedTarget`, and `hasTarget` marking held-but-untargeted 落点). `computeLockedValue()` sums `locked` records; `PortfolioSummary.strategyAssets = grossAssets − lockedAssets` (floored at 0) is the investable pool. UI: `LandingPointScreen`, reachable from both PlanningScreen and SettingsScreen; edit form on AssetRecordScreen (subCategory + locked). Backup `BackupData` (current version 9) includes `allocationTargets`.

**Risk check (风险体检 · 永不满仓)**: `evaluateRiskAlerts()` (pure fn in PortfolioSummary.kt) produces `RiskAlert` rows — WARNING when AGGRESSIVE ratio exceeds `settings.maxAggressiveRatio`, WARNING per over-cap landing point, INFO per reached-target landing point that isn't also over-cap. Surfaced in a 风险体检 card atop PlanningScreen; the ratio cap is edited in TargetAllocationScreen.

## Key Design Decisions

- **Average Cost Method**: `totalCost` is proportionally reduced when selling (shares and cost both decrease, unit cost unchanged)
- **Multi-Currency**: All values are converted to `baseCurrency` (CNY default) using rates returned by the FinUnity service. The local cache retains compatibility keys such as `"USDCNY=X"`.
- **Explicit Currency**: Each position/record has explicit `currency`; do not infer from symbol
- **Liability Handling**: LIABILITY accounts reduce total assets (balance is subtracted). All other account balances are ignored — assets tracked via AssetRecord.
- **Cash Auto-Management**: `adjustCashAsset()` creates/updates/deletes CASH AssetRecords automatically when buying/selling non-cash assets
- **Price Cache**: Price entity has 12h staleness, 30-sec connect/read timeouts, circuit breaker (5 failures → 5-min open)
- **Batch Refresh**: PriceSyncWorker refreshes in batches of 5, exponential backoff (1min/5min/15min), max 3 attempts
- **Offline Support**: Prices cached in Room; stale cache returned as `isFallback=true` when network unavailable
- **Rebalancing**: Configurable three-bucket target allocation; drift > threshold (default 5%) triggers recommendations
- **CSV Import**: Supports importing accounts, positions, transactions from CSV files in assets/ directory
- **Currency Formatting**: `formatCurrency()` in `ui/screens/Formatters.kt` (single source) — `¥` for CNY, `$` for USD, `HK$` for HKD

## Tech Stack

- Kotlin 1.9.20, compileSdk 36, targetSdk 36, minSdk 26, jvmTarget 17
- Jetpack Compose with Material 3 (BOM 2023.10.01)
- Room 2.6.1 with KSP, WorkManager 2.9.0
- Retrofit 2.9.0 + OkHttp 4.12.0 + Gson
- Navigation Compose 2.7.5, Lifecycle ViewModel Compose 2.6.2
- Testing: JUnit 4.13.2, Mockito 5.8.0 + mockito-kotlin 5.2.1

## UI Component Kit

Design system defined in `ui/theme/Theme.kt` (green primary `#166B45`, gray-based text hierarchy). Custom components in `ui/components/FinUi.kt`: FinCard (no-elevation card), FinTextField (rounded), FinPill (toggle pill), FinSoftButton (green button), profitColor/profitText helpers.

## Workers

- **PriceSyncWorker**: PeriodicWorkRequest every 24h (requires network). Gets stock/ETF AssetRecord tickers, batch-refreshes prices and exchange rates, saves PriceHistory for successful real prices, and retains old cache on failure. Called from `PriceSyncWorker.schedule()` in MainActivity.onCreate().
- **SnapshotWorker**: PeriodicWorkRequest daily at 9 AM (no network required). Computes total assets/cost, saves AssetSnapshot, cleans up snapshots >2 years old.

## Imported Claude Cowork project instructions
