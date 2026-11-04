package com.coolskyai.modules.file.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.file.entity.FileInfo;
import com.coolskyai.modules.file.mapper.FileInfoMapper;
import com.coolskyai.modules.file.service.FileService;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 文件上传与受控下载
 *
 * 下载流程：先 GET /file/token 换一次性 token（5 分钟），再 GET /file/download?token=
 * 私有文件不暴露可猜测的直链。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class FileController {
	@Autowired
	private FileService fileService;
	@Autowired
	private FileInfoMapper fileInfoMapper;
	@Autowired
	private AccountService accountService;

	/** 前台/图片直链的域名前缀，用于拼接可长期访问的图片地址 */
	@Value("${blog.api}")
	private String blogApi;

	@OperationLogger("上传文件")
	@PostMapping("/admin/file/upload")
	public Result upload(@RequestParam("file") MultipartFile multipartFile,
	                     @RequestParam(defaultValue = "PRODUCT") String bizType,
	                     @RequestParam(defaultValue = "false") boolean isPublic) throws Exception {
		Long ownerId = accountService.requireUserId();
		FileInfo info = fileService.upload(multipartFile, bizType, ownerId, isPublic);
		Map<String, Object> data = new HashMap<>(8);
		data.put("fileId", info.getId());
		data.put("fileUuid", info.getFileUuid());
		data.put("fileName", info.getOriginalName());
		data.put("fileSize", info.getFileSize());
		data.put("mimeType", info.getMimeType());
		data.put("isPublic", info.getIsPublic());
		// /image/** 映射到上传目录，拼出可直接放进 Markdown 的地址
		data.put("url", blogApi + "/image/" + info.getObjectKey());
		return Result.ok("上传成功", data);
	}

	/**
	 * 用户侧上传（付款凭证截图等），一律私有
	 */
	@PostMapping("/file/upload")
	public Result uploadByUser(@RequestParam("file") MultipartFile multipartFile,
	                           @RequestParam(defaultValue = "PROOF") String bizType) throws Exception {
		Long userId = accountService.requireUserId();
		String type = ("PROOF".equals(bizType) || "AVATAR".equals(bizType)) ? bizType : "OTHER";
		FileInfo info = fileService.upload(multipartFile, type, userId, false);
		Map<String, Object> data = new HashMap<>(8);
		data.put("fileId", info.getId());
		data.put("fileName", info.getOriginalName());
		data.put("fileSize", info.getFileSize());
		data.put("previewToken", fileService.issueDownloadToken(info.getId(), userId));
		return Result.ok("上传成功", data);
	}

	/**
	 * 换取一次性下载 token
	 */
	@GetMapping("/file/token")
	public Result token(@RequestParam Long fileId) {
		Long userId = accountService.requireUserId();
		/*
		 * 先确认有权限，再签发凭证。
		 * 站点所有者例外：后台需要查看用户上传的付款凭证，这类文件不属于自己也无商品权益。
		 */
		if (!com.coolskyai.util.CurrentUser.isOwner()) {
			fileService.resolveForDownload(fileId, userId);
		}
		Map<String, Object> data = new HashMap<>(4);
		data.put("token", fileService.issueDownloadToken(fileId, userId));
		data.put("expireSeconds", 300);
		return Result.ok("OK", data);
	}

	/**
	 * 下载（凭证自带身份，无需登录头）
	 */
	@GetMapping("/file/download")
	public ResponseEntity<Resource> download(@RequestParam String token,
	                                         jakarta.servlet.http.HttpServletRequest request) {
		FileService.Ticket ticket = fileService.parseToken(token);
		FileInfo info = fileService.resolveForDownload(ticket.fileId, ticket.userId, ticket.privileged);
		File file = fileService.physicalFile(info);
		if (!file.exists()) {
			return ResponseEntity.notFound().build();
		}

		fileInfoMapper.insertDownload(ticket.fileId, ticket.userId,
				com.coolskyai.util.IpAddressUtils.getIpAddress(request),
				request.getHeader("User-Agent"));

		String encodedName = URLEncoder.encode(info.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
				.body(new FileSystemResource(file));
	}
}
