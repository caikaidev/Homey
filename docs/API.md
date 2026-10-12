# 崽崽小看板 · API 契约（v1，P1 冻结）

Web 与 Android App 共用。已发布的字段只增不改；新增字段客户端应忽略未知字段。
Worker 测试（`web/test/api.test.ts`）按本文件断言。

## 通用

- 基础路径：`/api`，请求与响应均为 JSON（`Content-Type: application/json`）。
- 日期：业务日期 `YYYY-MM-DD`，按 Asia/Shanghai 计算；时间点 `HH:MM`；时间戳为毫秒 UTC。
- 错误：`{ "error": { "code": "...", "message": "给人看的中文说明" } }`

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | `bad_request` / `bad_schedule` / `not_scheduled` | 参数不对 / 排期不合法 / 这一天这个时间点不需要做 |
| 401 | `unauthorized` | 没登录或口令错误 |
| 403 | `forbidden` | 没权限（App 改事项/设置，撤销别人的登记） |
| 404 | `not_found` | 不存在 |
| 409 | `task_inactive` / `undo_expired` | 事项已暂停或归档 / 超过 2 分钟不能撤销 |

### 鉴权与记录人

| 端 | 方式 | 记录人 |
| --- | --- | --- |
| Web（家长） | Cloudflare Access 登录（`Cf-Access-Jwt-Assertion` 头或 `CF_Authorization` cookie）。cookie 方式的写请求必须同源（`Origin` 一致） | 设置里“邮箱 → 显示名”的映射，没配时用邮箱 `@` 前的部分 |
| App（照护人） | `Authorization: Bearer <FAMILY_KEY>` | `X-Recorder` 头（URL 编码的显示名，最多 20 字），没带时为“照护人” |

App 对事项和设置只读；只有家长能调用标注 **家长** 的接口。

## 对象

### Schedule

```json
{ "type": "daily", "start": "2026-10-10", "times": ["19:00"] }
{ "type": "interval_days", "every": 2, "start": "2026-10-10", "times": ["08:00"] }
{ "type": "weekdays", "weekdays": [1, 3, 5], "start": "2026-10-10", "times": ["08:00"], "end": "2026-12-31" }
```

- `start` 起生效，`interval_days` 以 `start` 为锚点：`(date - start) % every == 0`。暂停再恢复不改锚点。
- `every` 2–30；`times` 1–6 个，服务端去重并排序。
- `weekdays`（P2）：0 = 周日 … 6 = 周六，去重排序。
- `end`（P2，可选，所有类型）：最后一天（含当天），不早于 `start`。没有就是长期。

### Task

```json
{
  "id": "uuid", "title": "AD 一粒", "kind": "supplement", "schedule": { … },
  "describe": "隔天 08:00（10月10日起）", "priority": 0, "status": "active",
  "note": "", "created_by": "爸爸", "created_at": 0, "updated_at": 0
}
```

`kind`：`supplement` | `medicine`。`status`：`active` | `paused` | `archived`。`priority` 越大越靠前。

### Completion（登记）

```json
{ "id": "uuid", "result": "done", "reason": "", "recorded_by": "奶奶",
  "recorded_at": 0, "source": "app", "can_undo": true }
```

`result`：`done` | `skipped`。`source`：`web` | `app` | `widget`。`can_undo`：是当前调用者自己的登记且未满 2 分钟。

## 接口

### `GET /api/ping`
`{ ok: true, actor: { kind: "web"|"app", name }, serverTime }`

### `GET /api/board?date=YYYY-MM-DD`
`date` 默认今天。

```json
{
  "date": "2026-10-10", "weekday": "周六", "nickname": "崽崽", "pending_count": 1,
  "today": [
    { "task_id": "…", "title": "钙", "kind": "supplement", "note": "", "priority": 0,
      "slot": "19:00", "status": "pending", "completion": null }
  ],
  "tomorrow": { "date": "2026-10-11", "items": [ { "task_id": "…", "title": "AD", "kind": "supplement", "slot": "08:00" } ] },
  "serverTime": 0
}
```

- 只含进行中的事项。`status`：`pending` | `done` | `skipped`。
- 排序：未登记在前，同组按 `slot`、再按 `priority` 降序。
- 当天改过排期时，已有的登记仍会出现（时间点保留）。

