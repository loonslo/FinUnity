package com.finunity.data.local.entity

/** 持仓当前值的来源。来源用于幂等更新与审计，不参与资产估值。 */
enum class HoldingSourceType {
    MANUAL,
    OCR,
    CSV,
    BROKER_SYNC,
    TRADE,
    LEGACY_MIGRATION,
    CASH_FLOW
}

/** 账户数据源类型；BROKER 仅在真实授权同步实现后才可标记为已同步。 */
enum class AccountSourceType {
    MANUAL,
    OCR,
    CSV,
    BROKER
}

enum class SyncState {
    NOT_APPLICABLE,
    PENDING_AUTHORIZATION,
    SYNCED,
    STALE,
    FAILED
}

/** 流水的业务来源，避免把导入快照误展示为用户主动买卖。 */
enum class TransactionOrigin {
    SNAPSHOT_IMPORT,
    CSV_IMPORT,
    TRADE,
    CASH_FLOW,
    LEGACY_MIGRATION,
    RECURRING
}
