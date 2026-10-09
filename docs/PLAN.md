# 改造计划：家小满（纯 Android）→ 牛牛照护（Web + Android）

> 依据：《牛牛照护看板 · MVP 开发计划》。本文件说明在**现有 Homey 仓库**上怎么落地，阶段划分沿用原计划 P0–P4。
> 状态：已定稿（2026-10-09），开工按本文件执行。

## 已确认的决策

| # | 决策 |
| --- | --- |
| 1 | **补给/库存功能全部移除**（物品、库存、保质期、备份恢复、CSV 导出等代码与 Room 表一并删除，不做数据迁移） |
| 2 | **改名改包名**：App 名「牛牛照护」，包名 `ian.dev.niuniu`（与旧 App 并存，旧 App 由用户手动卸载） |
| 3 | **鉴权**：Web 走 Cloudflare Access；App 调 `/api/*` 用 FAMILY_KEY。**所有第三方 API Key 只放在 Worker Secrets，客户端（Web/App）一律不持有** |
| 3' | **AI 解析改用 Gemini API**（替代原计划的 Workers AI），模型 `gemini-3.8-flash`，由 Worker 服务端调用 |
| 4 | **前端**：Hono（Worker 路由）+ 原生 HTML/CSS/JS（ES Module，无构建步骤） |
| 5 | 参考原型 `niuniu-care/` 与需求文档 `niuniu-care-mvp_0_8cpf.md` 放进仓库 `docs/reference/` |

---

## 0. 现状与差距

| | 现在（v1.2） | 目标（MVP） |
| --- | --- | --- |
| 产品 | 家庭耗材补给预测 | 照护事项看板（AD/钙每日补充剂，后续用药） |
| 数据 | 仅本机 Room | Cloudflare D1 为唯一真相，全家共享 |
| 端 | Android 单端 | 家长 Web（配置）+ 奶奶 Android（查看/登记） |
| 排期 | 无 | daily / interval_days → 后续 weekdays、一天多次、结束日期 |
| 身份 | 无 | Access（Web 家长）+ FAMILY_KEY（App），记录带记录人 |

Android 端保留：Gradle 工程、CI/签名发布流程、Compose 主题与通用组件、WorkManager/通知写法。其余业务代码删除。

---

## 1. 仓库结构

```
Homey/
├── app/                 # Android「牛牛照护」（位置不动，包名改为 ian.dev.niuniu）
├── web/                 # Cloudflare Worker + 静态前端
│   ├── wrangler.toml    # D1 binding、assets 目录、placement、GEMINI_MODEL 变量
│   ├── migrations/      # 0001_init.sql 起，只追加不改旧文件
│   ├── src/
│   │   ├── index.ts     # Hono：/api/* 路由，其余交给静态资源
│   │   ├── auth.ts      # FAMILY_KEY / Access JWT 校验，解析记录人
│   │   ├── bizdate.ts   # Asia/Shanghai 业务日期
│   │   ├── schedule.ts  # 排期 → 某业务日的实例（纯函数，重点测试）
│   │   ├── parse/       # rules.ts（P1 规则解析）+ gemini.ts（P2）
│   │   └── api/         # board / tasks / checkin / skip / undo / history / parse
│   ├── public/          # 手机优先页面：今日 / 新建 / 事项 / 记录（原生 JS）
│   └── test/            # vitest + @cloudflare/vitest-pool-workers（本地 D1）
├── docs/
│   ├── PLAN.md          # 本文件
│   ├── API.md           # P1 定稿的 API 契约（Web 与 App 共用，定了不改）
│   └── reference/       # 原型代码与 MVP 需求文档
└── .github/workflows/   # android / web 两个 job，按路径触发
```

- Workers Builds 连接本仓库，**Root directory = `web/`**，build watch paths = `web/**`，push main 自动部署。
- 删除 `metadata.json`、`.env.example` 中的 AI Studio/Gemini 客户端残留，以及 secrets-gradle-plugin（App 不再持有任何 Key）。

---

## 2. 关键设计（P1 定下来就不改）

### 2.1 数据表（`migrations/0001_init.sql`）

- `tasks`：`id TEXT PK (uuid)`, `title`, `kind`（`supplement`，预留 `medicine`）, `schedule_json`, `priority`, `status`（active/paused/archived）, `note`, `created_by`, `created_at`, `updated_at`
- `completions`：`id`, `task_id`, `biz_date`（YYYY-MM-DD）, `slot`（"08:00"）, `result`（done/skipped）, `reason`, `recorded_by`, `recorded_at`, `undone_at`
  - `UNIQUE(task_id, biz_date, slot)` 保证幂等；撤销为**软删**（写 `undone_at`，保留审计），再次登记时复用该行清空 `undone_at`
- 约定：字符串 UUID、枚举存字符串且不改名、时间存毫秒 UTC。

### 2.2 排期与实例

- `schedule_json`：`{type:"daily"|"interval_days", every?:2, start:"2026-10-10", times:["08:00"]}`；P2 扩 `weekdays`、多 `times`、`end`。`times` 一开始就是数组，扩展不破坏格式。
- 实例 = f(业务日期, 排期)，**只在服务端计算**；Android 只渲染 `/api/board` 结果并缓存，不重写排期逻辑。
- `interval_days` 以 `start` 为锚点：`(date - start) % every == 0`，纯日期差计算，测试覆盖跨月/跨年。

