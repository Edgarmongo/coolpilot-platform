package com.coolskyai.modules.portfolio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.modules.portfolio.dto.PortfolioSaveDTO;
import com.coolskyai.modules.portfolio.entity.Portfolio;
import com.coolskyai.modules.portfolio.mapper.PortfolioMapper;
import com.coolskyai.util.markdown.MarkdownUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 项目展示（作品集）
 *
 * 1. 详情正文用 Markdown，渲染走 MarkdownUtils —— 与文章、商品详情同一条链路，
 *    不引入第二套富文本，后台也只需要一个 textarea。
 * 2. 列表不返回 content，避免把几十 KB 正文塞进列表响应。
 * 3. slug 是前台地址的一部分，必须唯一且格式受限；写错会导致
 *    /projects/xxx 打不开，所以在保存时就拦，而不是等前台报错。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class PortfolioService {

	private static final int NAME_MAX = 100;
	private static final int SUMMARY_MAX = 255;
	private static final int URL_MAX = 512;
	private static final int SORT_STEP = 10;

	private static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
			"ACTIVE", "MAINTAINING", "WIP", "ARCHIVED"
	));

	@Autowired
	private PortfolioMapper portfolioMapper;

	// ---------------- 前台 ----------------

	public List<Portfolio> listEnabled() {
		return portfolioMapper.listEnabled();
	}

	/**
	 * @return portfolio 基本信息 + contentHtml 渲染后的正文
	 */
	public Map<String, Object> getBySlug(String slug) {
		Portfolio portfolio = portfolioMapper.findEnabledBySlug(slug);
		if (portfolio == null) {
			throw new NotFoundException("项目不存在或未公开");
		}
		Map<String, Object> data = new HashMap<>(4);
		data.put("portfolio", portfolio);
		data.put("contentHtml", MarkdownUtils.markdownToHtmlExtensions(
				portfolio.getContent() == null ? "" : portfolio.getContent()));
		return data;
	}

	// ---------------- 后台 ----------------

	public List<Portfolio> listAll() {
		return portfolioMapper.listAll();
	}

	public Portfolio getById(Long id) {
		Portfolio portfolio = portfolioMapper.findById(id);
		if (portfolio == null) {
			throw new NotFoundException("项目不存在或已删除");
		}
		return portfolio;
	}

	@Transactional(rollbackFor = Exception.class)
	public Long create(PortfolioSaveDTO dto) {
		Portfolio portfolio = new Portfolio();
		apply(portfolio, dto, null);
		portfolio.setSort(dto.getSort() != null ? dto.getSort() : nextSort());
		portfolioMapper.insert(portfolio);
		return portfolio.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void update(Long id, PortfolioSaveDTO dto) {
		Portfolio exists = portfolioMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("项目不存在或已删除");
		}
		apply(exists, dto, id);
		if (dto.getSort() != null) {
			exists.setSort(dto.getSort());
		}
		portfolioMapper.update(exists);
	}

	@Transactional(rollbackFor = Exception.class)
	public void move(Long id, String direction) {
		boolean up = "up".equalsIgnoreCase(direction);
		List<Portfolio> items = new ArrayList<>(portfolioMapper.listAll());
		int index = -1;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).getId().equals(id)) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			throw new BadRequestException("项目不存在或已删除");
		}
		int target = up ? index - 1 : index + 1;
		if (target < 0 || target >= items.size()) {
			return;
		}
		for (int i = 0; i < items.size(); i++) {
			Portfolio item = items.get(i);
			int expected = (i + 1) * SORT_STEP;
			if (item.getSort() == null || item.getSort() != expected) {
				portfolioMapper.updateSort(item.getId(), expected);
				item.setSort(expected);
			}
		}
		Portfolio current = items.get(index);
		Portfolio neighbour = items.get(target);
		portfolioMapper.updateSort(current.getId(), neighbour.getSort());
		portfolioMapper.updateSort(neighbour.getId(), current.getSort());
	}

	@Transactional(rollbackFor = Exception.class)
	public void changeEnabled(Long id, Boolean enabled) {
		if (portfolioMapper.findById(id) == null) {
			throw new BadRequestException("项目不存在或已删除");
		}
		portfolioMapper.updateEnabled(id, enabled != null && enabled);
	}

	@Transactional(rollbackFor = Exception.class)
	public void delete(Long id) {
		if (portfolioMapper.findById(id) == null) {
			throw new BadRequestException("项目不存在或已删除");
		}
		portfolioMapper.delete(id);
	}

	// ---------------- 内部 ----------------

	private void apply(Portfolio portfolio, PortfolioSaveDTO dto, Long selfId) {
		String slug = dto.getSlug() == null ? "" : dto.getSlug().trim().toLowerCase();
		if (slug.isEmpty()) {
			throw new BadRequestException("前台地址标识（slug）不能为空");
		}
		if (!slug.matches("[a-z0-9][a-z0-9-]{0,62}")) {
			throw new BadRequestException("slug 只能用小写字母、数字和连字符，且以字母或数字开头");
		}
		long excludeId = selfId == null ? -1L : selfId;
		if (portfolioMapper.countBySlug(slug, excludeId) > 0) {
			throw new BadRequestException("slug 已被占用，换一个");
		}
		portfolio.setSlug(slug);

		String name = dto.getName() == null ? "" : dto.getName().trim();
		if (name.isEmpty()) {
			throw new BadRequestException("项目名称不能为空");
		}
		if (name.length() > NAME_MAX) {
			throw new BadRequestException("项目名称最多 " + NAME_MAX + " 个字符");
		}
		portfolio.setName(name);

		String summary = dto.getSummary() == null ? "" : dto.getSummary().trim();
		if (summary.length() > SUMMARY_MAX) {
			throw new BadRequestException("简介最多 " + SUMMARY_MAX + " 个字符");
		}
		portfolio.setSummary(summary);

		portfolio.setCoverUrl(normalizeUrl(dto.getCoverUrl()));
		portfolio.setRepoUrl(normalizeUrl(dto.getRepoUrl()));
		portfolio.setDemoUrl(normalizeUrl(dto.getDemoUrl()));

		String status = dto.getStatus() == null ? "ACTIVE" : dto.getStatus().trim().toUpperCase();
		if (!STATUSES.contains(status)) {
			throw new BadRequestException("不支持的项目状态：" + dto.getStatus());
		}
		portfolio.setStatus(status);

		String tech = dto.getTechStack() == null ? "" : dto.getTechStack().trim();
		if (tech.length() > 255) {
			throw new BadRequestException("技术栈最多 255 个字符");
		}
		portfolio.setTechStack(tech);

		portfolio.setContent(dto.getContent() == null ? "" : dto.getContent());
		portfolio.setEnabled(dto.getEnabled() == null || dto.getEnabled());
	}

	private String normalizeUrl(String raw) {
		String url = raw == null ? "" : raw.trim();
		if (url.isEmpty()) {
			return null;
		}
		if (url.length() > URL_MAX) {
			throw new BadRequestException("链接最多 " + URL_MAX + " 个字符");
		}
		if (!url.startsWith("/") && !url.startsWith("http://") && !url.startsWith("https://")) {
			throw new BadRequestException("链接需以 / 或 http(s):// 开头");
		}
		return url;
	}

	private int nextSort() {
		List<Portfolio> items = portfolioMapper.listAll();
		int max = 0;
		for (Portfolio item : items) {
			if (item.getSort() != null && item.getSort() > max) {
				max = item.getSort();
			}
		}
		return max + SORT_STEP;
	}
}
