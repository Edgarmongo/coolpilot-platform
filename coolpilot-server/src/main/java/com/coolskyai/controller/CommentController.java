package com.coolskyai.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.entity.User;
import com.coolskyai.mapper.CommentMapper;
import com.coolskyai.model.dto.Comment;
import com.coolskyai.model.vo.PageComment;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.CommentService;
import com.coolskyai.service.impl.UserServiceImpl;
import com.coolskyai.util.JwtUtils;
import com.coolskyai.util.StringUtils;
import com.coolskyai.util.comment.CommentUtils;
import com.coolskyai.enums.CommentOpenStateEnum;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 评论
 * @Author: CoolSkyAI
 * @Date: 2020-08-15
 */
@RestController
public class CommentController {
	@Autowired
	CommentService commentService;
	@Autowired
	UserServiceImpl userService;
	@Autowired
	CommentUtils commentUtils;
	@Autowired
	AccountService accountService;
	@Autowired
	CommentMapper commentMapper;

	/**
	 * 根据页面分页查询评论列表
	 *
	 * @param page     页面分类（0普通文章，1关于我...）
	 * @param blogId   如果page==0，需要博客id参数
	 * @param pageNum  页码
	 * @param pageSize 每页个数
	 * @param jwt      若文章受密码保护，需要获取访问Token
	 * @return
	 */
	@GetMapping("/comments")
	public Result comments(@RequestParam Integer page,
	                       @RequestParam(defaultValue = "") Long blogId,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "10") Integer pageSize,
	                       @RequestHeader(value = "Authorization", defaultValue = "") String jwt) {
		CommentOpenStateEnum openState = commentUtils.judgeCommentState(page, blogId);
		switch (openState) {
			case NOT_FOUND:
				return Result.create(404, "该博客不存在");
			case CLOSE:
				return Result.create(403, "评论已关闭");
			case PASSWORD:
				//文章受密码保护，需要验证Token
				if (JwtUtils.judgeTokenIsExist(jwt)) {
					try {
						String subject = JwtUtils.getTokenBody(jwt).getSubject();
						if (JwtConstants.isOwnerSubject(subject)) {
							//博主身份Token
							String username = JwtConstants.ownerName(subject);
							User admin = (User) userService.loadUserByUsername(username);
							if (admin == null) {
								return Result.create(403, "博主身份Token已失效，请重新登录！");
							}
						} else {
							//经密码验证后的Token
							Long tokenBlogId = Long.parseLong(subject);
							//博客id不匹配，验证不通过，可能博客id改变或客户端传递了其它密码保护文章的Token
							if (!tokenBlogId.equals(blogId)) {
								return Result.create(403, "Token不匹配，请重新验证密码！");
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						return Result.create(403, "Token已失效，请重新验证密码！");
					}
				} else {
					return Result.create(403, "此文章受密码保护，请验证密码！");
				}
				break;
			default:
				break;
		}
		//查询该页面所有评论的数量
		Integer allComment = commentService.countByPageAndIsPublished(page, blogId, null);
		//查询该页面公开评论的数量
		Integer openComment = commentService.countByPageAndIsPublished(page, blogId, true);
		PageHelper.startPage(pageNum, pageSize);
		PageInfo<PageComment> pageInfo = new PageInfo<>(commentService.getPageCommentList(page, blogId, -1L));
		PageResult<PageComment> pageResult = new PageResult<>(pageInfo.getPages(), pageInfo.getList());
		Map<String, Object> map = new HashMap<>(8);
		map.put("allComment", allComment);
		map.put("closeComment", allComment - openComment);
		map.put("comments", pageResult);
		return Result.ok("获取成功", map);
	}

	/**
	 * 提交评论 又长又臭 能用就不改了:)
	 * 单个ip，30秒内允许提交1次评论
	 *
	 * @param comment 评论DTO
	 * @param request 获取ip
	 * @param jwt     博主身份Token
	 * @return
	 */
	@AccessLimit(seconds = 30, maxCount = 1, msg = "30秒内只能提交一次评论")
	@PostMapping("/comment")
	public Result postComment(@RequestBody Comment comment,
	                          HttpServletRequest request,
	                          @RequestHeader(value = "Authorization", defaultValue = "") String jwt) {
		//评论内容合法性校验
		if (StringUtils.isEmpty(comment.getContent()) || comment.getContent().length() > 250 ||
				comment.getPage() == null || comment.getParentCommentId() == null) {
			return Result.error("参数有误");
		}
		//是否访客的评论
		boolean isVisitorComment = false;
		//父评论
		com.coolskyai.entity.Comment parentComment = null;
		//对于有指定父评论的评论，应该以父评论为准，只判断页面可能会被绕过“评论开启状态检测”
		if (comment.getParentCommentId() != -1) {
			//当前评论为子评论
			parentComment = commentService.getCommentById(comment.getParentCommentId());
			Integer page = parentComment.getPage();
			Long blogId = page == 0 ? parentComment.getBlog().getId() : null;
			comment.setPage(page);
			comment.setBlogId(blogId);
		} else {
			//当前评论为顶级评论
			if (comment.getPage() != 0) {
				comment.setBlogId(null);
			}
		}
		//判断是否可评论
		CommentOpenStateEnum openState = commentUtils.judgeCommentState(comment.getPage(), comment.getBlogId());
		switch (openState) {
			case NOT_FOUND:
				return Result.create(404, "该博客不存在");
			case CLOSE:
				return Result.create(403, "评论已关闭");
			case PASSWORD:
				//文章受密码保护
				//验证Token合法性
				if (JwtUtils.judgeTokenIsExist(jwt)) {
					String subject;
					try {
						subject = JwtUtils.getTokenBody(jwt).getSubject();
					} catch (Exception e) {
						e.printStackTrace();
						return Result.create(403, "Token已失效，请重新验证密码！");
					}
					//博主评论，不受密码保护限制，根据博主信息设置评论属性
					if (JwtConstants.isOwnerSubject(subject)) {
						//Token验证通过，获取Token中用户名
						String username = JwtConstants.ownerName(subject);
						User admin = (User) userService.loadUserByUsername(username);
						if (admin == null) {
							return Result.create(403, "博主身份Token已失效，请重新登录！");
						}
						commentUtils.setAdminComment(comment, request, admin);
						isVisitorComment = false;
					} else if (JwtConstants.isUserSubject(subject)) {
						/*
						 * 登录访客的 token 不含文章密码信息。
						 * 之前这里直接 Long.parseLong(subject)，遇到 visitor:2 会抛异常变成 500。
						 */
						return Result.create(403, "此文章受密码保护，请验证密码");
					} else {//普通访客经文章密码验证后携带Token
						//对访客的评论昵称、邮箱合法性校验
						if (StringUtils.isEmpty(comment.getNickname(), comment.getEmail()) || comment.getNickname().length() > 15) {
							return Result.error("参数有误");
						}
						//对于受密码保护的文章，则Token是必须的
						Long tokenBlogId = Long.parseLong(subject);
						//博客id不匹配，验证不通过，可能博客id改变或客户端传递了其它密码保护文章的Token
						if (!tokenBlogId.equals(comment.getBlogId())) {
							return Result.create(403, "Token不匹配，请重新验证密码！");
						}
						commentUtils.setVisitorComment(comment, request);
						isVisitorComment = true;
					}
				} else {//不存在Token则无评论权限
					return Result.create(403, "此文章受密码保护，请验证密码！");
				}
				break;
			case OPEN:
				//评论正常开放
				//有Token则为博主评论，或文章原先为密码保护，后取消保护，但客户端仍存在Token
				if (JwtUtils.judgeTokenIsExist(jwt)) {
					String subject;
					try {
						subject = JwtUtils.getTokenBody(jwt).getSubject();
					} catch (Exception e) {
						e.printStackTrace();
						return Result.create(403, "Token已失效，请重新验证密码");
					}
					//博主评论，根据博主信息设置评论属性
					if (JwtConstants.isOwnerSubject(subject)) {
						//Token验证通过，获取Token中用户名
						String username = JwtConstants.ownerName(subject);
						User admin = (User) userService.loadUserByUsername(username);
						if (admin == null) {
							return Result.create(403, "博主身份Token已失效，请重新登录！");
						}
						commentUtils.setAdminComment(comment, request, admin);
						isVisitorComment = false;
					} else if (JwtConstants.isUserSubject(subject)) {
						/*
						 * 登录访客评论：记录账号ID（用于「我的评论」），
						 * 昵称/邮箱缺省时用账号资料补齐，避免前端不传就报「参数有误」。
						 */
						Long visitorId = JwtConstants.userId(subject);
						if (visitorId == null) {
							return Result.create(403, "登录状态异常，请重新登录");
						}
						User visitor = userService.findUserById(visitorId);
						if (visitor == null) {
							return Result.create(403, "登录状态已失效，请重新登录");
						}
						comment.setUserId(visitor.getId());
						if (StringUtils.isEmpty(comment.getNickname())) {
							comment.setNickname(StringUtils.isEmpty(visitor.getNickname())
									? visitor.getUsername() : visitor.getNickname());
						}
						if (StringUtils.isEmpty(comment.getEmail())) {
							// email 列为非空；账号未填邮箱时用占位地址，不参与发信
							comment.setEmail(StringUtils.isEmpty(visitor.getEmail())
									? "user" + visitor.getId() + "@coolskyai.local" : visitor.getEmail());
						}
						if (comment.getNickname().length() > 15) {
							return Result.error("昵称不能超过15个字符");
						}
						commentUtils.setVisitorComment(comment, request);
						isVisitorComment = true;
					} else {//文章原先为密码保护，后取消保护，但客户端仍存在Token，则忽略Token
						//对访客的评论昵称、邮箱合法性校验
						if (StringUtils.isEmpty(comment.getNickname(), comment.getEmail()) || comment.getNickname().length() > 15) {
							return Result.error("参数有误");
						}
						commentUtils.setVisitorComment(comment, request);
						isVisitorComment = true;
					}
				} else {
					//访客评论
					//对访客的评论昵称、邮箱合法性校验
					if (StringUtils.isEmpty(comment.getNickname(), comment.getEmail()) || comment.getNickname().length() > 15) {
						return Result.error("参数有误");
					}
					commentUtils.setVisitorComment(comment, request);
					isVisitorComment = true;
				}
				break;
			default:
				break;
		}
		commentService.saveComment(comment);
		commentUtils.judgeSendNotify(comment, isVisitorComment, parentComment);
		return Result.ok("评论成功");
	}

	/**
	 * 我的评论：只返回当前登录账号发表的评论。
	 * 历史匿名评论没有 user_id，不会出现在这里。
	 */
	@GetMapping("/me/comments")
	public Result myComments(@RequestParam(defaultValue = "1") Integer pageNum,
	                         @RequestParam(defaultValue = "10") Integer pageSize) {
		Long userId = accountService.requireUserId();
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1 || pageSize > 100) ? 10 : pageSize;
		int total = commentMapper.countByUser(userId);
		List<Map<String, Object>> list = commentMapper.pageByUser(userId, (page - 1) * size, size);
		return Result.ok("OK", new PageResult<>((total + size - 1) / size, total, list));
	}
}
