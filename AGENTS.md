# FinUnity 项目规则

FinUnity 是中文 Android 多币种资产管理应用。实现采用 Compose、ViewModel、Repository、Room 和 WorkManager；准确的依赖版本、SDK 配置与签名条件以 Gradle 文件为准。

## 构建与验证

在 Windows 使用 `gradlew.bat`，在 macOS/Linux 使用 `./gradlew`：

```text
gradlew.bat assembleDebug
gradlew.bat test
gradlew.bat :app:testDebugUnitTest --tests "com.finunity.data.model.PortfolioCalculationTest"
```

单元测试位于 `app/src/test/`；涉及 Room 迁移或 Android 组件时，还要查看 `app/src/androidTest/`，在有设备或模拟器的环境运行相应测试。发布构建需满足 `app/build.gradle` 中的本机签名配置；不得提交签名文件或密码。

## 数据与状态边界

- UI 经 ViewModel、Repository 访问 DAO 或 FinUnity 服务；价格同步和快照由独立 Worker 执行。更改调用链前检查对应 Repository、Worker 和测试。
- `Position` 是历史兼容模型，`AssetRecord` 是当前资产模型；迁移或备份改动必须覆盖两者的读取兼容性。Room 当前 schema、迁移和导出分别以 `AppDatabase.kt`、`data/local/migration/`、`app/schemas/` 为准。
- 资产总额由资产记录计算；非负债账户的 `balance` 不再叠加到资产总额，`LIABILITY` 账户余额按负债处理。现金作为 `AssetRecord(CASH)` 管理。
- 每条资产记录保留显式币种，不从证券代码推断；跨币种计算先换算到基础币种。卖出按比例减少持有数量和总成本，保持单位成本不变。
- 三桶分配为 `DEFENSIVE`、`BALANCED`、`AGGRESSIVE`。落点表按 `subCategory` 将资产与目标连接；锁定资产不计入可投资资产池。修改分桶、落点或再平衡计算时，核对 `PortfolioCalculator`、`PortfolioSummary` 与相关界面的一致性。
- 价格缓存、同步失败回退和历史快照属于持久状态。失败时保留已有有效数据，并在 UI 中呈现数据质量；不要把回退价格当作新行情写入历史。

## 变更约定

- 修改数据库字段时补迁移与相应验证，不能以破坏性重建掩盖现有数据迁移问题。
- 修改资产、交易、币种或风险分桶计算时，运行对应单元测试，并检查备份恢复及显示口径。
- 界面沿用 `ui/theme/Theme.kt` 和 `ui/components/FinUi.kt` 的组件与配色；货币格式统一使用 `ui/screens/Formatters.kt`。

## 项目进度摘要

完成有实质进展的任务后，先更新项目原有任务与验证记录，再核对根目录 `PROJECT_STATUS.md` 的概览、进度、下一步、证据和日期。文件变化本身不代表任务完成。

## PROJECT_STATUS 同步契约（v1）

有实质进展时先更新项目内任务和验收记录，再更新根目录 `PROJECT_STATUS.md`；交付前校验以下格式。只维护本项目，不自动写入日常 vault，不把文件存在、格式通过或 Git 改动当作完成。

- 文件以 YAML frontmatter 开始，字段名不得重复；必填 `project_id`、`updated`、`status`、`overview`、`progress`、`next`、`evidence`。
- `project_id` 与登记的工作区相对路径一致，路径中的 `/` 替换为 `-`；本项目为 `FinUnityWorkspace-FinUnity`。
- `updated` 使用未加引号的真实 `YYYY-MM-DD` 日期，表示摘要维护日期；纯格式修订也可更新，但须在正文记录修订日期、原进展日期及未重新验收的边界。
- `status` 只允许 `active`（进行中）、`waiting`（等待输入）、`paused`（已延期）、`unknown`（待核实）；正在推进统一用 `active`，禁止 `in_progress`。它是项目跟进状态，不代表所有功能已经验收，也不使用 `done`。
- `overview`、`progress`、`next` 为非空字符串；复杂文字用 YAML 引号或块字符串。`progress` 区分实际通过、历史记录及尚未验收的事项。
- `evidence` 为非空字符串列表，只填项目内现存文件的相对路径，使用 `/`；禁止网址、描述文字、绝对路径、`.`/`..` 路径段、隐藏目录/文件、越界链接和 `PROJECT_STATUS.md` 自引用。不得读取或引用凭据、认证、运行目录；`runtime/`、`tmp/`、`temp/`、`logs/`、`storage/logs/` 下的文件不能作为 evidence 正本，核对过的结果应写入稳定任务/验收文档。
- URL、测试命令、结果、部署编号和说明写入项目内任务或验收文件，再由 `evidence` 引用该文件；注明实际验证日期、环境、结果及未验证项。迁移旧摘要文字时标为历史记录搬迁，不能冒充本次复测。
- 同步契约说明和模板位于日常库 `output/项目进展同步/README.md`、`PROJECT_STATUS.template.md`；本节保留完整字段规则，项目离开共同工作区后仍适用。跨项目访问须遵守既有项目边界；仅在用户本次明确授权访问日常同步工具且工具可用时，从共同工作区执行 `py -3.14 -B notebook_obsidian/日常/output/项目进展同步/sync_project_status.py --check --project FinUnityWorkspace/FinUnity`，只检查格式和证据路径，不读取或写入待办。

- 未使用同步工具时，仍须在本项目内逐项自检字段、状态与证据路径，并报告未通过项；不能跳过摘要维护。新建独立项目时先补齐本节约束和 PROJECT_STATUS.md，未知业务进度用 unknown，不因目录存在而推断完成。
