package com.coolskyai.modules.planet.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.coolskyai.entity.SiteSetting;
import com.coolskyai.mapper.SiteSettingMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 知识星球介绍页配置（CP-WEB-03）
 *
 * 只做三件事：读 type=6 的 6 个配置项、按 name_en 归类、判断是否启用。
 *
 * 几个决定：
 *   1. 不缓存。6 行一次主键顺序扫描，比维护「保存配置时要记得清缓存」可靠；
 *      真到了每页都渲染的程度再加。
 *   2. 未启用时返回 enabled=false 而不是 404 —— 前台要显示「尚未开放」，
 *      返回 404 会让访客以为站点坏了。
 *   3. 只暴露展示字段，不含任何后台配置项（本例也没有敏感字段，
 *      但保持这个习惯，避免以后有人往 type=6 里塞内部字段）。
 *
 * 明确边界：这里不涉及会员、支付、内容权限。交易与成员管理全部由知识星球
 * 官方完成，本站只做介绍与跳转。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class PlanetService {

	private static final int TYPE_PLANET = 6;

	@Autowired
	private SiteSettingMapper siteSettingMapper;

	public Map<String, Object> get(String lang) {
		boolean en = "en".equalsIgnoreCase(lang);
		List<SiteSetting> items = siteSettingMapper.listByType(TYPE_PLANET);

		Map<String, Object> data = new HashMap<>(8);
		data.put("enabled", false);
		data.put("name", "");
		data.put("cover", "");
		data.put("intro", "");
		data.put("link", "");
		data.put("qr", "");

		boolean hasContent = false;
		for (SiteSetting item : items) {
			String key = item.getNameEn();
			if (key == null) {
				continue;
			}
			String value = en && item.getValueEn() != null && !item.getValueEn().trim().isEmpty()
					? item.getValueEn().trim()
					: item.getValue() == null ? "" : item.getValue().trim();
			switch (key) {
				case "planetName" -> {
					data.put("name", value);
					hasContent |= !value.isEmpty();
				}
				case "planetCover" -> data.put("cover", value);
				case "planetIntro" -> data.put("intro", value);
				case "planetLink" -> {
					data.put("link", value);
					hasContent |= !value.isEmpty();
				}
				case "planetQr" -> data.put("qr", value);
				case "planetEnabled" -> data.put("enabled", "1".equals(value) || "true".equalsIgnoreCase(value));
				default -> {
					// 未知配置项忽略：以后加字段不会影响这里
				}
			}
		}

		// 开关打开但名称和链接都是空的，说明站长还没填完，按未开放处理，
		// 避免前台出现一个空白的星球页
		if (!hasContent) {
			data.put("enabled", false);
		}
		return data;
	}
}
