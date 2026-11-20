package com.coolskyai.controller.admin;

import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.entity.Tag;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.BlogService;
import com.coolskyai.service.TagService;
import com.coolskyai.util.StringUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签后台管理。
 *
 * <p>两条业务规则写在这里而不是 service：
 * <ul>
 *   <li>标签名不能重复。新增时任何同名都算冲突；修改时若同名记录就是自己，放行。</li>
 *   <li>仍被文章引用的标签不允许删除 —— 否则文章详情取标签时会查空。</li>
 * </ul>
 *
 * @author CoolSkyAI
 */
@RestController
@RequestMapping("/admin")
public class TagAdminController {

	private static final String ORDER_BY_ID_DESC = "id desc";

	private final BlogService blogService;
	private final TagService tagService;

	public TagAdminController(BlogService blogService, TagService tagService) {
		this.blogService = blogService;
		this.tagService = tagService;
	}

	@GetMapping("/tags")
	public Result tags(@RequestParam(defaultValue = "1") Integer pageNum,
	                   @RequestParam(defaultValue = "10") Integer pageSize) {
		PageHelper.startPage(pageNum, pageSize, ORDER_BY_ID_DESC);
		PageInfo<Tag> pageInfo = new PageInfo<>(tagService.getTagList());
		return Result.ok("请求成功", pageInfo);
	}

	@OperationLogger("添加标签")
	@PostMapping("/tag")
	public Result saveTag(@RequestBody Tag tag) {
		Result invalid = checkName(tag, null);
		if (invalid != null) {
			return invalid;
		}
		tagService.saveTag(tag);
		return Result.ok("添加成功");
	}

	@OperationLogger("修改标签")
	@PutMapping("/tag")
	public Result updateTag(@RequestBody Tag tag) {
		Result invalid = checkName(tag, tag.getId());
		if (invalid != null) {
			return invalid;
		}
		tagService.updateTag(tag);
		return Result.ok("更新成功");
	}

	/**
	 * 按 id 删除标签，已被文章引用时拒绝。
	 */
	@OperationLogger("删除标签")
	@DeleteMapping("/tag")
	public Result delete(@RequestParam Long id) {
		if (blogService.countBlogByTagId(id) != 0) {
			return Result.error("已有博客与此标签关联，不可删除");
		}
		tagService.deleteTagById(id);
		return Result.ok("删除成功");
	}

	/**
	 * 校验标签名非空且不与**其它**标签重名。
	 *
	 * @param selfId 修改场景传自己的 id，同名记录是它就视为合法；新增传 null
	 */
	private Result checkName(Tag tag, Long selfId) {
		if (StringUtils.isEmpty(tag.getName())) {
			return Result.error("参数不能为空");
		}
		Tag sameName = tagService.getTagByName(tag.getName());
		if (sameName == null) {
			return null;
		}
		boolean belongsToSelf = selfId != null && selfId.equals(sameName.getId());
		return belongsToSelf ? null : Result.error("该标签已存在");
	}
}
