package com.coolskyai.modules.opensource.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.opensource.dto.OssProjectDTO;
import com.coolskyai.modules.opensource.entity.OssProject;
import com.coolskyai.modules.opensource.enums.OssCategory;
import com.coolskyai.modules.opensource.enums.OssVerdict;
import com.coolskyai.modules.opensource.mapper.OssProjectMapper;
import com.coolskyai.modules.opensource.vo.OssArticleItem;
import com.coolskyai.modules.opensource.vo.OssProjectCard;
import com.coolskyai.modules.opensource.vo.OssProjectDetail;
import com.coolskyai.util.StringUtils;
import com.coolskyai.util.markdown.MarkdownUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 开源项目商业观察：业务层。
 *
 * 三条贯穿全类的规则，改代码前先看：
 *
 * 1. **公开接口只认 publish_status = 'published'**。
 *    mapper 里没有任何「把状态当参数传」的公开方法，就是为了让这条规则无法被绕过。
 *
 * 2. **归档 ≠ 删除**。归档后档案 404，但关联文章与关联记录都保留 ——
 *    文章本身仍然可以通过 /blog/{slug} 打开，历史链接不会断。
 *
 * 3. **评估卡是人工填的 JSON，解析失败不能拖垮整页**。
 *    解析不了就把 assessment 置空并标记 invalid，档案页其余部分照常渲染。
 */
@Service
public class OssProjectService {