### `GET /api/tasks?status=active,paused`
`{ tasks: Task[] }`。`status` 可多选，默认全部。

### `GET /api/tasks/:id`
`{ task: Task, preview: [{ date, times[] }] }`（从今天起 7 天）

### `POST /api/tasks` · 家长
请求 `{ title, schedule, note?, priority?, kind? }` → 201 `{ task }`

### `PATCH /api/tasks/:id` · 家长
请求任意子集 `{ title?, schedule?, note?, priority? }` → `{ task }`。改排期只影响以后，不改已有登记。

### `PATCH /api/tasks/:id/status` · 家长
请求 `{ status }` → `{ task }`

### `POST /api/checkin`
请求 `{ task_id, date, slot, source? }`

- 201：新登记；200：已有有效登记（原样返回，不改写）。都返回 `{ created, task_id, date, slot, completion }`。
- 同一实例（`task_id + date + slot`）全局只有一条有效登记，并发提交也是。App 离线队列可以放心重发。
- `date` 不能晚于今天；`slot` 必须是该事项当天排期里的时间点；事项必须是进行中。

### `POST /api/skip`
同 `checkin`，另需 `reason`（必填，最多 100 字）。

### `POST /api/undo`
请求 `{ task_id, date, slot }` → `{ ok: true, task_id, date, slot }`。只能撤销自己 2 分钟内的登记；撤销是软删（保留审计），之后可以重新登记。

### `GET /api/history?days=14`
`days` 1–60。今天在前：

```json
{ "days": [ { "date": "2026-10-10", "weekday": "周六", "items": [
  { "task_id": "…", "title": "AD", "slot": "08:00", "status": "missed",
    "reason": "", "recorded_by": null, "recorded_at": null, "source": null }
] } ] }
```

`status`：`done` | `skipped` | `missed`（以前的日子没登记）| `pending`（今天还没登记）。未登记的实例按当前排期推算，只针对进行中的事项、且不早于事项创建那天。

### `GET /api/review?days=7`
家庭试用复盘（P4），`days` 1–60，统计口径同 `/api/history`；今天还没登记的不算到期。

```json
{ "from": "2026-10-04", "to": "2026-10-10",
  "due": 14, "done": 12, "skipped": 1, "missed": 1,
  "on_time": 11, "on_time_rate": 0.79, "median_delay_min": 12,
  "late_day": 0, "by_source": { "app": 10, "web": 3 }, "by_recorder": { "奶奶": 10, "妈妈": 3 },
  "undone": 1,
  "days": [ { "date": "2026-10-10", "weekday": "周六", "due": 2, "on_time": 2, "missed": 0 } ] }
```

- `on_time`：计划时间后 60 分钟内登记（提前也算）；`on_time_rate = on_time / due`。
- `median_delay_min`：已喂的登记时间减计划时间的中位数（分钟，提前为负）。
- `late_day`：登记日期和那件事的日期不是同一天（隔天补登），日期错位要看它。
- `undone`：撤销后没有再登记的次数，误触的参考。

### `GET /api/settings`
`{ child_nickname, members: { parents: [{ email, name }], caregivers: [name] } }`

### `PATCH /api/settings` · 家长
请求任意子集 `{ child_nickname?, members? }` → 同 GET。`members` 整体替换；每类最多 10 人。

### `POST /api/parse` · 家长
请求 `{ text }` → 排期草稿，**不保存**：

```json
{ "source": "gemini", "title": "AD", "kind": "supplement", "schedule": { … }, "describe": "隔天 08:00（10月10日起）",
  "preview": [ { "date": "2026-10-10", "times": ["08:00"] }, … ], "warnings": [] }
```

- 配了 `GEMINI_API_KEY` 时先用 Gemini（`source: "gemini"`）。没配 Key、超时（15 秒）、出错或返回的排期不合法时，降级为规则解析（`source: "rules"`），并在 `warnings` 第一条说明原因。
- `kind`：`supplement` | `medicine`；用药时 `warnings` 一定含“请按医嘱核对剂量与疗程”。
- `preview` 从今天和 `start` 中较晚的一天起算。
