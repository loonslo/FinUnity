# FinUnity - 个人资产账本与家庭资产决策辅助

一个安静、轻量的 Android 应用，用于追踪多币种投资账户和标普四象限资产配置。

## 功能

- **资产总览**：首页环形图展示四象限资产结构（稳健/进取/保命/防守）
- **规划决策**：目标配置、再平衡提醒、月度复盘、大额支出模拟
- **多账户管理**：支持券商、银行、基金、保险等 8 种账户类型
- **多资产类型**：股票、ETF、基金、现金、定期、房产、车辆、保单
- **自动同步**：从 Yahoo Finance 每日更新股票/ETF/基金价格
- **多币种支持**：CNY、USD、HKD 自动汇率换算
- **数据备份**：JSON 导出/恢复，本地数据完全由你掌控
- **月度复盘提醒**：每月通知提醒查看资产变化

## 标普四象限

| 象限 | 风险偏好 | 典型资产 |
|------|---------|---------|
| 进取（生钱的钱） | 高风险 | 股票、ETF、股票型基金 |
| 稳健（保本的钱） | 低波动 | 定期、债券、货币基金、房产、车辆 |
| 保命（保命的钱） | 专款专用 | 保险、年金、应急保障 |
| 防守（要花的钱） | 随时可用 | 活期、余额宝、现金 |

## 技术栈

- **语言**: Kotlin 1.9.20
- **UI**: Jetpack Compose + Material 3
- **数据库**: Room 2.6.1
- **后台任务**: WorkManager 2.9.0
- **网络**: Retrofit 2.9.0 + OkHttp 4.12.0
- **架构**: MVVM + Repository

## 项目结构

```
app/src/main/java/com/finunity/
├── data/
│   ├── local/          # Room 数据库 (8 张表，版本 9)
│   ├── remote/         # Yahoo Finance API
│   ├── repository/     # 数据中间层（含备份/恢复）
│   └── model/          # 数据模型与计算器
├── ui/
│   ├── screens/        # 20+ 页面
│   ├── components/     # FinUi 组件库
│   └── theme/          # FinUnity 设计系统
├── viewmodel/          # MainViewModel
└── worker/             # PriceSync / Snapshot / ReviewReminder
```

## 构建

### Windows
```bash
# 确保 JAVA_HOME 指向 JDK 17
./gradlew assembleDebug      # 编译 Debug APK
./gradlew testDebugUnitTest  # 运行单元测试
```

### macOS / Linux
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 数据说明

- 所有数据存储在本地 Room 数据库，不上传任何服务器
- 股票/ETF/基金价格每日自动同步一次（Yahoo Finance）
- 支持离线查看（使用缓存价格，12 小时过期回退）
- 导出为 JSON 文件，可跨设备恢复
- 非交易型资产（房产、车辆、保单）以手动估值记录

## 免责声明

本应用仅供个人资产管理使用，不构成投资建议。股票价格来自 Yahoo Finance，仅供参考。
