package com.finunity.data.model

import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetType

object AccountAssetRules {
    fun allowedAssetTypes(accountType: AccountType?): List<AssetType> = when (accountType) {
        AccountType.BROKER -> listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND, AssetType.CASH)
        AccountType.BANK -> listOf(AssetType.FUND, AssetType.TIME_DEPOSIT, AssetType.CASH)
        AccountType.FUND -> listOf(AssetType.FUND, AssetType.CASH)
        AccountType.CASH_MANAGEMENT -> listOf(AssetType.FUND, AssetType.CASH)
        AccountType.BOND -> listOf(AssetType.FUND, AssetType.TIME_DEPOSIT, AssetType.CASH)
        AccountType.INSURANCE -> listOf(AssetType.FUND, AssetType.TIME_DEPOSIT)
        AccountType.LIABILITY -> emptyList()
        AccountType.OTHER, null -> AssetType.entries.toList()
    }

    fun allowedAssetText(accountType: AccountType): String = when (accountType) {
        AccountType.BROKER -> "股票、ETF、基金、现金"
        AccountType.BANK -> "基金、定期存款、现金"
        AccountType.FUND -> "基金、现金"
        AccountType.CASH_MANAGEMENT -> "基金、现金"
        AccountType.BOND -> "基金、定期存款、现金"
        AccountType.INSURANCE -> "基金、定期存款"
        AccountType.LIABILITY -> "不添加资产，仅记录负债金额"
        AccountType.OTHER -> "股票、ETF、基金、现金、定期存款"
    }

    fun ruleNote(accountType: AccountType): String = when (accountType) {
        AccountType.BROKER -> "适合证券 App、券商账户和股票基金持仓。"
        AccountType.BANK -> "适合银行卡、存款、银行理财和现金余额。"
        AccountType.FUND -> "适合基金销售平台或单独基金账户。"
        AccountType.CASH_MANAGEMENT -> "适合余额宝、零钱通、货币基金等随用资金。"
        AccountType.BOND -> "适合固收、债券或定存类资金入口。"
        AccountType.INSURANCE -> "适合记录保单现金价值、年金或偏长期稳健资金。"
        AccountType.LIABILITY -> "负债账户不会进入添加资产流程。"
        AccountType.OTHER -> "用于暂时无法归类的资产，后续可再调整。"
    }
}
