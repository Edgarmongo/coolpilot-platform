package com.coolskyai.modules.topic.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.topic.dto.TopicSaveDTO;
import com.coolskyai.modules.topic.entity.Topic;
import com.coolskyai.modules.topic.mapper.TopicMapper;
import com.coolskyai.modules.topic.vo.TopicArticle;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 专题（系列文章合集）
 *
 * 专题本身没有复杂状态机，重点在两处：
 *   1. slug 唯一且可读 —— 专题就是要被分享的，链接必须干净
 *   2. 绑定顺序即阅读顺序 —— 「从第一篇读到最后一篇」是这个功能存在的理由
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class TopicService {

	private static final String SLUG_PATTERN = "^[A-Za-z0-9_-]{1,120}$";

	@Autowired
	private TopicMapper topicMapper;

	// ---------------- 前台 ----------------

	public List<Topic> listPublic() {
		return topicMapper.listPublished();
	}

	/**
	 * 专题详情：只带已发布文章。
	 *
	 * 未发布的文章不出现，即使编辑把它排进了专题 ——
	 * 专题页是公开页面，不能因为绑定关系把草稿泄露出去。
	 */
	public Map<String, Object> detailBySlug(String slug) {
		Topic topic = topicMapper.findPublishedBySlug(slug);
		if (topic == null) {
			throw new BadRequestException("专题不存在或未发布");
		}
		List<TopicArticle> articles = topicMapper.listArticles(topic.getId(), true);
		Map<String, Object> data = new HashMap<>(4);
		data.put("topic", topic);
		data.put("articles", articles);
		return data;
	}

	/**
	 * 文章所属的已发布专题，用于文章页的「本篇属于」入口
	 */
	public List<Topic> topicsOfBlog(Long blogId) {
		return topicMapper.listByBlogId(blogId);
	}

	// ---------------- 后台 ----------------

	public PageResult<Topic> pageAdmin(Boolean published, String keyword, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
		int total = topicMapper.countAdmin(published, keyword);
		List<Topic> list = topicMapper.pageAdmin(published, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public Map<String, Object> adminDetail(Long id) {
		Topic topic = topicMapper.findById(id);
		if (topic == null) {
			throw new BadRequestException("专题不存在");
		}
		Map<String, Object> data = new HashMap<>(4);
		data.put("topic", topic);
		// 后台要看到全部（含草稿），否则编辑改完顺序一保存就会把草稿踢出去
		data.put("articles", topicMapper.listArticles(id, false));
		data.put("articleIds", topicMapper.listArticleIds(id));
		return data;
	}

	@Transactional(rollbackFor = Exception.class)
	public Long create(TopicSaveDTO dto) {
		Topic topic = build(dto, null);
		topicMapper.insert(topic);
		if (dto.getArticleIds() != null) {
			bind(topic.getId(), dto.getArticleIds());
		}
		return topic.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void update(Long id, TopicSaveDTO dto) {
		Topic existing = topicMapper.findById(id);
		if (existing == null) {
			throw new BadRequestException("专题不存在");
		}
		Topic topic = build(dto, id);
		topic.setId(id);
		topicMapper.update(topic);
		// null 表示「不改绑定」，空数组才是「清空」
		if (dto.getArticleIds() != null) {
			bind(id, dto.getArticleIds());
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void bindArticles(Long id, List<Long> articleIds) {
		if (topicMapper.findById(id) == null) {
			throw new BadRequestException("专题不存在");
		}
		bind(id, articleIds);
	}

	@Transactional(rollbackFor = Exception.class)
	public void delete(Long id) {
		if (topicMapper.findById(id) == null) {
			throw new BadRequestException("专题不存在");
		}
		topicMapper.deleteArticles(id);
		topicMapper.deleteById(id);
	}

	public void changePublished(Long id, Boolean published) {
		if (topicMapper.findById(id) == null) {
			throw new BadRequestException("专题不存在");
		}
		topicMapper.updatePublished(id, Boolean.TRUE.equals(published));
	}

	// ---------------- 内部 ----------------

	private void bind(Long topicId, List<Long> articleIds) {
		topicMapper.deleteArticles(topicId);
		if (articleIds == null || articleIds.isEmpty()) {
			return;
		}
		int sort = 0;
		Set<Long> seen = new LinkedHashSet<>();
		for (Long articleId : articleIds) {
			if (articleId == null || !seen.add(articleId)) {
				continue;
			}
			if (topicMapper.countBlog(articleId) == 0) {
				// 文章被删了但前端还带着旧 id，跳过比整单失败更合理
				continue;
			}
			topicMapper.insertArticle(topicId, articleId, sort++);
		}
	}

	private Topic build(TopicSaveDTO dto, Long selfId) {
		if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
			throw new BadRequestException("专题名称不能为空");
		}
		String title = dto.getTitle().trim();
		if (title.length() > 120) {
			throw new BadRequestException("专题名称不能超过 120 个字符");
		}

		String slug = dto.getSlug() == null ? "" : dto.getSlug().trim();
		if (slug.isEmpty()) {
			/*
			 * 留空时自动生成：中文标题没法直接转成合规 slug（不做拼音转换），
			 * 但也不能因为没填 slug 就不让建专题，否则编辑每建一个都要手编一个英文串。
			 */
			slug = generateSlug();
		}
		if (!slug.matches(SLUG_PATTERN)) {
			throw new BadRequestException("slug 只能使用字母、数字、连字符和下划线，且不超过 120 个字符");
		}
		Topic exist = topicMapper.findBySlug(slug);
		if (exist != null && (selfId == null || !exist.getId().equals(selfId))) {
			throw new BadRequestException("slug 已被《" + exist.getTitle() + "》占用，请换一个");
		}

		Topic topic = new Topic();
		topic.setSlug(slug);
		topic.setTitle(title);
		topic.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
		topic.setCover(dto.getCover() == null ? "" : dto.getCover().trim());
		topic.setIsPublished(dto.getPublished() == null || dto.getPublished());
		topic.setSort(dto.getSort() == null ? 0 : dto.getSort());
		return topic;
	}

	/**
	 * 生成 topic-xxxxxxxx 形式的兜底 slug，冲突时重试
	 */
	private String generateSlug() {
		for (int i = 0; i < 5; i++) {
			String slug = "topic-" + Long.toString(System.nanoTime(), 36);
			if (topicMapper.findBySlug(slug) == null) {
				return slug;
			}
		}
		return "topic-" + System.currentTimeMillis();
	}
}
