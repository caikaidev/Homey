-- 按日期使用的图片（宝宝屋二维码等）。一个类别一天最多一张；过了使用日期由服务器顺手删除。
CREATE TABLE day_images (
  id          TEXT PRIMARY KEY,                       -- 每次上传换新 id，客户端可按 id 长期缓存
  category    TEXT NOT NULL,                          -- babyroom
  use_date    TEXT NOT NULL,                          -- YYYY-MM-DD，Asia/Shanghai
  mime        TEXT NOT NULL,                          -- image/jpeg | image/png | image/webp
  size        INTEGER NOT NULL,
  data        BLOB NOT NULL,
  created_by  TEXT NOT NULL DEFAULT '',
  created_at  INTEGER NOT NULL,
  UNIQUE (category, use_date)
);
