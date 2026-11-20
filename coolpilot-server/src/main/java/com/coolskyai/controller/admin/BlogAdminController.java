package com.coolskyai.controller.admin;

import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.entity.Blog;
import com.coolskyai.entity.Category;
import com.coolskyai.entity.Tag;
import com.coolskyai.entity.User;
import com.coolskyai.model.dto.BlogVisibility;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.BlogService;
import com.coolskyai.service.CategoryService;
import com.coolskyai.service.CommentService;
import com.coolskyai.service.TagService;
import com.coolskyai.util.StringUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文章后台管理。
 *
 * <p>这里最绕的是保存/更新时对分类和标签的处理：前端可能传「已有项的 id」，
 * 也可能传「新名称」直接新建，两种混在同一个字段里。所以入参是 {@code Object}，
 * 要按运行时类型分别处理。
 *
 * <p>另一条约定：**参数错误用 `Result.error` 返回，不抛异常**。
 * 前端按 code 展示中文提示，抛异常会走到全局处理器，提示文案就变了。
 *
 * @author CoolSkyAI
 */
@RestController
@RequestMapping("/admin")
public class BlogAdminController {

	/** 阅读时长估算：中文技术文按每分钟 200 字。只是给个数量级，不用精确。 */
	private static final int WORDS_PER_MINUTE = 200;

	/** 个人博客只有一个作者，文章统一挂在这个账号下 */
	private static final long DEFAULT_AUTHOR_ID = 1L;

	private static final String ORDER_BY_CREATE_TIME_DESC = "create_time desc";

	private final BlogService blogService;
	private final CategoryService categoryService;
	private final TagService tagService;
	private final CommentService commentService;

	public BlogAdminController(BlogService blogService, CategoryService categoryService,
	                           TagService tagService, CommentService commentService) {
		this.blogService = blogService;
		this.categoryService = categoryService;
		this.tagService = tagService;
		this.commentService = commentService;
	}

	/**
	 * 文章列表（后台视角，含草稿与私密文章）。
	 */
	@GetMapping("/blogs")
	public Result blogs(@RequestParam(defaultValue = "") String title,
	                    @RequestParam(defaultValue = "") Integer categoryId,
	                    @RequestParam(defaultValue = "1") Integer pageNum,
	                    @RequestParam(defaultValue = "10") Integer pageSize) {
		PageHelper.startPage(pageNum, pageSize, ORDER_BY_CREATE_TIME_DESC);
		PageInfo<Blog> pageInfo = new PageInfo<>(blogService.getListByTitleAndCategoryId(title, categoryId));

		Map<String, Object> payload = new HashMap<>(4);
		payload.put("blogs", pageInfo);
		payload.put("categories", categoryService.getCategoryList());
		return Result.ok("请求成功", payload);
	}

	/**
	 * 删除文章：先断开关联，再删正文，最后清理评论。
	 *
	 * <p>顺序不能反 —— 评论要按 blogId 清理，文章没了就查不到归属了。
	 */
	@OperationLogger("删除博客")
	@DeleteMapping("/blog")
	public Result delete(@RequestParam Long id) {
		blogService.deleteBlogTagByBlogId(id);
		blogService.deleteBlogById(id);
		commentService.deleteCommentsByBlogId(id);
		return Result.ok("删除成功");
	}

	/**
	 * 编辑页需要的下拉数据：分类与标签。
	 */
	@GetMapping("/categoryAndTag")
	public Result categoryAndTag() {
		Map<String, Object> payload = new HashMap<>(4);
		payload.put("categories", categoryService.getCategoryList());
		payload.put("tags", tagService.getTagList());
		return Result.ok("请求成功", payload);
	}

	@OperationLogger("更新博客置顶状态")
	@PutMapping("/blog/top")
	public Result updateTop(@RequestParam Long id, @RequestParam Boolean top) {
		blogService.updateBlogTopById(id, top);
		return Result.ok("操作成功");
	}

	@OperationLogger("更新博客推荐状态")
	@PutMapping("/blog/recommend")
	public Result updateRecommend(@RequestParam Long id, @RequestParam Boolean recommend) {
		blogService.updateBlogRecommendById(id, recommend);
		return Result.ok("操作成功");
	}

	@OperationLogger("更新博客可见性状态")
	@PutMapping("blog/{id}/visibility")
	public Result updateVisibility(@PathVariable Long id, @RequestBody BlogVisibility blogVisibility) {
		blogService.updateBlogVisibilityById(id, blogVisibility);
		return Result.ok("操作成功");
	}

	@GetMapping("/blog")
	public Result getBlog(@RequestParam Long id) {
		return Result.ok("获取成功", blogService.getBlogById(id));
	}

