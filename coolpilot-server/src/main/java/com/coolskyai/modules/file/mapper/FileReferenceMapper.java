package com.coolskyai.modules.file.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Description: 附件引用检查
 *
 * 为什么用「文本匹配」而不是关联表：
 * 图片是直接写进 Markdown 正文的（文章、商品详情、项目详情、关于页），
 * 从来没有一张「内容 ↔ 文件」的关联表。要拦住「删了还在用的图」，
 * 只能按 objectKey 去这些长文本字段里找。
 *
 * 这不是最优雅的方案，但它是唯一无需改内容模型的方案：
 * 加关联表意味着每次保存内容都要解析 Markdown 再同步一遍关系，
 * 漏一次就出现「明明在用却提示可删」，比现在更危险。
 *
 * objectKey 形如 2026/11/uuid.png，正文里存的完整地址也包含这一段，
 * 所以直接 LIKE 匹配即可，换域名也不影响。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Mapper
public interface FileReferenceMapper {

	// ---------------- 正文里的图片 ----------------

	@Select("SELECT title FROM blog WHERE content LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> blogs(@Param("key") String key);

	@Select("SELECT title FROM product WHERE detail LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> products(@Param("key") String key);

	@Select("SELECT name FROM portfolio WHERE content LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> portfolios(@Param("key") String key);

	@Select("SELECT name_zh FROM about WHERE value LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> aboutEntries(@Param("key") String key);

	// ---------------- 独立图片字段（封面 / 头像 / 二维码） ----------------
	// 这些字段不在正文里，必须单独查：漏掉就会「删掉正在当封面的图」，
	// 前台列表直接变裂图，而且没人知道是哪一步弄坏的。

	@Select("SELECT title FROM blog WHERE first_picture LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> blogCovers(@Param("key") String key);

	@Select("SELECT title FROM product WHERE cover LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> productCovers(@Param("key") String key);

	@Select("SELECT name FROM portfolio WHERE cover_url LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> portfolioCovers(@Param("key") String key);

	@Select("SELECT nickname FROM user WHERE avatar LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> userAvatars(@Param("key") String key);

	@Select("SELECT CONCAT('动态 #', id) FROM moment WHERE content LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> moments(@Param("key") String key);

	@Select("SELECT account_name FROM social_account WHERE qr_url LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> socialAccounts(@Param("key") String key);

	@Select("SELECT name_zh FROM site_setting WHERE value LIKE CONCAT('%', #{key}, '%') LIMIT 5")
	List<String> siteSettings(@Param("key") String key);
}
