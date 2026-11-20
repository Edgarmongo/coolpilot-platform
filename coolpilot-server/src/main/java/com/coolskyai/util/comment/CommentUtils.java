package com.coolskyai.util.comment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import com.coolskyai.config.properties.BlogProperties;
import com.coolskyai.constant.PageConstants;
import com.coolskyai.entity.User;
import com.coolskyai.model.dto.Comment;
import com.coolskyai.model.vo.FriendInfo;
import com.coolskyai.service.AboutService;
import com.coolskyai.service.BlogService;
import com.coolskyai.service.FriendService;
import com.coolskyai.service.UserService;
import com.coolskyai.util.HashUtils;
import com.coolskyai.util.IpAddressUtils;
import com.coolskyai.util.MailUtils;
import com.coolskyai.util.StringUtils;
import com.coolskyai.util.comment.channel.ChannelFactory;
import com.coolskyai.util.comment.channel.CommentNotifyChannel;
import com.coolskyai.enums.CommentOpenStateEnum;
import com.coolskyai.enums.CommentPageEnum;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 评论组装与提醒
 *
 * 职责有三块：把请求里的评论补全成可入库的实体、判断当前页面是否允许评论、
 * 以及决定「这条评论该提醒谁」。
 *
 * 提醒规则有六种组合（见 judgeSendNotify 注释），核心判断只有两条：
 * 回复了别人的评论且对方愿意收提醒 → 提醒对方；访客发评论 → 提醒博主自己。
 *
 * 注意 blogService 是静态字段：getCommentPageEnum / getPageTitle 是静态方法，
 * 被模板通道与 Telegram 机器人直接调用。这是历史形态，新增代码请正常注入。
 *
 * @author: CoolSkyAI
 * @date: 2022-01-22
 */
@Component
@DependsOn("springContextUtils")
public class CommentUtils {

	/** 头像库里的张数，取模保证同一昵称永远同一张 */
	private static final int AVATAR_COUNT = 6;

	/** 只允许 http/https 的常规域名，挡掉 javascript: 与带特殊字符的地址 */
	private static final Pattern WEBSITE_PATTERN = Pattern.compile(
			"^https?://([^!@#$%^&*?.\\s-]([^!@#$%^&*?.\\s]{0,63}[^!@#$%^&*?.\\s])?\\.)+[a-z]{2,6}/?");

	private static final String GUEST_MAIL_TEMPLATE = "guest.html";

	private static BlogService blogService;

	private final BlogProperties blogProperties;
	private final MailUtils mailUtils;
	private final AboutService aboutService;
	private final FriendService friendService;
	private final UserService userService;
	private final CommentNotifyChannel notifyChannel;
	private final Boolean commentDefaultOpen;

	public CommentUtils(BlogProperties blogProperties,
	                    MailUtils mailUtils,
	                    AboutService aboutService,
	                    FriendService friendService,
	                    UserService userService,
	                    @Value("${comment.notify.channel}") String notifyChannelName,
	                    @Value("${comment.default-open}") Boolean commentDefaultOpen) {
		this.blogProperties = blogProperties;
		this.mailUtils = mailUtils;
		this.aboutService = aboutService;
		this.friendService = friendService;
		this.userService = userService;
		this.notifyChannel = ChannelFactory.getChannel(notifyChannelName);
		this.commentDefaultOpen = commentDefaultOpen;
	}

	@Autowired
	public void setBlogService(BlogService blogService) {
		CommentUtils.blogService = blogService;
	}

	// ---------------- 提醒 ----------------

	/**
	 * 决定这条评论要提醒谁。六种情况：
	 * 1. 博主以父评论提交：不提醒
	 * 2. 博主回复自己：不提醒
	 * 3. 博主回复访客：只提醒该访客（对方开了提醒才发）
	 * 4. 访客以父评论提交：只提醒博主
	 * 5. 访客回复博主：只提醒博主
	 * 6. 访客回复访客（哪怕是自己先前的评论）：提醒被回复者，同时提醒博主
	 *
	 * @param comment          刚提交的评论
	 * @param isVisitorComment 是否访客提交
	 * @param parentComment    父评论，可为 null
	 */
	public void judgeSendNotify(Comment comment, boolean isVisitorComment, com.coolskyai.entity.Comment parentComment) {
		if (shouldMailParent(parentComment)) {
			mailParent(parentComment, comment);
		}
		if (isVisitorComment) {
			notifyChannel.notifyMyself(comment);
		}
	}

	private boolean shouldMailParent(com.coolskyai.entity.Comment parent) {
		if (parent == null) {
			return false;
		}
		// 博主自己的评论不提醒；访客主动关闭提醒也不发
		return !Boolean.TRUE.equals(parent.getAdminComment()) && Boolean.TRUE.equals(parent.getNotice());
	}

	/**
	 * 邮件提醒被回复的人：内容走 guest.html 模板，链接指向评论所在页面
	 */
	private void mailParent(com.coolskyai.entity.Comment parent, Comment comment) {
		Map<String, Object> model = new HashMap<>(16);
		model.put("parentNickname", parent.getNickname());
		model.put("nickname", comment.getNickname());
		model.put("title", getPageTitle(comment));
		model.put("time", comment.getCreateTime());
		model.put("parentContent", parent.getContent());
		model.put("content", comment.getContent());
		model.put("url", blogProperties.getView() + getPagePath(comment));

		String subject = "您在 " + blogProperties.getName() + " 的评论有了新回复";
		mailUtils.sendHtmlTemplateMail(model, parent.getEmail(), subject, GUEST_MAIL_TEMPLATE);
	}

	// ---------------- 页面定位 ----------------

