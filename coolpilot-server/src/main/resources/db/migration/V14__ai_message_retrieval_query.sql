-- 追问改写后，把「真正拿去检索的那句话」留档
--
-- 用户看到的是「那退款呢」，实际检索用的是改写后的独立问题。
-- 排查「为什么召回了这些片段」时，没有这一列就只能靠猜。
-- 只对 USER 消息有值；早期数据为空，表示当时没有改写。
ALTER TABLE ai_message
	ADD COLUMN retrieval_query VARCHAR(200) NULL COMMENT '本轮实际用于检索的查询（追问改写后）' AFTER content;
