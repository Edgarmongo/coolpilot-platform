package com.coolskyai.modules.brand.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.entity.SiteSetting;
import com.coolskyai.mapper.SiteSettingMapper;
import com.coolskyai.service.RedisService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 站点品牌信息（前台顶栏与页脚）
 *
 * 数据仍在 site_setting 里（type = 1），这里只做「按需投影」：
 * 前台只需要五个字段，没必要把 /site 那套站点信息 + 文章 + 分类全拉一遍。
 *
 * 两条刻意的规则：
 *   1. 每一项都有默认值。配置行被误删时页面不该出现空白标题，
 *      默认值与改造前写死的文案完全一致，行为不会突变。
 *   2. 读走 Redis。顶栏页脚每个页面都要渲染，而且基本不变；
 *      后台在「站点设置」保存时会一并清掉这个缓存（见 SiteSettingServiceImpl）。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class BrandService {

	private static final String DEFAULT_MARK = "CP";
	private static final String DEFAULT_NAME = "CoolSkyAI";
	private static final String DEFAULT_FOOTER_TEXT = "CoolSkyAI 技术平台";
	private static final String DEFAULT_FOOTER_TAGLINE = "技术知识与服务";

	@Autowired
	private SiteSettingMapper siteSettingMapper;
	@Autowired
	private RedisService redisService;

	public Map<String, String> get() {
		Map<String, String> cached = redisService.getMapByValue(RedisKeyConstants.BRAND_INFO_MAP);
		if (cached != null) {
			return cached;
		}
		Map<String, String> values = new LinkedHashMap<>();
		List<SiteSetting> settings = siteSettingMapper.getList();
		for (SiteSetting setting : settings) {
			// 只认基础设置组，避免和资料卡里的 name / email 这类同名字段串味
			if (setting.getType() != null && setting.getType() == 1) {
				values.put(setting.getNameEn(), setting.getValue());
			}
		}
		Map<String, String> brand = new LinkedHashMap<>(8);
		// 标记与页脚允许「配置了但留空」= 主动不显示；只有整行缺失才用默认值。
		// 品牌名不同：空白的站名没有任何意义，一律回落到默认值。
		brand.put("brandMark", configured(values, "brandMark", DEFAULT_MARK));
		brand.put("brandName", orDefault(values.get("blogName"), DEFAULT_NAME));
		brand.put("footerText", configured(values, "footerText", DEFAULT_FOOTER_TEXT));
		brand.put("footerTagline", configured(values, "footerTagline", DEFAULT_FOOTER_TAGLINE));
		// 备案号为空是常态（本地开发、海外部署），不做默认值，前台按非空才渲染
		brand.put("beian", values.get("beian") == null ? "" : values.get("beian").trim());
		redisService.saveMapToValue(RedisKeyConstants.BRAND_INFO_MAP, brand);
		return brand;
	}

	/**
	 * 配置存在就用配置（哪怕是空串），配置整行不存在才用默认值。
	 *
	 * 两者的区别很实际：站长把「页脚副文案」清空，是想让它不显示；
	 * 而迁移没跑到、有人删了这行配置时，页脚不该突然空一块。
	 */
	private String configured(Map<String, String> values, String key, String fallback) {
		if (!values.containsKey(key)) {
			return fallback;
		}
		String value = values.get(key);
		return value == null ? "" : value.trim();
	}

	private String orDefault(String value, String fallback) {
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		return value.trim();
	}
}
