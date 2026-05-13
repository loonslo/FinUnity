# FinUnity 产品化优化任务板

来源：用户反馈 - 资产管理产品化改造（第三轮）

状态说明：
- `[ ]` 待处理
- `[x]` 已完成

## P5 第二轮优化（已完成）

- [x] P5-01 ~ P5-07：全部完成

## P6 第三轮优化

### P6-01 持仓页面顶部分类样式美化
- [x] PriceChangeScreen.kt 的 PriceFilterPanel 样式优化
- [x] 与其他页面（MainScreen、AccountHubScreen）风格协调

### P6-02 所有 TopAppBar 去掉 title Text
- [x] 检查所有 Screen 的 TopAppBar
- [x] 移除 `title = { Text(...) }` 这种写法
- [x] 改为直接使用默认或空 title

### P6-03 账户Hub页面账户类型样式调整
- [x] AccountHubScreen.kt 移除 AccountTypeChip 的边框
- [x] 账户类型作为一行文字展示在总资产下方
- [x] 用颜色区分不同类型

### P6-04 账户Hub页面导入按钮调整
- [x] 移除页面内的"导入数据"按钮
- [x] 在总资产卡右上角添加一个导入图标按钮
- [x] 按钮只有图标，没有文字

### P6-05 编辑持仓页删除入口调整
- [x] AssetRecordScreen.kt 移除底部的"危险操作"卡片
- [x] 在 TopAppBar 右侧添加一个小图标（感叹号）
- [x] 点击图标后显示删除确认对话框

### P6-06 持仓详情页交互优化
- [x] AssetDetailScreen.kt 买入/卖出按钮移到页面底部
- [x] "调整持仓"按钮替代原有的编辑入口
- [x] 移除 TopAppBar 的编辑图标
- [x] 确保入口数量合理

### P6-07 返回按钮行为修复（已完成）
- [x] 实现导航栈，navigateTo/navigateBack

## 编译验证

- [x] BUILD-01 ./gradlew assembleDebug 通过
- [x] BUILD-02 ./gradlew test 通过