-- ============================================================
-- V21：商品成交模式
--
-- 背景：服务类商品原来靠 price_mode 隐式决定入口 ——
--   FIXED  → 同时给「立即购买」和「先提需求」（等于默认双入口）
--   其它   → 只给「提交需求」
-- 双入口不是错，但不该是默认。改成显式配置：同一商品原则上只配一种模式，
-- 确有业务需要才填 BOTH。
--
-- 空值（NULL）表示「按规则推导」，规则见 DATA-MODEL 第十八节：
--   非服务商品          → DIRECT
--   服务 + FIXED        → DIRECT
--   服务 + STARTING/CUSTOM → QUOTE
--
-- 存量处理：现有的「服务 + 固定价」商品本来是双入口，直接按新规则会少一个
-- 按钮。这里显式写成 BOTH，把升级前后的行为对齐；站长想收成单一模式，
-- 在后台改一下即可，不用怕升级把入口弄没了。
-- ============================================================

ALTER TABLE `product`
	ADD COLUMN `deal_mode` VARCHAR(16) NULL
		COMMENT 'DIRECT=直接下单 / QUOTE=需求报价 / BOTH=两者都允许；NULL=按 price_mode 推导'
		AFTER `price_mode`;

UPDATE `product`
SET `deal_mode` = 'BOTH'
WHERE `product_type` = 'SERVICE'
  AND `price_mode` = 'FIXED';
