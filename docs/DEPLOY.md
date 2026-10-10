# 部署指南（Cloudflare）

一次性设置，按顺序做。需要 Cloudflare 账号，域名可选（没有就用 `*.workers.dev`）。

## 1. 建 D1 数据库

```bash
cd web
npx wrangler login
npx wrangler d1 create <数据库名>   # 也可以在 Dashboard 手动建，名字随意
```

把 `database_id` 和数据库名分别填进 `web/wrangler.toml` 的 `database_id`、`database_name`，提交到 main。
`database_id` 不是密钥，可以进仓库。

建表不用手动执行：Workers Builds 每次部署都会先跑 `npm run deploy`，
它会先执行 `wrangler d1 migrations apply DB --remote`（只跑还没跑过的迁移），再部署 Worker。

## 2. 连接 GitHub 自动部署（Workers Builds）

Dashboard → Workers & Pages → Create → Import a repository → 选本仓库：

| 项 | 值 |
| --- | --- |
| Project name | `zaizai-board`（必须与 `wrangler.toml` 的 `name` 一致） |
| Production branch | `main` |
| Root directory | `web` |
| Build command | `npm ci` |
| Deploy command | `npm run deploy`（先建表/迁移，再部署） |
| Build watch paths | `web/**`（只改 Android 时不触发部署） |
| Non-production branch deploy command | 保持默认 `npx wrangler versions upload`（**不要**改成 `npm run deploy`，否则分支预览也会改正式数据库） |

> 如果构建日志在迁移这一步报权限错误（如 `Authentication error`、`not authorized`），
> 说明构建用的 API Token 没有 D1 权限：到 Worker → Settings → Builds → API token，
> 换成一个包含 **Account / D1 / Edit** 和 **Workers Scripts / Edit** 的 Token，再点 Retry build。

## 3. 设置密钥

Dashboard → Workers → `zaizai-board` → Settings → Variables and Secrets → Add（类型选 **Secret**）：

| 名称 | 说明 |
| --- | --- |
| `FAMILY_KEY` | 家庭口令，给奶奶的 App 用。建议 `openssl rand -base64 24` 生成 |
| `GEMINI_API_KEY` | P2 再加 |

## 4. Cloudflare Access（家长登录）

Zero Trust → Access → Applications → Add → Self-hosted：

1. **应用**：域名填 Worker 的地址（`zaizai-board.<子域>.workers.dev` 或自定义域名），路径留空（整站）。
2. **策略**：Allow，Include → Emails → 填爸爸、妈妈的邮箱。
3. **再加一个应用**：同一域名，路径 `api/*`，策略 Action 选 **Bypass**，Include → Everyone。
   这样 App 调 `/api/*` 不会被 Access 拦，由 Worker 自己校验 `FAMILY_KEY`；浏览器访问 `/api/*` 时仍会带上 Access 登录信息。
4. 记下两个值，填进 `web/wrangler.toml` 的 `ACCESS_TEAM_DOMAIN` 和 `ACCESS_AUD`，提交到 main：
   - **团队域名**（形如 `https://<team>.cloudflareaccess.com`）：Zero Trust → Settings（General / Custom Pages）里的 Team domain。
     最省事的办法：配好 Access 后用无痕窗口打开网站，被跳转到的登录页地址开头就是它。
   - **AUD**（一长串十六进制）：Access → Applications → 点**整站那个应用** → Configure → Overview 里的 Application Audience (AUD) Tag。
     注意要整站应用的，不是 `api/*` 那个 Bypass 应用的。

> 注意：`/api/*` 设了 Bypass 后，Worker 校验 Access JWT 是家长身份的唯一依据，所以 `ACCESS_AUD` 必须填对。

## 5. 验收（P0）

```bash
URL=https://zaizai-board.<子域>.workers.dev
curl -i $URL/api/ping                                        # 401
curl -i -H "Authorization: Bearer <FAMILY_KEY>" $URL/api/ping  # 200
```

浏览器打开 `$URL`，用家长邮箱登录 Access 后，页面显示“已连接，当前身份：…”。
