# 改造计划：家小满（纯 Android）→ 牛牛照护看板（Web + Android）

> 依据：《牛牛照护看板 · MVP 开发计划》。本文件说明在**现有 Homey 仓库**上怎么落地，阶段划分沿用原计划 P0–P4。
> 状态：草案，待确认文末「待决策」后开工。

---

## 0. 现状与差距

| | 现在（v1.2） | 目标（MVP） |
| --- | --- | --- |
| 产品 | 家庭耗材补给预测（物品/库存/保质期） | 照护事项看板（AD/钙每日补充剂，后续用药） |
| 数据 | 仅本机 Room，备份靠 JSON 文件 | Cloudflare D1 为唯一真相，全家共享 |
| 端 | Android 单端 | 家长 Web（配置）+ 奶奶 Android（查看/登记） |
| 排期 | 无（只有每日提醒时间） | daily / interval_days → 后续 weekdays、一天多次、结束日期 |
| 身份 | 无 | Access（Web 家长）+ FAMILY_KEY（API/App），记录带记录人 |

结论：**业务模型基本是新的**，现有代码能复用的是工程骨架（Gradle、CI、签名发布）、Compose 主题与通用组件、通知/WorkManager 写法、"迁移不丢数据"的约定。

缺失的参考材料（本会话环境里没有，需要你提供或确认可不依赖）：
- `~/workspace/niuniu-care/`（Web 原型 v0.1，尤其 `src/parse.ts` 的 prompt 和规则解析）
- `niuniu-care-mvp_0_8cpf.md`（第 11 节验收清单 —— 原计划说它是最终裁判）

---

## 1. 仓库结构（monorepo，Android 原地不动）

```
Homey/
├── app/                 # Android（保持原位，避免改 Gradle/CI/release 路径）
├── web/                 # 新增：Cloudflare Worker + 静态前端
│   ├── wrangler.toml    # Workers + D1 binding + assets 目录 + AI binding
│   ├── migrations/      # 0001_init.sql 起，只追加不改旧文件
│   ├── src/
│   │   ├── index.ts     # 路由 /api/*，其余交给静态资源
│   │   ├── auth.ts      # FAMILY_KEY / Access JWT 校验，解析记录人
│   │   ├── schedule.ts  # 排期 → 某业务日的实例（纯函数，重点测试）
│   │   ├── bizdate.ts   # Asia/Shanghai 业务日期
│   │   ├── parse.ts     # 规则解析（P1）+ Workers AI（P2）
│   │   └── api/         # board / tasks / checkin / skip / history / parse
│   ├── public/          # 手机优先的 Web 页面（今日 / 新建 / 事项 / 记录）
│   └── test/            # vitest + @cloudflare/vitest-pool-workers（本地 D1）
├── docs/
│   ├── PLAN.md          # 本文件
│   └── API.md           # P1 定稿的 API 契约（App 和 Web 共用，定了不改）
└── .github/workflows/   # ci.yml 拆成 android / web 两个 job（按路径触发）
```

- Workers Builds 连接本仓库，**Root directory 设为 `web/`**，push main 自动部署；Android 的改动不会触发部署（可配 build watch paths = `web/**`）。
- 前端先用无构建的原生 HTML/JS（或 Vite 小项目），P1 不引入框架，保持部署简单。
- 清理：`metadata.json`、`.env.example` 里的 AI Studio/Gemini 残留可以删掉；secrets-gradle-plugin 视情况保留给 `API_BASE_URL`。

---

## 2. 关键设计（P1 定下来就不改）

### 2.1 数据表（`migrations/0001_init.sql`）

- `tasks`：`id TEXT PK (uuid)`, `title`, `kind`（`supplement`，预留 `medicine`）, `schedule_json`, `priority`, `status`（active/paused/archived）, `note`, `created_by`, `created_at`, `updated_at`
- `completions`：`id`, `task_id`, `biz_date`（YYYY-MM-DD）, `slot`（"08:00"）, `result`（done/skipped）, `reason`, `recorded_by`, `recorded_at`, `undone_at`
  - `UNIQUE(task_id, biz_date, slot)` —— 幂等的根基；撤销用软删 `undone_at` 或直接删行（二选一，见待决策 Q5）
- 沿用现有约定：字符串 UUID、枚举存字符串且不改名、时间存毫秒 UTC。

### 2.2 排期与实例

- `schedule_json` P1：`{type:"daily"|"interval_days", every?:2, start:"2026-10-10", times:["08:00"]}`；P2 扩 `weekdays`、多 `times`、`end`。**字段一开始就用数组 `times`**，P2 扩展不破坏格式。
- 实例 = f(业务日期, 排期) 实时计算，**只在服务端算**。Android 不重写排期逻辑，只渲染 `/api/board` 的结果并缓存 —— 避免 TS/Kotlin 两份逻辑不一致。
- `interval_days` 以 `start` 为锚点：`(date - start) % every == 0`，跨月/跨年用纯日期差，测试覆盖。

### 2.3 API（补齐原计划没写但 P3 必须的两项）

