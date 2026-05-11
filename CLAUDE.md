# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FinUnity is an Android portfolio tracker app for managing multi-currency investments. It aggregates accounts (broker, bank, fund), tracks stock positions, fetches real-time prices from Yahoo Finance, and provides rebalancing alerts.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run unit tests with verbose output
./gradlew test --info

# Run a specific test class
./gradlew test --tests "com.finunity.data.model.PortfolioCalculationTest"
```

## Architecture

**Pattern**: MVVM + Repository

**Data Flow**:
1. UI (Compose Screens) → ViewModel → Repository → DAO/API
2. PriceSyncWorker (WorkManager) → Repository → DAO

**Key Components**:
- `data/local/` - Room database with DAOs for Account, Position, Price, Settings, Transaction entities
- `data/remote/YahooFinanceApi.kt` - Retrofit interface for Yahoo Finance chart API
- `data/repository/PriceRepository.kt` - Coordinates price fetching and caching
- `data/repository/CsvImportRepository.kt` - CSV import for accounts, positions, transactions
- `viewmodel/MainViewModel.kt` - Central ViewModel handling portfolio calculations
- `worker/PriceSyncWorker.kt` - WorkManager periodic task (every 5 min)

**Database Schema**:
- `accounts` - id, name, type (BROKER/BANK/FUND/CASH_MANAGEMENT/BOND/INSURANCE/LIABILITY/OTHER), currency, balance
- `positions` - id, accountId, symbol, shares, totalCost, currency (explicit, not inferred)
- `prices` - symbol, price, currency, timestamp
- `settings` - baseCurrency, targetAllocation, rebalanceThreshold
- `transactions` - id, accountId, symbol, type (BUY/SELL/DIVIDEND/FEE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW), shares, price, amount, currency, timestamp

## Important Design Decisions

- **Average Cost Method**: `totalCost` is proportionally reduced when selling (shares and cost both decrease)
- **Multi-Currency**: All values converted to `baseCurrency` (CNY default) using Yahoo Finance exchange rates
- **Position Aggregation**: Positions with same symbol across accounts are merged into `PositionSummary`
- **Explicit Currency**: Each position has an explicit `currency` field; do not infer from symbol
- **Liability Handling**: LIABILITY accounts reduce total assets (balance is subtracted)
- **Offline Support**: Prices cached in Room; WorkManager refreshes every 5 minutes when network available
- **Rebalancing**: Supports configurable target allocations per asset class; recommendations generated when drift exceeds threshold
- **CSV Import**: Supports importing accounts, positions, and transactions from CSV files in assets/

## Tech Stack

- Kotlin 1.9.20, compileSdk 34, minSdk 26
- Jetpack Compose with Material 3
- Room 2.6.1 with KSP
- WorkManager 2.9.0
- Retrofit 2.9.0 + OkHttp 4.12.0
- Navigation Compose 2.7.5
