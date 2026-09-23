# 家小满（Homey）

[![CI](https://github.com/caikaidev/Homey/actions/workflows/ci.yml/badge.svg)](https://github.com/caikaidev/Homey/actions/workflows/ci.yml)

安装包 ID / 代码包名：`ian.dev.homey`

> 不用记什么时候该买什么。打开 App，只看今天要处理的事。

## V1 范围（精简版）

| 页面 | 做什么 |
| --- | --- |
| 今天 | 「该买了」勾选买到 → 一键入库；「快过期，先吃掉」；「一周内会用完」 |
| 物品 | 全部物品，按分类筛选、搜索；右上角进入「数据与备份」 |
| 详情 | 还够用几天；随手加减数量 / 更新余量；买到了；不再记录（可撤销） |
| 添加 / 编辑 | 常用预设一键填好；3 种记录方式；分类、位置、提醒天数放在「更多设置」 |
| 提醒与备份 | 每日提醒开关与时间（默认 9:00）；每日自动备份、选择备份文件夹、导出 JSON、从备份恢复（合并/覆盖，可撤回）、导出 CSV |

三种记录方式：

- **数数量**（纸尿裤、湿巾、抽纸）：剩余数量 ÷ 每天用量 = 还能用几天；每天用量填 0 表示常备品，不预测。
- **看余量**（洗洁精、奶粉、面霜）：满 / 75% / 50% / 25% / 用完，按「一整瓶大约用几天」估算。
- **看保质期**（牛奶、水果）：每次买回来是一批，各自有到期日，优先提醒最早到期的那批。

判断规则（`domain/prediction/PredictionEngine.kt`，纯函数，有单元测试）：

- 还能用天数 ≤ 提前提醒天数 → 该买了；再多 7 天以内 → 一周内会用完
- 余量 ≤ 25% → 该买了
- 3 天内到期 → 快过期；1 天内 → 红色

暂不做（V2 再考虑）：待办、日历同步、拍照识别/扫码、按周期记录、购买记录与价格、平板布局、按流水自动学习每天用量。

## 数据保存约定

目标：**升级不丢数据、换手机不重新录入、以后能平滑迁移。**

- **主键是字符串 UUID**，不是自增整数。两台手机的数据合并时不会撞 ID。
- **软删除**：删除只写 `deletedAt`，合并旧备份时不会把删掉的东西复活。
- **`updatedAt` 是合并依据**：库存变化也会刷新所属物品的 `updatedAt`。
- **库存流水 `stock_logs` 只追加**：每次新建、加减、更新余量、补货都会记一条。
- **写入用 `@Upsert`，不用 `REPLACE`**：`REPLACE` 会先删再插，触发外键级联删掉库存和流水。
- **枚举按 name 存字符串**：已发布的 name 不改名，废弃值在 `fromCode()` 里显式映射。

### 数据库版本与迁移

- `AppDatabase.VERSION = 2`，`exportSchema = true`，结构导出到 `app/schemas/`（**首次编译后请提交生成的 `2.json`**）。
- **不使用** `fallbackToDestructiveMigration()`：缺迁移时宁可崩溃，也不静默清空数据。
- 每次升级前，`PreMigrationBackup` 会把原始数据库文件复制到 `files/backups/pre-migration/`。
- `MIGRATION_1_2`：自增 ID → UUID；6 种模式并为 3 种（PACKAGE/NONE → 数数量，CYCLE → 看余量）；送达天数 + 安全天数 → 提前提醒天数；批次备注 → 存放位置；购买记录、使用周期 → 库存流水；待办保留（暂不显示）；提醒表是可重算的数据，不保留。
- 改 `@Entity` 的流程：版本号 +1 → 在 `Migrations.kt` 追加迁移（已发布的不改）→ 在 `MigrationTest` 里补测试。

### 备份文件格式

```json
{
  "format": "homey-backup",
  "formatVersion": 1,
  "exportedAt": 1790000000000,
  "appVersion": "1.1",
  "products": [], "inventories": [], "stockLogs": [], "todos": []
}
```

- 读取时忽略不认识的字段、给缺失字段默认值；`formatVersion` 更高的文件会提示先升级 App。
- 格式有不兼容变化时 `formatVersion` +1，并在 `BackupCodec.upgrade()` 里补转换。
- 恢复前一定先把当前数据存一份快照（`files/backups/before-restore/`，保留 5 份），可一键撤回。
- 合并规则：按 ID 对齐，`updatedAt` 较新的一方胜出并带上它的全部库存批次；流水按 ID 去重追加。

### 备份存放位置

| 位置 | 说明 |
| --- | --- |
| `files/backups/auto/` | 每日自动备份，保留 7 份；随系统备份（Google 备份 / 换机迁移）一起走 |
| 用户选择的文件夹（SAF） | 同样每日写入、保留 7 份；卸载 App 也不会删 |
| 手动导出 | 任意位置，文件名 `homey-backup-yyyy-MM-dd.json` |

`backup_rules.xml` / `data_extraction_rules.xml` 已包含数据库、设置和 `backups/` 目录。

## 代码结构

```
app/src/main/java/ian/dev/homey/
├── data/
│   ├── model/        Entities.kt（Product/Inventory/StockLog/Todo）、Enums.kt
│   ├── dao/          Daos.kt
│   ├── database/     AppDatabase.kt、Migrations.kt
│   ├── repository/   HomeyRepository.kt（所有写操作，事务 + 流水）、SampleData
│   └── backup/       BackupCodec（JSON 格式）、BackupManager（导出/恢复/自动备份/CSV）、BackupPrefs
├── domain/
│   ├── prediction/   PredictionEngine.kt
│   ├── worker/       DailyReminderWorker（定点提醒，每次执行完预约明天）、AutoBackupWorker（每日备份）
│   └── notification/
└── ui/               HomeyViewModel、MainScreen、screens/、components/、theme/
```

## 构建与测试

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest   # PredictionEngine / ReminderSchedule / Migration / Backup / Repository
```

每次 push 到 main 和每个 PR 都会在 GitHub Actions 上跑单元测试、打 debug 包，并检查 Room 结构文件是否已提交；debug APK 可在 Actions 页面下载。

设计稿：见 Claude Design 画布（今天 / 物品 / 详情 / 添加 / 数据与备份 五个画板）。
