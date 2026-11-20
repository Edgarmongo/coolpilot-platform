package com.coolskyai.controller.admin;

import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.entity.Category;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.BlogService;
import com.coolskyai.service.CategoryService;
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
 * 分类后台管理。
 *
 * <p>规则与标签一致：名称不可重复、被文章引用时不可删除。
 * 注意两者的提示文案不同（「分类名称不能为空」/「该分类已存在」），
 * 前端直接展示这些字符串，改中文案要同步改前端。
 *
 * @author CoolSkyAI
 */
@RestController
@RequestMapping("/admin")
public class CategoryAdminController {

	private static final String ORDER_BY_ID_DESC = "id desc";

	private final BlogService blogService;
	private final CategoryService categoryService;

	public CategoryAdminController(BlogService blogService, CategoryService categoryService) {
		this.blogService = blogService;
		this.categoryService = categoryService;
	}

	@GetMapping("/categories")
	public Result categories(@RequestParam(defaultValue = "1") Integer pageNum,
	                         @RequestParam(defaultValue = "10") Integer pageSize) {
		PageHelper.startPage(pageNum, pageSize, ORDER_BY_ID_DESC);
		PageInfo<Category> pageInfo = new PageInfo<>(categoryService.getCategoryList());
		return Result.ok("请求成功", pageInfo);
	}

	@OperationLogger("添加分类")
	@PostMapping("/category")
	public Result saveCategory(@RequestBody Category category) {
		Result invalid = checkName(category, null);
		if (invalid != null) {
			return invalid;
		}
		categoryService.saveCategory(category);
		return Result.ok("分类添加成功");
	}

	@OperationLogger("修改分类")
	@PutMapping("/category")
	public Result updateCategory(@RequestBody Category category) {
		Result invalid = checkName(category, category.getId());
		if (invalid != null) {
			return invalid;
		}
		categoryService.updateCategory(category);
		return Result.ok("分类更新成功");
	}

	/**
	 * 按 id 删除分类，仍被文章引用时拒绝 —— 否则文章详情取分类会查空。
	 */
	@OperationLogger("删除分类")
	@DeleteMapping("/category")
	public Result delete(@RequestParam Long id) {
		if (blogService.countBlogByCategoryId(id) != 0) {
			return Result.error("已有博客与此分类关联，不可删除");
		}
		categoryService.deleteCategoryById(id);
		return Result.ok("删除成功");
	}

	/**
	 * 校验名称非空且不与**其它**分类重名。
	 *
	 * @param selfId 修改场景传自己的 id，同名记录是它就放行；新增传 null
	 */
	private Result checkName(Category category, Long selfId) {
		if (StringUtils.isEmpty(category.getName())) {
			return Result.error("分类名称不能为空");
		}
		Category sameName = categoryService.getCategoryByName(category.getName());
		if (sameName == null) {
			return null;
		}
		boolean belongsToSelf = selfId != null && selfId.equals(sameName.getId());
		return belongsToSelf ? null : Result.error("该分类已存在");
	}
}
