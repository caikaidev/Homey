export interface Env {
  DB: D1Database;
  ASSETS: Fetcher;
  /** App 调用 API 的家庭口令（Secret）。未设置时一律拒绝口令登录。 */
  FAMILY_KEY?: string;
  /** Cloudflare Access 团队域名，如 https://<team>.cloudflareaccess.com；留空表示不启用。 */
  ACCESS_TEAM_DOMAIN?: string;
  /** Cloudflare Access 应用的 AUD 标签。 */
  ACCESS_AUD?: string;
  GEMINI_API_KEY?: string;
  GEMINI_MODEL?: string;
}

/** 当前请求是谁发起的。记录人（recorded_by / created_by）取 name。 */
export type Actor =
  | { kind: 'web'; email: string; name: string }
  | { kind: 'app'; name: string };

export type AppEnv = { Bindings: Env; Variables: { actor: Actor } };
