package top.naccl.modules.product.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.annotation.OperationLogger;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.product.dto.ProductSaveDTO;
import top.naccl.modules.product.entity.Lesson;
import top.naccl.modules.product.entity.ProductResource;
import top.naccl.modules.product.service.ProductService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 知识商品管理（后台）
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@RestController
public class ProductAdminController {
	@Autowired
	private ProductService productService;
	@Autowired
	private AccountService accountService;

	@GetMapping("/admin/products")
	public Result products(@RequestParam(required = false) String type,
	                       @RequestParam(required = false) String status,
	                       @RequestParam(required = false) String keyword,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", productService.pageAdmin(type, status, keyword, pageNum, pageSize));
	}

	@GetMapping("/admin/product/{id}")
	public Result detail(@PathVariable Long id) {
		return Result.ok("OK", productService.adminDetail(id));
	}

	@OperationLogger("创建商品")
	@PostMapping("/admin/product")
	public Result create(@RequestBody ProductSaveDTO dto) {
		Long ownerId = accountService.requireUserId();
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", productService.create(dto, ownerId));
		return Result.ok("创建成功", data);
	}

	@OperationLogger("编辑商品")
	@PutMapping("/admin/product/{id}")
	public Result update(@PathVariable Long id, @RequestBody ProductSaveDTO dto) {
		productService.update(id, dto);
		return Result.ok("保存成功");
	}

	@OperationLogger("变更商品状态")
	@PutMapping("/admin/product/{id}/status")
	public Result changeStatus(@PathVariable Long id, @RequestParam String status) {
		productService.changeStatus(id, status);
		return Result.ok("操作成功");
	}

	@OperationLogger("保存课时")
	@PostMapping("/admin/lesson")
	public Result saveLesson(@RequestBody Lesson lesson) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", productService.saveLesson(lesson));
		return Result.ok("保存成功", data);
	}

	@OperationLogger("删除课时")
	@DeleteMapping("/admin/lesson/{id}")
	public Result deleteLesson(@PathVariable Long id) {
		productService.deleteLesson(id);
		return Result.ok("删除成功");
	}

	@OperationLogger("绑定商品资源")
	@PostMapping("/admin/product/{id}/resource")
	public Result bindResource(@PathVariable Long id, @RequestBody ProductResource resource) {
		resource.setProductId(id);
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", productService.bindResource(resource));
		return Result.ok("绑定成功", data);
	}
}
