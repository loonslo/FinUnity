> 2026-10-07 当前部署：baikai.site / halfopen.dev 相同内容分别部署并开放搜索。迁站策略已撤销；详见 [双域名部署与验收](docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md)。旧记录保留原验收日期。

# 衡仓 - 个人资产账本与家庭资产决策辅助

一个安静、轻量的 Android 应用，用于追踪多币种账户、资产和三桶规划。

## 功能

- **资产总览**：首页环形图展示三桶资产结构（防守/稳健/进攻）
- **规划决策**：目标配置、专款隔离、落点跟踪、再平衡、风险红线、回撤阶梯和压力测试
- **多账户管理**：支持券商、银行、基金、保险等 8 种账户类型
- **多资产类型**：股票、ETF、基金、现金、定期、房产、车辆、保单
- **自动同步**：通过 FinUnity 专属服务统一更新股票/ETF 价格、公募基金净值和汇率
- **多币种支持**：CNY、USD、HKD 自动汇率换算
- **数据备份**：JSON 导出/恢复，包含账户、资产、流水、快照、价格历史和落点目标
- **月度复盘提醒**：每月通知提醒查看资产变化

## 三桶规划

| 三桶 | 风险偏好 | 典型资产 |
|------|---------|---------|
| 进攻 | 高风险 | 股票、ETF、权益基金 |
| 稳健 | 低波动 | 定期、债券、货币基金、房产、车辆、保单 |
| 防守 | 随时可用 | 活期、现金和近期备用金 |

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
│   ├── local/          # Room 数据库（版本 26，显式迁移）
│   ├── remote/         # FinUnity 专属服务 API
│   ├── repository/     # 数据中间层（含备份/恢复）
│   └── model/          # 数据模型与计算器
├── ui/
│   ├── screens/        # 20+ 页面
│   ├── components/     # FinUi 组件库
│   └── theme/          # 衡仓设计系统
├── viewmodel/          # MainViewModel
└── worker/             # PriceSync / Snapshot / ReviewReminder
```

## 构建

默认服务地址为 `https://finunity-api.halfopen.dev/api/v1/`。可通过 Gradle Property 或环境变量覆盖，且必须是 HTTPS：

```powershell
$env:FINUNITY_API_BASE_URL = "https://finunity-api.halfopen.dev/api/v1/"
.\gradlew.bat assembleDebug
```

若显式将地址覆盖为空，App 仍可离线打开已有本地数据，但远端同步和截图解析会明确报出 `FINUNITY_API_BASE_URL 未配置`，不会返回模拟数据。默认地址的公网可达性仍需发布前验收。

### Windows
```bash
# 先确认 JAVA_HOME 指向 JDK 17
echo %JAVA_HOME%

# PowerShell / Git Bash 可使用
./gradlew assembleDebug
./gradlew testDebugUnitTest

# cmd.exe 也可明确使用 Windows wrapper
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

若提示 `JAVA_HOME is not set`，请将其设为 JDK 17 安装目录（不是 `bin` 子目录），重新打开终端后再构建。

### macOS / Linux
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 数据说明

- 账本数据默认存储在本地 Room；主动确认的截图会上传服务端并转交 DashScope 识别，详见隐私政策
- 股票/ETF 价格、基金净值和汇率通过 FinUnity 专属服务每日同步
- 支持离线查看（使用缓存价格；过期时明确显示延迟/过期状态）
- 导出为 JSON 文件，可跨设备恢复
- 非交易型资产（房产、车辆、保单）以手动估值记录

## 隐私与数据

- 账户、持仓、交易、目标配置和复盘数据默认保存在本机。
- 行情刷新只向 FinUnity 专属服务发送证券代码、资产类型和货币对，不发送持仓数量、成本或账户余额。
- 持仓截图只有在用户明确确认后才会发送到 FinUnity 专属服务解析；应用已关闭 Android 自动云备份。
- “备份恢复”导出的 JSON 可能包含完整财务信息，是明文文件，请妥善保管。
- 应用内可从“设置 → 隐私与数据说明”查看隐私说明；公开地址为 `https://finunity.halfopen.dev/android-privacy`，联系邮箱为 `chongqing115@126.com`。发布前仍需实际部署该网页、核对端点和费用预警。

## 免责声明

本应用仅供个人资产管理使用，不构成投资建议。专属服务返回的行情、净值和汇率可能延迟、缺失或修正，仅供参考。

## 2026-10-03 halfopen.dev 域名迁移


Android 默认 API 与隐私说明源码已更新为新域名；generateDebugBuildConfig 成功，生成 HTTPS API 地址正确，新旧 API readiness 已验收 200。已安装客户端继续使用旧 API 兼容入口；本次未签名/打包/分发、未做真机与完整业务网络验收。 详见 [迁移记录](docs/DOMAIN_MIGRATION_2026-10-03.md)。
