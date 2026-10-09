# 牛牛照护看板 · MVP 开发计划（给 Claude Code 执行）

> 目标：家长在 Web 配置照护事项（支持一句话/AI 解析排期），奶奶在 Android App 查看今日事项（可点确认，也可只看），全家看到同一份记录。
> 技术栈已定：Cloudflare Workers + D1 + Access + Workers AI；Android Kotlin。
> 参考实现：`~/workspace/niuniu-care/`（Web 原型 v0.1，本地全链路已验证，可对照或重写）。
> 需求详见：`~/workspace/user/files/niuniu-care-mvp_0_8cpf.md`（产品边界与验收清单，以它为准）。

---

## P0 — 地基（先跑起来）

- 建 Cloudflare 项目：`wrangler.toml`，Workers + D1 + 静态资源目录
- D1 建表：`migrations/0001_init.sql`（`tasks` 事项+排期JSON、`completions` 登记记录，唯一键 task_id+date+slot）；后续 schema 变更只加新 migration 文件，不直接改旧表
- **GitHub → Cloudflare 直接部署**：
  - 仓库放 GitHub 私有库（家庭项目不公开）；Claude Code 直接管理
  - Cloudflare Dashboard → Workers → 连接 GitHub 仓库（Workers Builds），push 到 main 自动部署
  - `wrangler.toml` 里填好真实 `database_id`；`FAMILY_KEY` 等密钥走 Cloudflare Dashboard 的 Secrets，不进仓库
  - D1 远程迁移：`npx wrangler d1 migrations apply niuniu-care --remote`
  - 如已有旧项目：Claude Code 负责把旧代码合并进来，API 形状以本计划 P1 为准
- **做**：最小可部署骨架。**不做**：任何业务页面。
- **验收**：push 到 main 后自动部署，公网 URL 能打开，API 返回 401（无口令）/ 200（有口令）。

## P1 — Web 核心闭环（先只做每日补充剂：AD/钙）

优先级说明：第一目标是让 AD/钙的"每天吃、吃了没"跑顺；生病用药是小概率场景，放到后续按需做。

API（全部 `/api/*`，JSON）：
- `GET /api/board?date=` → 今日事项实例（含完成状态）+ 明日只读预览
- `POST /api/tasks` / `GET /api/tasks` / `PATCH /api/tasks/:id/status`（active/paused/archived）
- `POST /api/checkin`（幂等，重复提交不产生重复记录）、`POST /api/skip`（带原因）
- `GET /api/history?days=`

排期（先只做补充剂够用的）：
- 类型：daily / interval_days（AD 隔天、钙每天）；一天一个时间点
- 实例按"业务日期（Asia/Shanghai）+ 排期"实时计算，不预生成；服务端是唯一真相

Web 页面（手机优先）：
- 今日：未登记在前，高优先级按钮文案"确认已喂"；明日只读
- 新建：一句话输入框 + 规则解析先行（"AD隔天吃早上8点"能解析就行，Workers AI 放 P2）
- 事项：暂停/恢复/归档；记录：14 天历史带记录人
- **验收**：家长在手机浏览器建好 AD/钙，一句话建事项可用；全家看到同一份"今天吃了没"；隔日排期跨天正确；重复登记不产生重复。

## P2 — AI 解析增强 + 排期扩展

- `POST /api/parse`：输入一句话 → 输出 `{schedule, describe, preview[7天], warnings[]}`
- Workers AI（`@cf/meta/llama-3-1-8b-instruct`，prompt 见参考实现 `src/parse.ts`）；无 binding/失败时降级规则解析
- **铁律**：AI 只做"翻译"，结果必须经用户核对 7 天预览后手动确认才保存；warnings 把没说清的标黄
- 前端加 🎤 语音按钮（Web Speech API，zh-CN）
- 排期扩展：一周几次（weekdays）、一天多次（times 数组）、结束日期（给后续用药场景铺路）
- **验收**："感冒药一天三次，吃3天"→每天3次+结束日期+warning；解析失败不阻塞，可手动填。

## P3 — Android 奶奶端（查看为主）

- 清单页：今日事项大字（20sp+、48dp 触控区），状态不只靠颜色区分；明日只读预览
- 登记：点确认（可不点，只看也行）；2 分钟内可撤销自己的记录
- 桌面小组件：今日日期、未登记数、前 3 项、最后同步时间；点击进 App
- 打开时语音播报（系统 TTS，可关）：最多播 3 项；离线显示"离线·最后同步时间"，不伪装最新
- Room 缓存 + 后台同步；通知为"尽力而为"提醒，不承诺精确闹钟
- **验收**：MVP 文档第 11 节验收清单全部通过；奶奶不找菜单完成查看和登记。

## P4 — 家庭试用打磨

- 连续 7 天真实试用（就用 AD/钙的日常），修日期错位/同步/误触问题
- 复盘：及时登记率、口头核对次数、奶奶是否主动用小组件
- **验收**：试用结论认为"比微信问更省心"，再考虑后续。

## 后续（按需，不阻塞试用）

- 生病用药场景：疗程（吃 N 天停）、症状记录（体温/咳嗽）、用药后观察提醒、误服应急文案、临时加任务下发
- 健康管理：疫苗/体检提醒、过敏忌口标红、生长曲线
- 喂养/睡眠/排便记录、外出交接摘要、库存联动（AD 快吃完提醒补货）
- **不做**（明确砍掉）：精确闹钟、农历排期、多孩子、公开注册

---

## 执行顺序建议

P0 → P1 → P2 → P3 → P4，严格按顺序，前一阶段验收通过再进下一阶段。
P1 只做 AD/钙的每日补充剂闭环；生病用药等场景进"后续"backlog，不阻塞试用。
P1 的 API 形状一旦定下来就不要改（App 复用同一套）。
有疑问先看 `niuniu-care-mvp_0_8cpf.md` 第 11 节验收清单，它是最终裁判。