### 2.3 API

| 方法 | 说明 |
| --- | --- |
| `GET /api/board?date=` | `{date, today:[实例+状态], tomorrow:[只读], serverTime}` |
| `GET/POST /api/tasks`，`PATCH /api/tasks/:id/status` | 事项 CRUD + 暂停/恢复/归档 |
| `PATCH /api/tasks/:id` | 改名/改排期（补充项） |
| `POST /api/checkin` | `{task_id, date, slot}`，幂等：已存在返回原记录 200 |
| `POST /api/skip` | 同上 + `reason` |
| `POST /api/undo` | 撤销自己 2 分钟内的记录（补充项，P3 依赖） |
| `GET /api/history?days=14` | 带记录人 |
| `POST /api/parse` | P2 |

响应 JSON，错误统一 `{error:{code,message}}`。契约写进 `docs/API.md`，Worker 测试按它断言。

### 2.4 鉴权、记录人与密钥

- Web：整站在 Cloudflare Access 后面（家人邮箱白名单）；Worker 校验 `Cf-Access-Jwt-Assertion`，邮箱映射成"爸爸/妈妈"。
- App：Access 对 `/api/*` 设 Bypass，Worker 校验 `Authorization: Bearer <FAMILY_KEY>`；记录人在 App 首次设置时选择，随请求带 `X-Recorder`。
- Worker Secrets：`FAMILY_KEY`、`GEMINI_API_KEY`（Dashboard 配置，不进仓库）；`GEMINI_MODEL=gemini-3.8-flash` 作为普通变量放 `wrangler.toml`，换模型不改代码。

---

## 3. 分阶段执行

### P0 — 地基 + 清场
- **Android 清场**：删除补给相关代码、Room 实体/迁移/schema、备份功能及对应测试；改名「牛牛照护」、包名 `ian.dev.niuniu`；留一个能编译、CI 通过的空壳首页。版本号重置为 2.0（新 App）。
- 建 `web/`：`wrangler.toml`、`0001_init.sql`、Hono 最小 Worker（鉴权后 `/api/ping` 返回 200）、空白静态页。
- CI：新增 web job（`npm ci && npm run typecheck && npm test`），android job 加 `paths` 过滤；release workflow 适配新包名。
- 需要你在 Cloudflare Dashboard 做（我会给逐步清单）：建 D1 拿 `database_id`、设 Secrets、连 Workers Builds（root=`web/`）、配 Access 应用与 `/api/*` Bypass。
- **验收**：push main 自动部署；无口令 401 / 有口令 200；Android CI 绿。

### P1 — Web 核心闭环（AD/钙）
- 先写 `bizdate.ts`、`schedule.ts` 并单测（隔日跨天、跨月、暂停/归档不出现）。
- API 全部实现 + 本地 D1 集成测试（重复 checkin 只一条）。
- 页面：今日（未登记在前，"确认已喂"）、明日只读、新建（一句话 + 规则解析 + 表单兜底）、事项管理、14 天记录。
- 产出并冻结 `docs/API.md`。

### P2 — Gemini 解析 + 排期扩展
- `POST /api/parse`：Worker 调 Gemini（`gemini-3.8-flash`，结构化 JSON 输出 + responseSchema），prompt 参考原型 `parse.ts`；超时/失败/无 Key 时降级规则解析。
- **区域风险**：Gemini API 不对部分地区开放，而 Worker 默认在离用户最近的机房执行（国内用户可能落在香港等不支持的地区）。对策：`wrangler.toml` 配 placement 指定美国区域（或 Smart Placement）；实测不行再经 Cloudflare AI Gateway 转发。P2 第一步先做连通性验证。
- 返回 `{schedule, describe, preview[7天], warnings[]}`，前端必须用户核对确认才保存；🎤 Web Speech API（zh-CN）。
- 排期扩 weekdays / 多 times / end，向后兼容旧数据。

### P3 — Android 奶奶端
- `data/remote/`（Retrofit + Moshi）、`data/local/`（Room v1：board 快照 + 待上传登记队列）。
- 首页「今日照护」：20sp+ 大字、48dp 触控、状态用图标+文字；明日只读。
- 登记：乐观更新 + 离线排队，幂等键 task_id+date+slot；2 分钟内显示"撤销"。
- 桌面小组件（Jetpack Glance）：日期、未登记数、前 3 项、最后同步时间。
- 打开时 TTS 播报（可关，最多 3 项）；离线显示"离线 · 最后同步 HH:mm"。
- WorkManager 周期同步（尽力而为）+ 定点通知。
- 设置：服务器地址、FAMILY_KEY、我是谁、播报开关。

### P4 — 家庭试用
- 7 天真实使用；Worker 记录登记时间 vs 计划时间、来源（web/app/widget），便于复盘。

---

## 4. 开工前需要的输入

- [ ] 把 `niuniu-care/`（原型）和 `niuniu-care-mvp_0_8cpf.md` 放进 `docs/reference/`（push 到 `plan/care-board` 分支或在会话中上传）
- [ ] Cloudflare：D1 `database_id`、准备好 `FAMILY_KEY`、Gemini API Key（P2 前即可）
