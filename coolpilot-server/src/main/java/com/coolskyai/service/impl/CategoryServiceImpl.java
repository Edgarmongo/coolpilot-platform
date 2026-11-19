package com.coolskyai.service.impl;

import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.entity.Category;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.CategoryMapper;
import com.coolskyai.service.CategoryService;
import com.coolskyai.service.RedisService;
import com.coolskyai.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类业务。
 *
 * <p>写操作统一走 {@link #evict(String...)}：分类变了，分类名列表缓存必然过期，
 * 首页文章卡上的分类标签也会跟着错，所以这两个 key 一起清。
 *
 * @author CoolSkyAI
 */
@Service
public class CategoryServiceImpl implements CategoryService {

	private final CategoryMapper categoryMapper;
	private final TagService tagService;
	private final RedisService redisService;

	public CategoryServiceImpl(CategoryMapper categoryMapper, TagService tagService, RedisService redisService) {
		this.categoryMapper = categoryMapper;
		this.tagService = tagService;
		this.redisService = redisService;
	}

	@Override
	public List<Category> getCategoryList() {
		return categoryMapper.getCategoryList();
	}

	/**
	 * 分类名列表读多写少，走缓存。缓存未命中才回源，回源结果立即写回。
	 */
	@Override
	public List<Category> getCategoryNameList() {
		return redisService.cacheThrough(
				RedisKeyConstants.CATEGORY_NAME_LIST,
				categoryMapper::getCategoryNameList);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveCategory(Category category) {
		requireAffected(categoryMapper.saveCategory(category), "分类添加失败");
		evict(RedisKeyConstants.CATEGORY_NAME_LIST);
	}

	@Override
	public Category getCategoryById(Long id) {
		Category category = categoryMapper.getCategoryById(id);
		if (category == null) {
			throw new NotFoundException("分类不存在");
		}
		return category;
	}

	@Override
	public Category getCategoryByName(String name) {
		return categoryMapper.getCategoryByName(name);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteCategoryById(Long id) {
		requireAffected(categoryMapper.deleteCategoryById(id), "删除分类失败");
		// 删除同样会影响首页展示，两个 key 都要清；只清名字列表会让首页残留已删分类
		evict(RedisKeyConstants.CATEGORY_NAME_LIST, RedisKeyConstants.HOME_BLOG_INFO_LIST);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateCategory(Category category) {
		requireAffected(categoryMapper.updateCategory(category), "分类更新失败");
		evict(RedisKeyConstants.CATEGORY_NAME_LIST, RedisKeyConstants.HOME_BLOG_INFO_LIST);
	}

	/**
	 * MyBatis 的写方法返回影响行数。0 行说明目标记录不存在或条件没命中，
	 * 视为失败并回滚 —— 静默成功比报错更难排查。
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
