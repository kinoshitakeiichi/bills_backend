-- V43 分类图标字段。可重复执行。
ALTER TABLE bills.b_categories
  ADD COLUMN IF NOT EXISTS icon_code varchar(64);

COMMENT ON COLUMN bills.b_categories.icon_code IS
  'Lucide icon stable name used by category UI; null means inherit parent/default visually';
