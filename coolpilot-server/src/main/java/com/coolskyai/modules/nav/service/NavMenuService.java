package com.coolskyai.modules.nav.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.modules.nav.dto.NavMenuSaveDTO;
import com.coolskyai.modules.nav.entity.NavMenu;
import com.coolskyai.modules.nav.mapper.NavMenuMapper;
import com.coolskyai.service.RedisService;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 前台导航菜单
 *
 * 三件事值得说明：
 *   1. 读走 Redis。导航在每一次 SSR 渲染里都要用，直接查库等于把
 *      「每个页面请求都多一次 SQL」变成常态；菜单变动极少，缓存收益很高。
 *   2. 任何写操作都清缓存，不做局部更新 —— 菜单就十几行，重建比算差异更不容易错。
 *   3. sort 允许重复。站长手填相同数字时按 id 兜底排序，而不是报错拦下来；
 *      真需要精确顺序时用上移/下移，那条路径会先把 sort 规整成 10/20/30。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class NavMenuService {

	private static final int LABEL_MAX = 20;
	private static final int PATH_MAX = 255;
	private static final int SORT_STEP = 10;

	@Autowired
	private NavMenuMapper navMenuMapper;
	@Autowired
	private RedisService redisService;

	// ---------------- 前台 ----------------

	public List<NavMenu> listPublic() {
		List<NavMenu> cached = redisService.getListByValue(RedisKeyConstants.NAV_MENU_LIST);
		if (cached != null) {
			return cached;
		}
		List<NavMenu> menus = navMenuMapper.listVisible();
		redisService.saveListToValue(RedisKeyConstants.NAV_MENU_LIST, menus);
		return menus;
	}

	// ---------------- 后台 ----------------

	public List<NavMenu> listAll() {
		return navMenuMapper.listAll();
	}

	@Transactional(rollbackFor = Exception.class)
	public Long create(NavMenuSaveDTO dto) {
		NavMenu menu = new NavMenu();
		menu.setLabel(normalizeLabel(dto.getLabel()));
		menu.setPath(normalizePath(dto.getPath()));
		menu.setTarget(normalizeTarget(dto.getTarget()));
		menu.setVisible(dto.getVisible() == null || dto.getVisible());
		menu.setSort(dto.getSort() != null ? dto.getSort() : nextSort());
		navMenuMapper.insert(menu);
		evict();
		return menu.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void update(Long id, NavMenuSaveDTO dto) {
		NavMenu exists = navMenuMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("菜单不存在或已删除");
		}
		exists.setLabel(normalizeLabel(dto.getLabel()));
		exists.setPath(normalizePath(dto.getPath()));
		exists.setTarget(normalizeTarget(dto.getTarget()));
		if (dto.getVisible() != null) {
			exists.setVisible(dto.getVisible());
		}
		if (dto.getSort() != null) {
			exists.setSort(dto.getSort());
		}
		navMenuMapper.update(exists);
		evict();
	}

	/**
	 * 上移/下移：与相邻项交换 sort。
	 *
	 * 先把整张表的 sort 规整成 10/20/30（重复或全 0 时也能正确交换），
	 * 再交换目标两项。这样站长不用理解 sort 数值，点两下就行。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void move(Long id, String direction) {
		boolean up = "up".equalsIgnoreCase(direction);
		List<NavMenu> menus = new ArrayList<>(navMenuMapper.listAll());
		int index = -1;
		for (int i = 0; i < menus.size(); i++) {
			if (menus.get(i).getId().equals(id)) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			throw new BadRequestException("菜单不存在或已删除");
		}
		int target = up ? index - 1 : index + 1;
		if (target < 0 || target >= menus.size()) {
			return;
		}
		// 规整：把当前位置顺序固化下来，避免 sort 重复导致「点了没反应」
		for (int i = 0; i < menus.size(); i++) {
			NavMenu menu = menus.get(i);
			int expected = (i + 1) * SORT_STEP;
			if (menu.getSort() == null || menu.getSort() != expected) {
				navMenuMapper.updateSort(menu.getId(), expected);
				menu.setSort(expected);
			}
		}
		NavMenu current = menus.get(index);
		NavMenu neighbour = menus.get(target);
		navMenuMapper.updateSort(current.getId(), neighbour.getSort());
		navMenuMapper.updateSort(neighbour.getId(), current.getSort());
		evict();
	}

	@Transactional(rollbackFor = Exception.class)
	public void changeVisible(Long id, Boolean visible) {
		NavMenu exists = navMenuMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("菜单不存在或已删除");
		}
		navMenuMapper.updateVisible(id, visible != null && visible);
		evict();
	}

	@Transactional(rollbackFor = Exception.class)
	public void delete(Long id) {
		NavMenu exists = navMenuMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("菜单不存在或已删除");
		}
		navMenuMapper.delete(id);
		evict();
	}

	// ---------------- 内部 ----------------

	private int nextSort() {
		List<NavMenu> menus = navMenuMapper.listAll();
		int max = 0;
		for (NavMenu menu : menus) {
			if (menu.getSort() != null && menu.getSort() > max) {
				max = menu.getSort();
			}
		}
		return max + SORT_STEP;
	}

	private String normalizeLabel(String raw) {
		String label = raw == null ? "" : raw.trim();
		if (label.isEmpty()) {
			throw new BadRequestException("菜单文字不能为空");
		}
		if (label.length() > LABEL_MAX) {
			throw new BadRequestException("菜单文字最多 " + LABEL_MAX + " 个字");
		}
		return label;
	}

	/**
	 * 站内路径必须以 / 开头，外链必须 http(s):// 开头。
	 *
	 * 不校验「路径是否存在对应页面」—— 菜单可以先于页面配好（比如先挂外链），
	 * 强行校验只会让站长多一步改代码。
	 */
	private String normalizePath(String raw) {
		String path = raw == null ? "" : raw.trim();
		if (path.isEmpty()) {
			throw new BadRequestException("链接不能为空");
		}
		if (path.length() > PATH_MAX) {
			throw new BadRequestException("链接过长（最多 " + PATH_MAX + " 个字符）");
		}
		boolean external = path.startsWith("http://") || path.startsWith("https://");
		if (!external && !path.startsWith("/")) {
			throw new BadRequestException("站内链接请以 / 开头，外链请以 http:// 或 https:// 开头");
		}
		if (path.matches(".*\\s.*")) {
			throw new BadRequestException("链接中不能包含空格");
		}
		return path;
	}

	private String normalizeTarget(String raw) {
		return "_blank".equalsIgnoreCase(raw) ? "_blank" : "_self";
	}

	private void evict() {
		redisService.deleteCacheByKey(RedisKeyConstants.NAV_MENU_LIST);
	}
}
