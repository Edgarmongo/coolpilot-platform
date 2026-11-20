package com.coolskyai.util.comment;

import org.junit.jupiter.api.Test;
import com.coolskyai.enums.CommentPageEnum;
import com.coolskyai.model.dto.Comment;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论工具里与 Spring 无关的那部分：头像映射、网址校验、页面定位。
 *
 * 重点是头像的确定性 —— 它由昵称哈希取模得到，值一变，老访客的头像就换人了。
 * 文章页（page=0）的标题/路径要查库，放在运行时冒烟里验。
 */
class CommentUtilsTest {

	@Test
	void avatarOf_同一昵称固定同一张() {
		String first = CommentUtils.avatarOf("CoolSky");
		assertThat(CommentUtils.avatarOf("CoolSky")).isEqualTo(first);
		assertThat(first).matches("/img/comment-avatar/[1-6]\\.jpg");
	}

	@Test
	void avatarOf_落在六张之内且能分散() {
		Set<String> avatars = new HashSet<>();
		for (String nickname : new String[]{"张三", "李四", "王五", "Alice", "Bob", "Carol", "Dave", "Eve"}) {
			String avatar = CommentUtils.avatarOf(nickname);
			assertThat(avatar).matches("/img/comment-avatar/[1-6]\\.jpg");
			avatars.add(avatar);
		}
		// 8 个昵称至少应落到 3 张不同的头像上
		assertThat(avatars.size()).isGreaterThanOrEqualTo(3);
	}

	@Test
	void isValidWebsite_接受常规域名() {
		assertThat(CommentUtils.isValidWebsite("https://coolskyai.com")).isTrue();
		assertThat(CommentUtils.isValidWebsite("http://coolskyai.com/")).isTrue();
		assertThat(CommentUtils.isValidWebsite("https://a.b.cn")).isTrue();
	}

	@Test
	void isValidWebsite_拒绝空值与脚本伪协议() {
		assertThat(CommentUtils.isValidWebsite(null)).isFalse();
		assertThat(CommentUtils.isValidWebsite("")).isFalse();
		assertThat(CommentUtils.isValidWebsite("coolskyai.com")).isFalse();
		assertThat(CommentUtils.isValidWebsite("javascript:alert(1)")).isFalse();
		assertThat(CommentUtils.isValidWebsite("https://exa mple.com")).isFalse();
		// 该规则只允许站点根地址，带路径的一律视为不合法（历史行为）
		assertThat(CommentUtils.isValidWebsite("https://coolskyai.com/about")).isFalse();
	}

	@Test
	void 页面常量_关于与友链() {
		Comment about = new Comment();
		about.setPage(1);
		assertThat(CommentUtils.getCommentPageEnum(about)).isEqualTo(CommentPageEnum.ABOUT);
		assertThat(CommentUtils.getPageTitle(about)).isEqualTo("关于我");
		assertThat(CommentUtils.getPagePath(about)).isEqualTo("/about");

		Comment friend = new Comment();
		friend.setPage(2);
		assertThat(CommentUtils.getCommentPageEnum(friend)).isEqualTo(CommentPageEnum.FRIEND);
		assertThat(CommentUtils.getPagePath(friend)).isEqualTo("/friends");
	}

	@Test
	void 页面常量_未知页面() {
		Comment unknown = new Comment();
		unknown.setPage(null);
		assertThat(CommentUtils.getCommentPageEnum(unknown)).isEqualTo(CommentPageEnum.UNKNOWN);
		assertThat(CommentUtils.getPagePath(unknown)).isEqualTo("UNKNOWN");

		Comment other = new Comment();
		other.setPage(99);
		assertThat(CommentUtils.getCommentPageEnum(other)).isEqualTo(CommentPageEnum.UNKNOWN);
	}
}