	/**
	 * 评论所属页面的枚举常量。只做分类，不携带标题与路径 ——
	 * 枚举是单例，往它身上写 title/path 会让并发请求互相覆盖。
	 * 需要标题或路径请用 getPageTitle / getPagePath。
	 */
	public static CommentPageEnum getCommentPageEnum(Comment comment) {
		return constantOf(comment.getPage());
	}

	public static String getPageTitle(Comment comment) {
		if (isBlogPage(comment)) {
			return blogService.getTitleByBlogId(comment.getBlogId());
		}
		return constantOf(comment.getPage()).getTitle();
	}

	public static String getPagePath(Comment comment) {
		if (isBlogPage(comment)) {
			return "/blog/" + comment.getBlogId();
		}
		return constantOf(comment.getPage()).getPath();
	}

	private static boolean isBlogPage(Comment comment) {
		return comment.getPage() != null && comment.getPage() == PageConstants.BLOG;
	}

	private static CommentPageEnum constantOf(Integer page) {
		if (page == null) {
			return CommentPageEnum.UNKNOWN;
		}
		return switch (page) {
			case PageConstants.BLOG -> CommentPageEnum.BLOG;
			case PageConstants.ABOUT -> CommentPageEnum.ABOUT;
			case PageConstants.FRIEND -> CommentPageEnum.FRIEND;
			default -> CommentPageEnum.UNKNOWN;
		};
	}

	// ---------------- 是否允许评论 ----------------

	/**
	 * 查询对应页面的评论是否开放
	 *
	 * @param page   页面分类（0 文章、1 关于我、2 友链）
	 * @param blogId page 为文章时需要，用于校验文章存在且已公开
	 */
	public CommentOpenStateEnum judgeCommentState(Integer page, Long blogId) {
		if (page == null) {
			return CommentOpenStateEnum.NOT_FOUND;
		}
		return switch (page) {
			case PageConstants.BLOG -> blogState(blogId);
			case PageConstants.ABOUT -> aboutState();
			case PageConstants.FRIEND -> friendState();
			default -> CommentOpenStateEnum.OPEN;
		};
	}

	private CommentOpenStateEnum blogState(Long blogId) {
		Boolean enabled = blogService.getCommentEnabledByBlogId(blogId);
		Boolean published = blogService.getPublishedByBlogId(blogId);
		if (enabled == null || published == null || !published) {
			return CommentOpenStateEnum.NOT_FOUND;
		}
		if (!enabled) {
			return CommentOpenStateEnum.CLOSE;
		}
		// 加密文章要先输密码才谈得上评论
		return StringUtils.isEmpty(blogService.getBlogPassword(blogId))
				? CommentOpenStateEnum.OPEN
				: CommentOpenStateEnum.PASSWORD;
	}

	private CommentOpenStateEnum aboutState() {
		return aboutService.getAboutCommentEnabled() ? CommentOpenStateEnum.OPEN : CommentOpenStateEnum.CLOSE;
	}

	private CommentOpenStateEnum friendState() {
		FriendInfo info = friendService.getFriendInfo(true, false);
		return info.getCommentEnabled() ? CommentOpenStateEnum.OPEN : CommentOpenStateEnum.CLOSE;
	}

	// ---------------- 评论属性 ----------------

	/**
	 * 为 Telegram 快捷回复补全博主评论属性
	 */
	public void setAdminCommentByTelegramAction(Comment comment) {
		// 约定 id=1 是博主
		User admin = userService.findUserById(1L);
		applyAdminComment(comment, admin);
		comment.setIp("via Telegram");
	}

	/**
	 * 后台直接回复：属性同博主评论，IP 取真实请求
	 */
	public void setAdminComment(Comment comment, HttpServletRequest request, User admin) {
		applyAdminComment(comment, admin);
		comment.setIp(IpAddressUtils.getIpAddress(request));
	}

	private void applyAdminComment(Comment comment, User admin) {
		if (admin == null) {
			throw new IllegalStateException("博主账号不存在（id=1），无法以博主身份发表评论");
		}
		comment.setAdminComment(true);
		comment.setCreateTime(new Date());
		comment.setPublished(true);
		comment.setAvatar(admin.getAvatar());
		comment.setWebsite("/");
		comment.setNickname(admin.getNickname());
		comment.setEmail(admin.getEmail());
		comment.setNotice(false);
	}

	/**
	 * 补全访客评论：昵称去空白、按昵称分配固定头像、校验网站、给默认值
	 */
	public void setVisitorComment(Comment comment, HttpServletRequest request) {
		comment.setNickname(comment.getNickname().trim());
		comment.setAvatar(avatarOf(comment.getNickname()));
		comment.setWebsite(isValidWebsite(comment.getWebsite()) ? comment.getWebsite() : "");
		comment.setAdminComment(false);
		// is_notice 是非空列，前端漏传时给默认值，否则插入直接被约束拒绝
		if (comment.getNotice() == null) {
			comment.setNotice(false);
		}
		comment.setCreateTime(new Date());
		comment.setPublished(commentDefaultOpen);
		comment.setEmail(comment.getEmail().trim());
		comment.setIp(IpAddressUtils.getIpAddress(request));
	}

	/**
	 * 昵称 → 固定头像。用 MurmurHash 而不是随机，保证同一昵称每次都是同一张脸
	 */
	static String avatarOf(String nickname) {
		long bucket = HashUtils.getMurmurHash32(nickname) % AVATAR_COUNT + 1;
		return "/img/comment-avatar/" + bucket + ".jpg";
	}

	/**
	 * 个人网站是选填项：不填（null/空串）直接判为不合法，调用方会置空
	 */
	static boolean isValidWebsite(String url) {
		if (url == null || url.isEmpty()) {
			return false;
		}
		return WEBSITE_PATTERN.matcher(url).matches();
	}
}
