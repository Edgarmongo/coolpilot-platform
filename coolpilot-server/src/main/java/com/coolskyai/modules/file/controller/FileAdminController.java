package com.coolskyai.modules.file.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.file.entity.FileInfo;
import com.coolskyai.modules.file.service.FileService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 附件管理（平台所有者，CP-BACKEND-02）
 *
 * 之前上传的图片在后台没有任何入口：既看不到传了什么，也删不掉。
 * 内容多起来之后 /data/upload 会积累大量不再被引用的文件。
 *
 * 两个刻意的决定：
 *   1. 删除前检查引用。图片是直接写进 Markdown 正文的，没有关联表，
 *      只能按 objectKey 去各处长文本里找；被引用就拒绝，而不是默默删掉
 *      导致文章里出现裂图。
 *   2. 列表不返回引用数。二十行文件要查六张表，代价不划算；
 *      真要删的时候再查，一次只查一个文件。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class FileAdminController {

	@Autowired
	private FileService fileService;

	@GetMapping("/admin/files")
	public Result list(@RequestParam(defaultValue = "1") Integer pageNum,
	                   @RequestParam(defaultValue = "20") Integer pageSize,
	                   @RequestParam(required = false) String bizType,
	                   @RequestParam(required = false) String keyword) {
		PageHelper.startPage(pageNum, pageSize, "id desc");
		List<FileInfo> files = fileService.listForAdmin(bizType, keyword);
		PageInfo<FileInfo> pageInfo = new PageInfo<>(files);
		return Result.ok("OK", pageInfo);
	}

	@GetMapping("/admin/files/stats")
	public Result stats() {
		return Result.ok("OK", fileService.stats());
	}

	/**
	 * 查这个附件被谁引用。被拒绝删除时前端用它展示具体位置。
	 */
	@GetMapping("/admin/files/{id}/references")
	public Result references(@PathVariable Long id) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("references", fileService.findReferencesById(id));
		return Result.ok("OK", data);
	}

	@OperationLogger("删除附件")
	@DeleteMapping("/admin/files/{id}")
	public Result delete(@PathVariable Long id) {
		fileService.deleteForAdmin(id);
		return Result.ok("已删除");
	}
}
