import { cloudflareTest, readD1Migrations } from '@cloudflare/vitest-plugin';
import { defineConfig } from 'vitest/config';

export default defineConfig(async () => {
  const migrations = await readD1Migrations('./migrations');
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: './wrangler.toml' },
        miniflare: {
          // 测试不连真实的 Access：清空团队域名与 AUD。
          bindings: { FAMILY_KEY: 'test-family-key', ACCESS_TEAM_DOMAIN: '', ACCESS_AUD: '', TEST_MIGRATIONS: migrations },
        },
      }),
    ],
    test: { setupFiles: ['./test/apply-migrations.ts'] },
  };
});
