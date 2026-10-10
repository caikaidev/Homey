# 崽崽小看板

[![Android](https://github.com/caikaidev/Homey/actions/workflows/android.yml/badge.svg)](https://github.com/caikaidev/Homey/actions/workflows/android.yml)
[![Web](https://github.com/caikaidev/Homey/actions/workflows/web.yml/badge.svg)](https://github.com/caikaidev/Homey/actions/workflows/web.yml)

> 家长在 Web 上配置宝宝的照护事项，奶奶在 App 上看今天要做什么、做了就点确认，全家看到同一份记录。

宝宝昵称可自定义（默认“崽崽”）。一个部署 = 一个家庭。

| 端 | 技术 | 目录 |
| --- | --- | --- |
| 家长 Web + API | Cloudflare Workers（Hono）+ D1 + Access，前端原生 HTML/JS | `web/` |
| 照护人 App | Android（Kotlin + Compose），包名 `ian.dev.zaizai` | `app/` |

文档：

- [需求与验收清单](docs/REQUIREMENTS.md)（最终裁判）
- [改造与开发计划](docs/PLAN.md)
- [API 契约](docs/API.md)（Web 与 App 共用）
- [部署指南](docs/DEPLOY.md)

## 本地开发

```bash
# Web / API
cd web
npm ci
cp .dev.vars.example .dev.vars      # 本地家庭口令
npm run db:migrate:local
npm run dev                          # http://localhost:8787
npm test                             # vitest（Workers 运行时 + 本地 D1）

# Android
./gradlew testDebugUnitTest assembleDebug
```

## 约定

- D1 结构变更只追加新的 `web/migrations/NNNN_*.sql`，不修改已上线的迁移。
- 任何第三方 API Key 只放在 Worker Secrets，Web 和 App 都不持有。
- 代码里不出现具体家庭的名字、邮箱等信息。
