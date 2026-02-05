-- 修复 Hibernate 序列表 "could not read a hi value" 错误
-- 原因：GenerationType.AUTO 在 MySQL 下使用表模拟序列，序列表存在但为空
-- 注：代码已改为 GenerationType.IDENTITY，以下为临时修复或回滚场景使用
--
-- 执行前请先查看表结构：DESCRIBE original_msg_seq;

-- ========== 方案 A：临时修复（不改代码时） ==========
-- 表仅有 next_val 列（Hibernate 6 默认）
INSERT INTO original_msg_seq (next_val) VALUES (1);

-- ========== 方案 B：已改用 IDENTITY 时，确保表支持 AUTO_INCREMENT ==========
-- 若以下表之前由 TABLE 策略创建，id 列可能无 AUTO_INCREMENT，需执行（表不存在则跳过）：
ALTER TABLE original_msg MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE corplist MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE delay_message MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE md5index MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
