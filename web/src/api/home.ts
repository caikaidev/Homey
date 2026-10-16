import { Hono } from 'hono';
import { bizDate } from '../bizdate';
import { listTasks, TASK_KINDS } from '../db';
import type { AppEnv } from '../env';
import { completionsBetween, instancesOn } from './board';
import { imagesFrom, presentImage, purgeExpired } from './images';

/** 首页卡片的名字。顺序就是 App 首页的顺序。 */
export const CATEGORY_TITLES: Record<string, string> = { supplement: '补剂', medicine: '药品', babyroom: '宝宝屋' };

export const home = new Hono<AppEnv>();

/**
 * App 首页的大卡片：有数据的类别才出现。
 * - 补剂 / 药品：有进行中的事项就显示，附今天还剩几件。
 * - 宝宝屋：今天有图片才显示。顺手删掉过期图片。
 */
home.get('/home', async (c) => {
  const now = Date.now();
  const date = bizDate(now);
  await purgeExpired(c.env.DB, date);
  const [tasks, completions, images] = await Promise.all([
    listTasks(c.env.DB, ['active']),
    completionsBetween(c.env.DB, date, date),
    imagesFrom(c.env.DB, date),
  ]);
  const today = instancesOn(date, tasks, completions);

  const cards: object[] = [];
  for (const kind of TASK_KINDS) {
    if (!tasks.some((t) => t.kind === kind)) continue;
    const mine = today.filter((i) => i.task.kind === kind);
    cards.push({
      key: kind,
      type: 'tasks',
      title: CATEGORY_TITLES[kind],
      total: mine.length,
      pending: mine.filter((i) => !i.completion).length,
    });
  }
  const babyroom = images.find((m) => m.category === 'babyroom' && m.use_date === date);
  if (babyroom) cards.push({ key: 'babyroom', type: 'image', title: CATEGORY_TITLES.babyroom, image: presentImage(babyroom) });

  return c.json({ date, cards, serverTime: now });
});