| 方法 | 说明 |
| --- | --- |
| `GET /api/board?date=` | `{date, today:[实例+状态], tomorrow:[只读], serverTime}` |
| `GET/POST /api/tasks`，`PATCH /api/tasks/:id/status` | 事项 CRUD + 暂停/恢复/归档 |
| `PATCH /api/tasks/:id` | **补充**：改名/改排期（家长总会要改时间） |
| `POST /api/checkin` | `{task_id, date, slot}`，幂等：已存在返回原记录 200 |
| `POST /api/skip` | 同上 + `reason` |
| `DELETE /api/checkin`（或 `POST /api/undo`） | **补充**：P3「2 分钟内可撤销自己的记录」需要，P1 就定进契约 |
| `GET /api/history?days=14` | 带记录人 |
| `POST /api/parse` | P2 |

所有响应 JSON；错误统一 `{error:{code,message}}`。契约写进 `docs/API.md`，Worker 测试按它断言。

### 2.4 鉴权与记录人

- Web：整站走 Cloudflare Access（家人邮箱白名单），Worker 读 `Cf-Access-Jwt-Assertion` 校验并取邮箱 → 映射成"爸爸/妈妈"显示名。
- App：Access 对 `/api/*` 设 Bypass（或用 Service Token），Worker 校验 `Authorization: Bearer <FAMILY_KEY>`；记录人由 App 首次设置时选择（"奶奶"），随请求带 `X-Recorder`。
- P0 验收"无口令 401 / 有口令 200"即指这一层。

---

## 3. 分阶段执行

### P0 — 地基
- 建 `web/`：`wrangler.toml`、`0001_init.sql`、最小 Worker（`/api/ping` 鉴权后返回 200）、空白静态页。
- CI：新增 web job（`npm ci && npm run typecheck && npm test`），Android job 加 `paths` 过滤。
- 你需要在 Dashboard 做的：建 D1 拿 `database_id`、设 `FAMILY_KEY` secret、连 Workers Builds（root=`web/`）、配 Access 应用。
- 仓库可见性：确认 `caikaidev/homey` 是私有库。

### P1 — Web 核心闭环（AD/钙）
- `bizdate.ts`、`schedule.ts` 先写并单测（隔日跨天、跨月、暂停中的事项不出现、归档不出现）。
- API 全部实现 + 本地 D1 集成测试（重复 checkin 只一条）。
- 页面：今日（未登记在前，"确认已喂"）、明日只读、新建（一句话 + 规则解析 + 表单兜底）、事项管理、14 天记录。
- 产出 `docs/API.md` 并冻结。

### P2 — AI 解析 + 排期扩展
- `/api/parse`：Workers AI（llama-3.1-8b-instruct）→ 结构化 schedule；失败/无 binding 降级规则解析。
- 返回 7 天预览 + warnings，前端必须用户确认才保存；🎤 Web Speech API。
- 排期扩 weekdays / 多 times / end，对旧数据向后兼容。

### P3 — Android 奶奶端（对现有 App 的改造）
- 新增 `data/remote/`（Retrofit + Moshi，依赖已在）、`data/care/`（Room 缓存 board 快照 + 待上传登记队列，**Room 版本 2→3，按现有约定写迁移+测试**）。
- 首页换成「今日照护」：大字 20sp+、48dp 触控、状态用图标+文字不只靠颜色；明日只读。
- 登记：乐观更新 + 离线排队，幂等键 = task_id+date+slot，重试安全；2 分钟内显示"撤销"。
- 桌面小组件（Jetpack Glance，新依赖）：日期、未登记数、前 3 项、最后同步时间。
- 打开时 TTS 播报（可关，最多 3 项）；离线显示"离线 · 最后同步 HH:mm"。
- WorkManager 周期同步（~15 分钟，尽力而为）+ 定点通知复用 `DailyReminderWorker` 的写法。
- 设置页：服务器地址、FAMILY_KEY、我是谁（记录人）、播报开关。
- 现有补给功能的去留见待决策 Q1。

### P4 — 家庭试用
- 7 天真实使用；Worker 记简单指标（登记时间 vs 计划时间、来源 web/app/widget）便于复盘。

---

## 4. 待决策

1. **现有补给/库存功能怎么处理？**（建议：代码与数据保留，App 首页改为照护看板，补给入口收到设置里或暂时隐藏；后续"AD 快吃完提醒补货"正好可以接回来。）
2. **App 名称与包名**：继续叫"家小满 / ian.dev.homey"，还是改名"牛牛照护"？包名建议不改（改了等于新 App，奶奶手机要重装、旧数据不迁移）。
3. **鉴权方案**：Access 管 Web + `/api/*` Bypass 用 FAMILY_KEY 给 App —— 是否认可？
4. **前端技术**：原生 HTML/JS（最简）还是 Vite + 轻量框架？
5. **撤销语义**：软删（保留审计）还是硬删？建议软删。
6. 参考原型 `niuniu-care/` 和需求文档 `niuniu-care-mvp_0_8cpf.md` 能否放进仓库（如 `docs/`）？没有它们，第 11 节验收清单只能按本计划推断。
