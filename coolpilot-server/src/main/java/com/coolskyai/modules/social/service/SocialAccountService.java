package com.coolskyai.modules.social.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.modules.social.dto.SocialAccountSaveDTO;
import com.coolskyai.modules.social.entity.SocialAccount;
import com.coolskyai.modules.social.mapper.SocialAccountMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Description: 社交账号（关注我）
 *
 * 几个刻意的决定：
 *   1. 不做缓存。全表只有几行，一次走索引的查询比维护缓存失效逻辑更省心；
 *      导航那种「每页都渲染」的东西才值得缓存，这里只有首页和 /follow 用。
 *   2. platform 用白名单校验。写错一个值（比如 GITHUB 写成 github）前台就会
 *      掉图标，与其让站长自己发现，不如保存时就拦下来。
 *   3. home_url 允许为空 —— 微信群、私人微信这类只有二维码没有主页的账号是
 *      真实存在的，强制填链接只会逼站长填假数据。
 *   4. 排序用「规整成 10/20/30 再交换」，与导航菜单同一套交互，站长不用
 *      理解 sort 数值。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class SocialAccountService {

	private static final int NAME_MAX = 64;
	private static final int INTRO_MAX = 255;
	private static final int URL_MAX = 512;
	private static final int SORT_STEP = 10;

	private static final Set<String> PLATFORMS = new HashSet<>(Arrays.asList(
			"GITHUB", "TELEGRAM", "QQ", "BILIBILI", "NETEASE", "EMAIL",
			"WECHAT", "WECHAT_MP", "DOUYIN", "XIAOHONGSHU", "ZHIHU", "CUSTOM"
	));

	@Autowired
	private SocialAccountMapper socialAccountMapper;

	// ---------------- 前台 ----------------

	public List<SocialAccount> listEnabled() {
		return socialAccountMapper.listEnabled();
	}

	// ---------------- 后台 ----------------

	public List<SocialAccount> listAll() {
		return socialAccountMapper.listAll();
	}

	@Transactional(rollbackFor = Exception.class)
	public Long create(SocialAccountSaveDTO dto) {
		SocialAccount account = new SocialAccount();
		apply(account, dto);
		account.setSort(dto.getSort() != null ? dto.getSort() : nextSort());
		socialAccountMapper.insert(account);
		return account.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void update(Long id, SocialAccountSaveDTO dto) {
		SocialAccount exists = socialAccountMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("账号不存在或已删除");
		}
		apply(exists, dto);
		if (dto.getSort() != null) {
			exists.setSort(dto.getSort());
		}
		socialAccountMapper.update(exists);
	}

	/**
	 * 上移/下移：与相邻项交换 sort。
	 * 先把整表 sort 规整成 10/20/30，避免重复值导致「点了没反应」。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void move(Long id, String direction) {
		boolean up = "up".equalsIgnoreCase(direction);
		List<SocialAccount> accounts = new ArrayList<>(socialAccountMapper.listAll());
		int index = -1;
		for (int i = 0; i < accounts.size(); i++) {
			if (accounts.get(i).getId().equals(id)) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			throw new BadRequestException("账号不存在或已删除");
		}
		int target = up ? index - 1 : index + 1;
		if (target < 0 || target >= accounts.size()) {
			return;
		}
		for (int i = 0; i < accounts.size(); i++) {
			SocialAccount account = accounts.get(i);
			int expected = (i + 1) * SORT_STEP;
			if (account.getSort() == null || account.getSort() != expected) {
				socialAccountMapper.updateSort(account.getId(), expected);
				account.setSort(expected);
			}
		}
		SocialAccount current = accounts.get(index);
		SocialAccount neighbour = accounts.get(target);
		socialAccountMapper.updateSort(current.getId(), neighbour.getSort());
		socialAccountMapper.updateSort(neighbour.getId(), current.getSort());
	}

	@Transactional(rollbackFor = Exception.class)
	public void changeEnabled(Long id, Boolean enabled) {
		SocialAccount exists = socialAccountMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("账号不存在或已删除");
		}
		socialAccountMapper.updateEnabled(id, enabled != null && enabled);
	}

	@Transactional(rollbackFor = Exception.class)
	public void delete(Long id) {
		SocialAccount exists = socialAccountMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("账号不存在或已删除");
		}
		socialAccountMapper.delete(id);
	}

	// ---------------- 内部 ----------------

	private void apply(SocialAccount account, SocialAccountSaveDTO dto) {
		String platform = dto.getPlatform() == null ? "" : dto.getPlatform().trim().toUpperCase();
		if (!PLATFORMS.contains(platform)) {
			throw new BadRequestException("不支持的平台类型：" + dto.getPlatform());
		}
		account.setPlatform(platform);

		String name = dto.getAccountName() == null ? "" : dto.getAccountName().trim();
		if (name.isEmpty()) {
			throw new BadRequestException("账号名称不能为空");
		}
		if (name.length() > NAME_MAX) {
			throw new BadRequestException("账号名称最多 " + NAME_MAX + " 个字符");
		}
		account.setAccountName(name);

		String intro = dto.getIntro() == null ? "" : dto.getIntro().trim();
		if (intro.length() > INTRO_MAX) {
			throw new BadRequestException("简介最多 " + INTRO_MAX + " 个字符");
		}
		account.setIntro(intro);

		account.setHomeUrl(normalizeUrl(dto.getHomeUrl(), true));
		account.setQrUrl(normalizeUrl(dto.getQrUrl(), false));

		String icon = dto.getIcon() == null ? "" : dto.getIcon().trim();
		account.setIcon(icon.isEmpty() ? platform.toLowerCase() : icon);
		account.setEnabled(dto.getEnabled() == null || dto.getEnabled());
	}

	/**
	 * @param isHome 主页链接允许 mailto:（邮箱类账号），图片地址不允许
	 *
	 * 不做「URL 必须有效」的强校验 —— 站内相对路径（/upload/xx.png）也是合法值，
	 * 只挡住明显填错的（比如直接写个「微信」当链接）。
	 */
	private String normalizeUrl(String raw, boolean isHome) {
		String url = raw == null ? "" : raw.trim();
		if (url.isEmpty()) {
			return null;
		}
		if (url.length() > URL_MAX) {
			throw new BadRequestException("链接最多 " + URL_MAX + " 个字符");
		}
		boolean ok = url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://")
				|| (isHome && url.startsWith("mailto:"));
		if (!ok) {
			throw new BadRequestException(isHome
					? "主页链接需以 / 或 http(s):// 或 mailto: 开头"
					: "图片地址需以 / 或 http(s):// 开头");
		}
		return url;
	}

	private int nextSort() {
		List<SocialAccount> accounts = socialAccountMapper.listAll();
		int max = 0;
		for (SocialAccount account : accounts) {
			if (account.getSort() != null && account.getSort() > max) {
				max = account.getSort();
			}
		}
		return max + SORT_STEP;
	}
}
