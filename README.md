# FinUnity - 个人资产Portfolio Tracker

一个简洁的 Android 应用，用于追踪你的所有投资账户和资产配置。

## 功能

- ✅ **资产总览**：一眼看到总资产、股票/现金比例
- ✅ **多账户管理**：支持券商、银行、基金等不同类型账户
- ✅ **持仓追踪**：自动汇总同一股票的多处持仓
- ✅ **实时价格**：从 Yahoo Finance 自动获取股票价格
- ✅ **多币种支持**：CNY、USD、HKD 自动换算
- ✅ **再平衡提醒**：股票占比超过阈值时提醒

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **数据库**: Room
- **后台任务**: WorkManager
- **网络**: Retrofit + OkHttp
- **架构**: MVVM + Repository

## 项目结构

```
app/src/main/java/com/finunity/
├── data/
│   ├── local/          # Room 数据库
│   ├── remote/         # Yahoo Finance API
│   ├── repository/     # 数据中间层
│   └── model/          # 数据模型
├── ui/
│   ├── screens/        # 页面
│   ├── components/      # UI 组件
│   └── theme/          # 主题
├── viewmodel/          # ViewModel
└── worker/             # WorkManager
```

## 构建

1. 用 Android Studio 打开 `FinUnity` 目录
2. 等待 Gradle 同步完成
3. 连接手机或启动模拟器
4. 点击 Run ▶️

## 数据说明

- 所有数据存储在本地（Room 数据库）
- 股票价格每5分钟自动刷新（由 WorkManager 执行）
- 支持离线查看（使用缓存价格）

## 免责声明

本应用仅供个人资产管理使用，不构成投资建议。股票价格来自 Yahoo Finance，仅供参考。
