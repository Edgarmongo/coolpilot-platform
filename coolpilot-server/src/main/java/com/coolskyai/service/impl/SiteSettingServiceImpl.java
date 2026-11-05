package com.coolskyai.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.constant.SiteSettingConstants;
import com.coolskyai.entity.SiteSetting;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.SiteSettingMapper;
import com.coolskyai.model.vo.Badge;
import com.coolskyai.model.vo.Copyright;
import com.coolskyai.model.vo.Favorite;
import com.coolskyai.model.vo.Introduction;
import com.coolskyai.service.RedisService;
import com.coolskyai.service.SiteSettingService;
import com.coolskyai.util.JacksonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description: 站点设置业务层实现
 * @Author: CoolSkyAI
 * @Date: 2020-08-09
 */
@Service
public class SiteSettingServiceImpl implements SiteSettingService {
	@Autowired
	SiteSettingMapper siteSettingMapper;
	@Autowired
	RedisService redisService;

	private static final Pattern PATTERN = Pattern.compile("\"(.*?)\"");

	/**
	 * 按 type 分组返回，键为 typeN。
	 *
	 * 原先这里是 switch 写死 1/2/3，type 4（友链说明）和 type 5（收款码）
	 * 永远不会返回 —— 后台「站点设置」看不到这两组，收款码实际上改不了，
	 * 与 PaymentService 注释里写的「后台可改」不符。
	 * 改成按库里实际存在的 type 动态分组，以后再新增分组不用改这段代码。
	 */
	@Override
	public Map<String, List<SiteSetting>> getList() {
		List<SiteSetting> siteSettings = siteSettingMapper.getList();
		Map<Integer, List<SiteSetting>> grouped = new TreeMap<>();
		for (SiteSetting s : siteSettings) {
			if (s.getType() == null) {
				continue;
			}
			grouped.computeIfAbsent(s.getType(), key -> new ArrayList<>()).add(s);
		}
		Map<String, List<SiteSetting>> map = new LinkedHashMap<>(8);
		grouped.forEach((type, list) -> map.put("type" + type, list));
		return map;
	}

	@Override
	public Map<String, Object> getSiteInfo() {
		String redisKey = RedisKeyConstants.SITE_INFO_MAP;
		Map<String, Object> siteInfoMapFromRedis = redisService.getMapByValue(redisKey);
		if (siteInfoMapFromRedis != null) {
			return siteInfoMapFromRedis;
		}
		List<SiteSetting> siteSettings = siteSettingMapper.getList();
		Map<String, Object> siteInfo = new HashMap<>(2);
		List<Badge> badges = new ArrayList<>();
		Introduction introduction = new Introduction();
		List<Favorite> favorites = new ArrayList<>();
		List<String> rollTexts = new ArrayList<>();
		for (SiteSetting s : siteSettings) {
			switch (s.getType()) {
				case 1:
					if (SiteSettingConstants.COPYRIGHT.equals(s.getNameEn())) {
						Copyright copyright = JacksonUtils.readValue(s.getValue(), Copyright.class);
						siteInfo.put(s.getNameEn(), copyright);
					} else {
						siteInfo.put(s.getNameEn(), s.getValue());
					}
					break;
				case 2:
					switch (s.getNameEn()) {
						case SiteSettingConstants.AVATAR:
							introduction.setAvatar(s.getValue());
							break;
						case SiteSettingConstants.NAME:
							introduction.setName(s.getValue());
							break;
						case SiteSettingConstants.GITHUB:
							introduction.setGithub(s.getValue());
							break;
						case SiteSettingConstants.TELEGRAM:
							introduction.setTelegram(s.getValue());
							break;
						case SiteSettingConstants.QQ:
							introduction.setQq(s.getValue());
							break;
						case SiteSettingConstants.BILIBILI:
							introduction.setBilibili(s.getValue());
							break;
						case SiteSettingConstants.NETEASE:
							introduction.setNetease(s.getValue());
							break;
						case SiteSettingConstants.EMAIL:
							introduction.setEmail(s.getValue());
							break;
						case SiteSettingConstants.FAVORITE:
							Favorite favorite = JacksonUtils.readValue(s.getValue(), Favorite.class);
							favorites.add(favorite);
							break;
						case SiteSettingConstants.ROLL_TEXT:
							Matcher m = PATTERN.matcher(s.getValue());
							while (m.find()) {
								rollTexts.add(m.group(1));
							}
							break;
						default:
							break;
					}
					break;
				case 3:
					Badge badge = JacksonUtils.readValue(s.getValue(), Badge.class);
					badges.add(badge);
					break;
				default:
					break;
			}
		}
		introduction.setFavorites(favorites);
		introduction.setRollText(rollTexts);
		Map<String, Object> map = new HashMap<>(8);
		map.put("introduction", introduction);
		map.put("siteInfo", siteInfo);
		map.put("badges", badges);
		redisService.saveMapToValue(redisKey, map);
		return map;
	}

	@Override
	public String getWebTitleSuffix() {
		return siteSettingMapper.getWebTitleSuffix();
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateSiteSetting(List<LinkedHashMap> siteSettings, List<Integer> deleteIds) {
		for (Integer id : deleteIds) {
			//删除
			deleteOneSiteSettingById(id);
		}
		for (LinkedHashMap s : siteSettings) {
			SiteSetting siteSetting = JacksonUtils.convertValue(s, SiteSetting.class);
			if (siteSetting.getId() != null) {
				//修改
				updateOneSiteSetting(siteSetting);
			} else {
				//添加
				saveOneSiteSetting(siteSetting);
			}
		}
		deleteSiteInfoRedisCache();
	}

	public void saveOneSiteSetting(SiteSetting siteSetting) {
		if (siteSettingMapper.saveSiteSetting(siteSetting) != 1) {
			throw new PersistenceException("配置添加失败");
		}
	}

	public void updateOneSiteSetting(SiteSetting siteSetting) {
		if (siteSettingMapper.updateSiteSetting(siteSetting) != 1) {
			throw new PersistenceException("配置修改失败");
		}
	}

	public void deleteOneSiteSettingById(Integer id) {
		if (siteSettingMapper.deleteSiteSettingById(id) != 1) {
			throw new PersistenceException("配置删除失败");
		}
	}

	/**
	 * 删除站点信息缓存
	 *
	 * 品牌信息（顶栏/页脚）也是从 site_setting 投影出来的，
	 * 后台一保存就得跟着失效，否则改完站点名前台还是旧字。
	 */
	private void deleteSiteInfoRedisCache() {
		redisService.deleteCacheByKey(RedisKeyConstants.SITE_INFO_MAP);
		// 品牌缓存按语言分了两份，改一次配置两份都要失效
		for (String locale : new String[]{"zh-CN", "en"}) {
			redisService.deleteCacheByKey(RedisKeyConstants.BRAND_INFO_MAP + ":" + locale);
		}
	}
}