	private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,179}$");
	private static final Set<String> RESEARCH_STATUS = Set.of("todo", "researching", "done");
	private static final Set<String> PUBLISH_STATUS = Set.of("draft", "published", "archived");
	private static final Set<String> RELATION_TYPES = Set.of("business", "research", "practice", "compare");

	private static final int DEFAULT_PAGE_SIZE = 12;

	@Autowired
	private OssProjectMapper mapper;

	// ============================================================
	// 前台
	// ============================================================

	public PageResult<OssProjectCard> pageProjects(String category, String tag, String keyword,
	                                               Boolean featured, String sort,
	                                               Integer pageNum, Integer pageSize) {
		String cat = normalizeCategory(category);
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 50);
		int total = mapper.countPublished(cat, tag, keyword, featured);
		List<OssProjectCard> list = mapper.pagePublished(cat, tag, keyword, featured, sort,
				(page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 项目档案。
	 *
	 * 未发布 / 已归档一律 404，不返回「存在但不可见」—— 后者会泄露项目名与状态。
	 */
	public OssProjectDetail detail(String slug) {
		OssProject project = mapper.findPublishedBySlug(slug);
		if (project == null) {
			throw new NotFoundException("项目不存在或未公开");
		}
		OssProjectDetail detail = new OssProjectDetail();
		detail.setProject(toCard(project));
		detail.setRepoUrl(project.getRepoUrl());
		detail.setOfficialUrl(project.getOfficialUrl());
		detail.setDocsUrl(project.getDocsUrl());
		// 与文章、作品集一致：description 存 Markdown，这里统一转 HTML
		detail.setContentHtml(MarkdownUtils.markdownToHtmlExtensions(
				project.getDescription() == null ? "" : project.getDescription()));
		detail.setArticles(mapper.listArticlesOfProject(project.getId()));
		detail.setRelated(mapper.listRelated(project.getId(), 3));
		// 评估卡原文透传：解析与兜底展示交给页面（见 OssProjectDetail 注释）
		detail.setAssessmentJson(project.getAssessmentJson());
		return detail;
	}

	/**
	 * 某个项目的关联文章。
	 *
	 * 单独开接口而不是复用详情：文章页的「所属项目」只需要项目名，
	 * 而专题导读、系列导航可能需要文章列表但不需要正文，没必要每次都转一遍 Markdown。
	 */
	public List<OssArticleItem> articlesOfSlug(String slug) {
		OssProject project = mapper.findPublishedBySlug(slug);
		if (project == null) {
			throw new NotFoundException("项目不存在或未公开");
		}
		return mapper.listArticlesOfProject(project.getId());
	}

	public PageResult<OssArticleItem> pageArticles(String category, String tag, String keyword,
	                                               Integer pageNum, Integer pageSize) {
		String cat = normalizeCategory(category);
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 50);
		int total = mapper.countArticles(cat, tag, keyword);
		List<OssArticleItem> list = mapper.pageArticles(cat, tag, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 领域列表：5 个枚举全部返回，没有项目的也要返回 0。
	 *
	 * 为什么补齐 0：前端要画 5 个筛选入口，缺一个就会让「这个领域不存在」和
	 * 「这个领域暂无项目」看起来一样。
	 */
	public List<Map<String, Object>> categories() {
		Map<String, Integer> counts = new HashMap<>();
		for (Map<String, Object> row : mapper.countByCategory()) {
			Object code = row.get("code");
			Object total = row.get("total");
			if (code != null) {
				counts.put(String.valueOf(code), total == null ? 0 : ((Number) total).intValue());
			}
		}
		List<Map<String, Object>> list = new ArrayList<>();
		for (OssCategory item : OssCategory.values()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("code", item.getCode());
			row.put("labelZh", item.getLabelZh());
			row.put("labelEn", item.getLabelEn());
			row.put("count", counts.getOrDefault(item.getCode(), 0));
			list.add(row);
		}
		return list;
	}

	public List<Map<String, Object>> tags() {
		return mapper.listTags();
	}

	/**
	 * 精选：featured 项目的 business 文章；没有 business 文章就退回项目本身。
	 *
	 * 不用 blog.is_recommend —— 那个字段已被站内「随机推荐文章」占用，
	 * 混用会让栏目文章出现在全站随机推荐里。
	 */
	public Map<String, Object> featured() {
		OssProjectCard project = mapper.findFeaturedProject();
		if (project == null) {
			// 没有标记精选时退回「排序最前的一个」，避免首页精选区域空着
			List<OssProjectCard> list = mapper.pagePublished(null, null, null, null, "default", 0, 1);
			project = list.isEmpty() ? null : list.get(0);
		}
		Map<String, Object> data = new HashMap<>(4);
		data.put("project", project);
		data.put("article", project == null ? null
				: mapper.findLatestArticleOfProject(project.getId(), "business"));
		return data;
	}

	/**
	 * 栏目内搜索：项目与文章两组。
	 * 不做分词、不做全文索引 —— 栏目规模在百级，LIKE 足够且结果可预期。
	 */
	public Map<String, Object> search(String keyword) {
		Map<String, Object> data = new HashMap<>(4);
		if (StringUtils.isEmpty(keyword)) {
			data.put("projects", new ArrayList<>());
			data.put("articles", new ArrayList<>());
			return data;
		}
		data.put("projects", mapper.pagePublished(null, null, keyword, null, "default", 0, 10));
		data.put("articles", mapper.pageArticles(null, null, keyword, 0, 10));
		return data;
	}

	/** 文章页用：这篇属于哪个已发布项目 */
	public OssArticleItem.ProjectBrief projectOfBlog(Long blogId) {
		return mapper.findProjectOfBlog(blogId);
	}

	// ============================================================
	// 后台
	// ============================================================

	public PageResult<OssProject> pageAdmin(String publishStatus, String researchStatus,
	                                        String category, String keyword,
	                                        Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
		int total = mapper.countAdmin(publishStatus, researchStatus, category, keyword);
		List<OssProject> list = mapper.pageAdmin(publishStatus, researchStatus, category, keyword,
				(page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public Map<String, Object> adminDetail(Long id) {
		OssProject project = mapper.findById(id);
		if (project == null) {
			throw new NotFoundException("项目不存在");
		}
		Map<String, Object> data = new HashMap<>(4);
		data.put("project", project);
		data.put("tagIds", mapper.listTagIds(id));
		data.put("articles", mapper.listAllArticlesOfProject(id));
		return data;
	}

	@Transactional(rollbackFor = Exception.class)
	public Long create(OssProjectDTO dto) {
		OssProject project = toEntity(dto);
		if (project.getId() != null) {
			throw new BadRequestException("新建时不能带 id");
		}
		checkSlug(project.getSlug(), null);
		mapper.insert(project);
		replaceTags(project.getId(), dto.getTagIds());
		replaceArticles(project.getId(), dto.getArticles());
		return project.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void update(OssProjectDTO dto) {
		if (dto.getId() == null) {
			throw new BadRequestException("缺少 id");
		}
		OssProject exists = mapper.findById(dto.getId());
		if (exists == null) {
			throw new NotFoundException("项目不存在");
		}
		OssProject project = toEntity(dto);
		checkSlug(project.getSlug(), project.getId());
		mapper.update(project);
		replaceTags(project.getId(), dto.getTagIds());
		replaceArticles(project.getId(), dto.getArticles());
	}

	public void publish(Long id) {
		changeStatus(id, "published");
	}

	public void archive(Long id) {
		changeStatus(id, "archived");
	}

	private void changeStatus(Long id, String status) {
		OssProject exists = mapper.findById(id);
		if (exists == null) {
			throw new NotFoundException("项目不存在");
		}
		mapper.updatePublishStatus(id, status);
	}

	// ============================================================
	// 组装与校验
	// ============================================================

	private OssProjectCard toCard(OssProject source) {
		OssProjectCard card = new OssProjectCard();
		card.setId(source.getId());
		card.setSlug(source.getSlug());
		card.setName(source.getName());
		card.setSummary(source.getSummary());
		card.setCategory(source.getCategory());
		card.setLicense(source.getLicense());
		card.setLanguage(source.getLanguage());
		card.setVerdict(source.getVerdict());
		card.setStars(source.getStars());
		card.setCoverUrl(source.getCoverUrl());
		card.setFeatured(source.getFeatured());
		card.setResearchStatus(source.getResearchStatus());
		card.setArticleCount(source.getArticleCount() == null ? 0 : source.getArticleCount());
		card.setLastVerifiedAt(source.getLastVerifiedAt());
		card.setUpdateTime(source.getUpdateTime());
		card.setTagsCsv(source.getTagsCsv());
		return card;
	}

	private String normalizeCategory(String category) {
		if (StringUtils.isEmpty(category) || "all".equals(category)) {
			return null;
		}
		return OssCategory.isValid(category) ? category : null;
	}

	private void checkSlug(String slug, Long selfId) {
		if (StringUtils.isEmpty(slug) || !SLUG_PATTERN.matcher(slug).matches()) {
			throw new BadRequestException("slug 只能是小写字母、数字与短横线，2–180 位");
		}
		OssProject exists = mapper.findBySlugAny(slug);
		if (exists != null && !exists.getId().equals(selfId)) {
			// 明确告诉调用方「撞了哪个项目」，而不是笼统的 500 或唯一键异常
			throw new BadRequestException("slug 已被项目「" + exists.getName() + "」占用");
		}
	}

	private OssProject toEntity(OssProjectDTO dto) {
		if (StringUtils.isEmpty(dto.getName())) {
			throw new BadRequestException("项目名不能为空");
		}
		if (dto.getCategory() != null && !OssCategory.isValid(dto.getCategory())) {
			throw new BadRequestException("领域不在取值范围内");
		}
		if (dto.getVerdict() != null && !dto.getVerdict().isEmpty() && !OssVerdict.isValid(dto.getVerdict())) {
			throw new BadRequestException("结论档不在取值范围内");
		}
		String researchStatus = dto.getResearchStatus() == null ? "todo" : dto.getResearchStatus();
		if (!RESEARCH_STATUS.contains(researchStatus)) {
			throw new BadRequestException("研究状态不在取值范围内");
		}
		String publishStatus = dto.getPublishStatus() == null ? "draft" : dto.getPublishStatus();
		if (!PUBLISH_STATUS.contains(publishStatus)) {
			throw new BadRequestException("发布状态不在取值范围内");
		}

		OssProject project = new OssProject();
		project.setId(dto.getId());
		project.setSlug(dto.getSlug() == null ? null : dto.getSlug().trim());
		project.setName(dto.getName().trim());
		project.setSummary(dto.getSummary());
		project.setDescription(dto.getDescription());
		project.setRepoUrl(dto.getRepoUrl());
		project.setOfficialUrl(dto.getOfficialUrl());
		project.setDocsUrl(dto.getDocsUrl());
		project.setLicense(dto.getLicense());
		project.setLanguage(dto.getLanguage());
		project.setCategory(dto.getCategory() == null ? OssCategory.DEFAULT_CODE : dto.getCategory());
		project.setCoverUrl(dto.getCoverUrl());
		project.setStars(dto.getStars());
		project.setStarsAt(dto.getStarsAt());
		project.setVerdict(dto.getVerdict() == null || dto.getVerdict().isEmpty() ? null : dto.getVerdict());
		project.setAssessmentJson(dto.getAssessmentJson());
		project.setResearchStatus(researchStatus);
		project.setPublishStatus(publishStatus);
		project.setFeatured(dto.getFeatured() != null && dto.getFeatured());
		project.setLastVerifiedAt(dto.getLastVerifiedAt() == null ? new Date() : dto.getLastVerifiedAt());
		project.setSort(dto.getSort() == null ? 0 : dto.getSort());
		return project;
	}

	private void replaceTags(Long projectId, List<Long> tagIds) {
		mapper.deleteTags(projectId);
		if (tagIds == null) {
			return;
		}
		for (Long tagId : new HashSet<>(tagIds)) {
			if (tagId != null) {
				mapper.insertTag(projectId, tagId);
			}
		}
	}

	/**
	 * 全量覆盖关联文章。
	 *
	 * 只允许关联「已发布」的文章：栏目页是公开页面，
	 * 关联了草稿就等于给了它一个可见入口（哪怕正文打不开，标题也已经漏出去了）。
	 */
	private void replaceArticles(Long projectId, List<OssProjectDTO.ArticleBind> articles) {
		mapper.deleteArticles(projectId);
		if (articles == null || articles.isEmpty()) {
			return;
		}
		Set<Long> blogIds = new HashSet<>();
		for (OssProjectDTO.ArticleBind bind : articles) {
			if (bind.getBlogId() != null) {
				blogIds.add(bind.getBlogId());
			}
		}
		if (blogIds.isEmpty()) {
			return;
		}
		List<Long> usable = mapper.filterUsableBlogIds(new ArrayList<>(blogIds));
		if (usable.size() != blogIds.size()) {
			Set<Long> missing = new HashSet<>(blogIds);
			missing.removeAll(usable);
			throw new BadRequestException("以下文章不存在或未发布，不能关联：" + missing);
		}
		int index = 0;
		Set<Long> seen = new HashSet<>();
		for (OssProjectDTO.ArticleBind bind : articles) {
			if (bind.getBlogId() == null || !seen.add(bind.getBlogId())) {
				continue;
			}
			String relation = RELATION_TYPES.contains(bind.getRelationType())
					? bind.getRelationType() : "research";
			int sort = bind.getSort() == null ? index : bind.getSort();
			mapper.insertArticle(projectId, bind.getBlogId(), relation, sort);
			index++;
		}
	}
}
