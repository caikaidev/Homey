# 📦 家庭补给管家 (Family Supply Manager)

> **核心理念**：“不用记什么时候该买什么，App 帮你预测，并在真正需要行动时提醒。”

家庭补给管家是一款专为现代家庭与育儿家庭打造的**智能物资与消耗品补给管理应用**。针对母婴耗材、日化消耗品、食品生鲜及周期性耗材的不同使用特性，提供 6 种差异化跟踪模型与动态预测引擎，解决“频繁囤积又容易断货”或“临期浪费”的痛点。

---

## ✨ 核心特性

### 1. 🎯 首页「今天需要处理」聚合看板
告别冗长的仓库清单，打开应用直奔核心问题——**“我现在需要做什么？”**
- **🔴 紧急行动项**：已见底断货、达到理论补货点、或保质期 ≤ 1 天的物品。
- **🛒 家庭采购清单**：自动汇总当前所有需补货商品，支持一键**合并同步到系统日历**或一键**买入入库**。
- **⚠️ 即将过期食材**：冷藏/常温临期预警，提醒优先食用，减少浪费。
- **🟡 近期缺货预测**：展示未来 7~14 天预计需要补货的周期性耗材（如牙膏、面霜、滤芯）。
- **📋 家庭协同待办**：“家人交代过一次，但不应该再花脑力去记的事项”。

---

### 2. 📊 6 种物品追踪模式（告别单一记账）
不同物品有不同的物理消耗规律，采用专门的数学模型跟踪：
- 🔢 **按数量消耗（COUNT）**：如纸尿裤、鸡蛋、奶粉等。根据日均消耗量、快递送达天数与安全缓冲期计算安全再订购点。
- 📦 **按整包消耗（PACKAGE）**：如湿巾、抽纸、垃圾袋。记录单包使用天数与剩余包数。
- 🔄 **按使用周期（CYCLE）**：如电动牙刷头、面霜、净水滤芯。记录“开封”与“用完”，消除虚假精确，按“预计还能用 X 周”展示。
- 🧴 **按余量估算（LEVEL）**：如洗洁精、洗衣液、洗发水。无需数滴数，直观标记百分比（100%、75%、50%、25%、已见底）。
- ⏳ **按保质期管理（EXPIRY）**：如鲜奶、果蔬、辅食、生鲜海鲜。优先标注临期倒计时与强提醒。
- 📌 **常备固定物资（NONE）**：如备用剪刀、指甲钳、应急手电筒等。

---

### 3. 🧠 智能预测引擎 (`PredictionEngine`)
- **安全库存与再订购点公式**：
  $$\text{Reorder Point} = \text{Daily Burn Rate} \times (\text{Lead Time Days} + \text{Safety Days})$$
- 结合历史使用周期（`UsageCycle`）动态调整预估消耗速率。
- 状态三色分级：🔴 紧急行动 (Urgent) / 🟡 注意备货 (Warning) / 🟢 充裕正常 (Normal)。

---

### 4. 📅 智能合并系统日历同步
- **拒绝日历刷屏**：自动将所有待买物资**聚合成单条带清单详情的「🛒 家庭采购」日历日程**。
- **增量更新**：再次同步时自动更新已有日历日程（通过 `calendarEventId`），不产生重复垃圾日程。
- 支持独立待办事项一键生成带提醒的系统日历日程。

---

### 5. ⚡ 极速录入与多模态交互
- **📷 拍照 OCR 识别**：模拟包装标签识别，自动解析品名、消耗模式与规格。
- **▣ 条形码扫码录入**：支持扫入常见家庭商品条形码并自动补全。
- **⚡ 精选常用预设模板**：预置花王纸尿裤、全棉时代棉柔巾、爱他美奶粉、艾惟诺润肤霜、智利蓝莓等高频物资，一键添加。
- **✏️ 自定义录入**：支持配置存放位置（如“主卧卫生间”、“次卧壁橱”）、规格单位、消耗参数与备注。

---

### 6. ⏰ 后台自动巡检与通知 (`WorkManager`)
- 每天定时后台自动巡检（`RestockDailyWorker`）。
- 发现急需补货或临期商品时发送本地系统通知。

---

## 🛠 技术架构与技术栈

本项目完全遵循现代 Android 最佳架构实践（Modern Android Architecture）：

| 模块 / 层次 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **语言** | Kotlin | 100% Kotlin 编写，充分利用协程与 Flow |
| **UI 框架** | Jetpack Compose + Material 3 | 声明式现代 UI，适配 Edge-to-Edge 沉浸式与无障碍标准 |
| **架构模式** | MVVM + Clean Architecture | ViewModel 驱动，UI State 响应式流 |
| **本地持久化** | Room Database + KSP | 完整管理 Product, Inventory, UsageCycle, Todo 等关联表 |
| **后台任务** | WorkManager | 可靠的后台周期性库存与到期监测 |
| **单元与快照测试** | Robolectric & Roborazzi | 本地 JVM 快速验证核心业务逻辑与 Compose 界面快照 |

---

## 📂 项目结构概览

```text
app/src/main/java/com/example/
├── data/
│   ├── dao/                 # Room 数据访问对象 (ProductDao, TodoDao, etc.)
│   ├── database/            # Room 数据库配置与迁移 (AppDatabase)
│   ├── model/               # 领域模型与实体 (Product, Inventory, TrackingMode, etc.)
│   └── repository/          # 数据仓储层 (SupplyRepository)
├── domain/
│   └── prediction/          # 智能预测引擎算法 (PredictionEngine)
├── service/
│   └── worker/              # WorkManager 后台每日补给检查 Worker
├── ui/
│   ├── components/          # 公共 UI 组件 (StatusBadge, EmptyStateCard, etc.)
│   ├── screens/             # 功能屏幕 (HomeScreen, InventoryScreen, AddProductScreen, TodoScreen)
│   ├── theme/               # Material 3 主题配色与字体规范
│   └── SupplyViewModel.kt   # 全局/核心状态管理 ViewModel
└── util/
    ├── CalendarHelper.kt    # 系统日历日程读写与聚合同步工具
    └── NotificationHelper.kt# 本地系统通知渠道与推送工具
```

---

## 🚀 本地运行与构建

### 环境要求
- **Android Studio**: Hedgehog (2023.1.1) 或更高版本（推荐 Ladybug / Meerkat）
- **JDK**: JDK 17
- **Min SDK**: 26 (Android 8.0)
- **Target SDK / Compile SDK**: 35 (Android 15)

### 构建命令
在项目根目录下通过 Gradle 执行以下命令：

```bash
# 编译 Debug APK
gradle assembleDebug

# 运行本地 JVM 单元测试与 Robolectric 测试
gradle :app:testDebugUnitTest

# （可选）运行 Roborazzi 界面快照比对测试
gradle :app:verifyRoborazziDebug
```

---

## 🤝 贡献与反馈
欢迎提交 Issue 或 Pull Request 来改进家庭补给管家！
- 提交 Bug 反馈或新功能设想：请在 GitHub Issues 中提出。
- 贡献代码前请确保运行并通过 `gradle :app:testDebugUnitTest`。

---

## 📄 开源许可证
本项目基于 [MIT License](LICENSE) 开源。
