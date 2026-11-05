package com.coolskyai.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.entity.About;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.AboutMapper;
import com.coolskyai.service.AboutService;
import com.coolskyai.service.RedisService;
import com.coolskyai.util.markdown.MarkdownUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 关于我页面业务层实现
 * @Author: CoolSkyAI
 * @Date: 2020-08-31
 */
@Service
public class AboutServiceImpl implements AboutService {
	@Autowired
	AboutMapper aboutMapper;
	@Autowired
	RedisService redisService;

	@Override
	public Map<String, String> getAboutInfo(String lang) {
		String locale = "en".equalsIgnoreCase(lang) ? "en" : "zh-CN";
		String redisKey = RedisKeyConstants.ABOUT_INFO_MAP + ":" + locale;
		Map<String, String> aboutInfoMapFromRedis = redisService.getMapByValue(redisKey);
		if (aboutInfoMapFromRedis != null) {
			return aboutInfoMapFromRedis;
		}
		List<About> abouts = aboutMapper.getList();
		Map<String, String> raw = new HashMap<>(16);
		for (About about : abouts) {
			raw.put(about.getNameEn(), about.getValue());
		}
		Map<String, String> aboutInfoMap = new HashMap<>(16);
		aboutInfoMap.putAll(raw);
		if ("en".equals(locale)) {
			// 英文稿存在才覆盖；空串视为「没写」，回落中文原文
			putIfPresent(aboutInfoMap, "title", raw.get("titleEn"));
			putIfPresent(aboutInfoMap, "content", raw.get("contentEn"));
		}
		String content = aboutInfoMap.get("content");
		if (content != null && !content.isEmpty()) {
			aboutInfoMap.put("content", MarkdownUtils.markdownToHtmlExtensions(content));
		}
		redisService.saveMapToValue(redisKey, aboutInfoMap);
		return aboutInfoMap;
	}

	private void putIfPresent(Map<String, String> map, String key, String value) {
		if (value != null && !value.trim().isEmpty()) {
			map.put(key, value);
		}
	}

	@Override
	public Map<String, String> getAboutSetting() {
		List<About> abouts = aboutMapper.getList();
		Map<String, String> map = new HashMap<>(16);
		for (About about : abouts) {
			map.put(about.getNameEn(), about.getValue());
		}
		return map;
	}

	@Override
	public void updateAbout(Map<String, String> map) {
		Set<String> keySet = map.keySet();
		for (String key : keySet) {
			updateOneAbout(key, map.get(key));
		}
		deleteAboutRedisCache();
	}

	@Transactional(rollbackFor = Exception.class)
	public void updateOneAbout(String nameEn, String value) {
		if (aboutMapper.updateAbout(nameEn, value) != 1) {
			throw new PersistenceException("修改失败");
		}
	}

	@Override
	public boolean getAboutCommentEnabled() {
		String commentEnabledString = aboutMapper.getAboutCommentEnabled();
		return Boolean.parseBoolean(commentEnabledString);
	}

	/**
	 * 删除关于我页面缓存
	 */
	private void deleteAboutRedisCache() {
		// 两种语言各一份缓存，改一次要同时失效
		for (String locale : new String[]{"zh-CN", "en"}) {
			redisService.deleteCacheByKey(RedisKeyConstants.ABOUT_INFO_MAP + ":" + locale);
		}
	}
}
