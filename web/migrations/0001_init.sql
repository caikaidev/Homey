-- 崽崽小看板 初始结构。已上线的迁移文件不再修改，结构变更只追加新文件。
-- 约定：主键为字符串 UUID；枚举存字符串；时间存毫秒 UTC；业务日期为 Asia/Shanghai 的 YYYY-MM-DD。

CREATE TABLE tasks (
  id            TEXT PRIMARY KEY,
  title         TEXT NOT NULL,
  kind          TEXT NOT NULL DEFAULT 'supplement',   -- supplement | medicine（后续）
  schedule_json TEXT NOT NULL,                        -- {"type":"daily"|"interval_days","every":2,"start":"2026-10-10","times":["08:00"]}
  priority      INTEGER NOT NULL DEFAULT 0,           -- 越大越靠前
  status        TEXT NOT NULL DEFAULT 'active',       -- active | paused | archived
  note          TEXT NOT NULL DEFAULT '',
  created_by    TEXT NOT NULL DEFAULT '',
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);

CREATE INDEX idx_tasks_status ON tasks (status);

CREATE TABLE completions (
  id           TEXT PRIMARY KEY,
  task_id      TEXT NOT NULL REFERENCES tasks (id),
  biz_date     TEXT NOT NULL,                         -- YYYY-MM-DD
  slot         TEXT NOT NULL,                         -- HH:MM，对应 schedule.times 中的一项
  result       TEXT NOT NULL,                         -- done | skipped
  reason       TEXT NOT NULL DEFAULT '',
  recorded_by  TEXT NOT NULL,
  source       TEXT NOT NULL DEFAULT 'web',           -- web | app | widget
  recorded_at  INTEGER NOT NULL,
  undone_at    INTEGER,                               -- 撤销为软删；重新登记时清空
  UNIQUE (task_id, biz_date, slot)
);

CREATE INDEX idx_completions_date ON completions (biz_date);

CREATE TABLE settings (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

INSERT INTO settings (key, value, updated_at) VALUES ('child_nickname', '崽崽', 0);