	@OperationLogger("发布博客")
	@PostMapping("/blog")
	public Result saveBlog(@RequestBody com.coolskyai.model.dto.Blog blog) {
		List<Tag> tags = new ArrayList<>();
		Result invalid = validateAndResolve(blog, tags);
		if (invalid != null) {
			return invalid;
		}

		normaliseMetrics(blog);
		Date now = new Date();
		blog.setCreateTime(now);
		blog.setUpdateTime(now);
		blog.setUser(defaultAuthor());
		blogService.saveBlog(blog);
		bindTags(blog.getId(), tags);

		// 返回 id：前端新建后要立刻绑定关联商品，没有 id 就得再查一次列表
		Map<String, Object> payload = new HashMap<>(2);
		payload.put("id", blog.getId());
		return Result.ok("添加成功", payload);
	}

	@OperationLogger("更新博客")
	@PutMapping("/blog")
	public Result updateBlog(@RequestBody com.coolskyai.model.dto.Blog blog) {
		List<Tag> tags = new ArrayList<>();
		Result invalid = validateAndResolve(blog, tags);
		if (invalid != null) {
			return invalid;
		}

		normaliseMetrics(blog);
		blog.setUpdateTime(new Date());
		blogService.updateBlog(blog);

		// 标签是全量覆盖：先清空关联，再按本次提交重建
		blogService.deleteBlogTagByBlogId(blog.getId());
		bindTags(blog.getId(), tags);
		return Result.ok("更新成功");
	}

	// ------------------------------------------------------------------
	// 入参处理
	// ------------------------------------------------------------------

	/**
	 * 校验必填项，并把分类、标签解析成实体写回 blog / tags。
	 *
	 * @param tagsOut 解析成功的标签写进这个集合（Java 没有出参，用集合代替）
	 * @return 校验失败时的响应；成功返回 null
	 */
	private Result validateAndResolve(com.coolskyai.model.dto.Blog blog, List<Tag> tagsOut) {
		if (StringUtils.isEmpty(blog.getTitle(), blog.getFirstPicture(), blog.getContent(), blog.getDescription())
				|| blog.getWords() == null || blog.getWords() < 0) {
			return Result.error("参数有误");
		}

		Result categoryFailure = resolveCategory(blog);
		if (categoryFailure != null) {
			return categoryFailure;
		}
		return resolveTags(blog, tagsOut);
	}

	/**
	 * 分类字段可能给 id（选已有）也可能给名称（新建），按类型分派。
	 */
	private Result resolveCategory(com.coolskyai.model.dto.Blog blog) {
		Object raw = blog.getCate();
		if (raw == null) {
			return Result.error("分类不能为空");
		}
		if (raw instanceof Integer id) {
			blog.setCategory(categoryService.getCategoryById(id.longValue()));
			return null;
		}
		if (raw instanceof String name) {
			if (categoryService.getCategoryByName(name) != null) {
				return Result.error("不可添加已存在的分类");
			}
			Category created = new Category();
			created.setName(name);
			categoryService.saveCategory(created);
			blog.setCategory(created);
			return null;
		}
		return Result.error("分类不正确");
	}

	/**
	 * 标签同理，但要处理一整批；任一不合法就整体失败，避免只写进去一半。
	 */
	private Result resolveTags(com.coolskyai.model.dto.Blog blog, List<Tag> tagsOut) {
		List<Object> submitted = blog.getTagList();
		if (submitted == null || submitted.isEmpty()) {
			return null;
		}
		for (Object raw : submitted) {
			if (raw instanceof Integer id) {
				tagsOut.add(tagService.getTagById(id.longValue()));
			} else if (raw instanceof String name) {
				if (tagService.getTagByName(name) != null) {
					return Result.error("不可添加已存在的标签");
				}
				Tag created = new Tag();
				created.setName(name);
				tagService.saveTag(created);
				tagsOut.add(created);
			} else {
				return Result.error("标签不正确");
			}
		}
		return null;
	}

	/**
	 * 维护 blog_tag 关联表。
	 */
	private void bindTags(Long blogId, List<Tag> tags) {
		for (Tag tag : tags) {
			blogService.saveBlogTag(blogId, tag.getId());
		}
	}

	private User defaultAuthor() {
		User author = new User();
		author.setId(DEFAULT_AUTHOR_ID);
		return author;
	}

	/**
	 * 字数缺失或非法时按字数反推阅读时长，避免前端传 0 显示「0 分钟」。
	 * 顺手把负数浏览量归零。
	 */
	private void normaliseMetrics(com.coolskyai.model.dto.Blog blog) {
		if (blog.getReadTime() == null || blog.getReadTime() < 0) {
			blog.setReadTime((int) Math.round(blog.getWords() / (double) WORDS_PER_MINUTE));
		}
		if (blog.getViews() == null || blog.getViews() < 0) {
			blog.setViews(0);
		}
	}
}
