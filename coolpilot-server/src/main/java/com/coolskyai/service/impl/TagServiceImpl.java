package com.coolskyai.service.impl;

import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.entity.Tag;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.TagMapper;
import com.coolskyai.service.RedisService;
import com.coolskyai.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 标签业务。
 *
 * <p>标签云是纯展示数据，命中率高，因此单独缓存 {@code TAG_CLOUD_LIST}；
 * 文章详情页按 blogId 取标签则直接回源，不值得为它维护一份缓存。
 *
 * @author CoolSkyAI
 */
@Service
public class TagServiceImpl implements TagService {

	private final TagMapper tagMapper;
	private final RedisService redisService;

	public TagServiceImpl(TagMapper tagMapper, RedisService redisService) {
		this.tagMapper = tagMapper;
		this.redisService = redisService;
	}

	@Override
	public List<Tag> getTagList() {
		return tagMapper.getTagList();
	}

	/**
	 * 标签云数据。缓存未命中时回源并写回。
	 */
	@Override
	public List<Tag> getTagListNotId() {
		return redisService.cacheThrough(
				RedisKeyConstants.TAG_CLOUD_LIST,
				tagMapper::getTagListNotId);
	}

	@Override
	public List<Tag> getTagListByBlogId(Long blogId) {
		return tagMapper.getTagListByBlogId(blogId);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveTag(Tag tag) {
		requireAffected(tagMapper.saveTag(tag), "标签添加失败");
		evict(RedisKeyConstants.TAG_CLOUD_LIST);
	}

	@Override
	public Tag getTagById(Long id) {
		Tag tag = tagMapper.getTagById(id);
		if (tag == null) {
			throw new NotFoundException("标签不存在");
		}
		return tag;
	}

	@Override
	public Tag getTagByName(String name) {
		return tagMapper.getTagByName(name);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteTagById(Long id) {
		requireAffected(tagMapper.deleteTagById(id), "标签删除失败");
		// 删标签会影响首页文章卡上的标签展示，首页缓存一并清掉
		evict(RedisKeyConstants.TAG_CLOUD_LIST, RedisKeyConstants.HOME_BLOG_INFO_LIST);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateTag(Tag tag) {
		requireAffected(tagMapper.updateTag(tag), "标签更新失败");
		// 标签名与颜色都会出现在首页，两个缓存都要失效
		evict(RedisKeyConstants.TAG_CLOUD_LIST, RedisKeyConstants.HOME_BLOG_INFO_LIST);
	}

	/**
	 * 影响行数不为 1 时视为失败并回滚。0 行通常意味着目标记录已不存在，
	 * 静默返回成功会让后台以为改成功了。
	 */
	private void requireAffected(int rows, String message) {
		if (rows != 1) {
			throw new PersistenceException(message);
		}
	}

	private void evict(String... keys) {
		for (String key : keys) {
			redisService.deleteCacheByKey(key);
		}
	}
}
