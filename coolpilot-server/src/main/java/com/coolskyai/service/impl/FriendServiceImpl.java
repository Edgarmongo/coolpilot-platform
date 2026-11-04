package com.coolskyai.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.entity.Friend;
import com.coolskyai.entity.SiteSetting;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.FriendMapper;
import com.coolskyai.mapper.SiteSettingMapper;
import com.coolskyai.model.vo.FriendInfo;
import com.coolskyai.service.FriendService;
import com.coolskyai.service.RedisService;
import com.coolskyai.util.markdown.MarkdownUtils;

import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * @Description: 友链业务层实现
 * @Author: CoolSkyAI
 * @Date: 2020-09-08
 */
@Service
public class FriendServiceImpl implements FriendService {
	@Autowired
	FriendMapper friendMapper;
	@Autowired
	SiteSettingMapper siteSettingMapper;
	@Autowired
	RedisService redisService;
	@Autowired
	org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

	@Override
	public List<Friend> getFriendList() {
		return friendMapper.getFriendList();
	}

	@Override
	public List<com.coolskyai.model.vo.Friend> getFriendVOList() {
		return friendMapper.getFriendVOList();
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateFriendPublishedById(Long friendId, Boolean published) {
		if (friendMapper.updateFriendPublishedById(friendId, published) != 1) {
			throw new PersistenceException("操作失败");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveFriend(Friend friend) {
		friend.setViews(0);
		friend.setCreateTime(new Date());
		if (friendMapper.saveFriend(friend) != 1) {
			throw new PersistenceException("添加失败");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateFriend(com.coolskyai.model.dto.Friend friend) {
		if (friendMapper.updateFriend(friend) != 1) {
			throw new PersistenceException("修改失败");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteFriend(Long id) {
		if (friendMapper.deleteFriend(id) != 1) {
			throw new PersistenceException("删除失败");
		}
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateViewsByNickname(String nickname) {
		if (friendMapper.updateViewsByNickname(nickname) != 1) {
			throw new PersistenceException("操作失败");
		}
	}

	/**
	 * 友链浏览计数（R-09）
	 *
	 * 原实现匿名 POST 一次就 +1，随便写个脚本就能把榜单刷歪。
	 * 这里按「昵称 + IP」做 1 小时去重，并且先确认昵称确实存在再写去重键——
	 * 否则攻击者用随机昵称就能无限占用 Redis 键空间。
	 */
	@Override
	public boolean addViewsOnce(String nickname, String ip) {
		if (nickname == null || nickname.trim().isEmpty() || nickname.length() > 50) {
			return false;
		}
		String name = nickname.trim();
		if (friendMapper.countByNickname(name) == 0) {
			return false;
		}
		String key = RedisKeyConstants.FRIEND_VIEW_DEDUPE + name + ":" + (ip == null ? "unknown" : ip);
		Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(1));
		if (!Boolean.TRUE.equals(first)) {
			return false;
		}
		return friendMapper.updateViewsByNickname(name) == 1;
	}

	@Override
	public FriendInfo getFriendInfo(boolean cache, boolean md) {
		String redisKey = RedisKeyConstants.FRIEND_INFO_MAP;
		if (cache) {
			FriendInfo friendInfoFromRedis = redisService.getObjectByValue(redisKey, FriendInfo.class);
			if (friendInfoFromRedis != null) {
				return friendInfoFromRedis;
			}
		}
		List<SiteSetting> siteSettings = siteSettingMapper.getFriendInfo();
		FriendInfo friendInfo = new FriendInfo();
		for (SiteSetting siteSetting : siteSettings) {
			if ("friendContent".equals(siteSetting.getNameEn())) {
				if (md) {
					friendInfo.setContent(MarkdownUtils.markdownToHtmlExtensions(siteSetting.getValue()));
				} else {
					friendInfo.setContent(siteSetting.getValue());
				}
			} else if ("friendCommentEnabled".equals(siteSetting.getNameEn())) {
				if ("1".equals(siteSetting.getValue())) {
					friendInfo.setCommentEnabled(true);
				} else {
					friendInfo.setCommentEnabled(false);
				}
			}
		}
		if (cache && md) {
			redisService.saveObjectToValue(redisKey, friendInfo);
		}
		return friendInfo;
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateFriendInfoContent(String content) {
		if (siteSettingMapper.updateFriendInfoContent(content) != 1) {
			throw new PersistenceException("修改失败");
		}
		deleteFriendInfoRedisCache();
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateFriendInfoCommentEnabled(Boolean commentEnabled) {
		if (siteSettingMapper.updateFriendInfoCommentEnabled(commentEnabled) != 1) {
			throw new PersistenceException("修改失败");
		}
		deleteFriendInfoRedisCache();
	}

	/**
	 * 删除友链页面缓存
	 */
	private void deleteFriendInfoRedisCache() {
		redisService.deleteCacheByKey(RedisKeyConstants.FRIEND_INFO_MAP);
	}
}
