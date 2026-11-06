package com.coolskyai.modules.file.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.modules.file.entity.FileInfo;
import com.coolskyai.modules.file.mapper.FileInfoMapper;
import com.coolskyai.modules.file.mapper.FileReferenceMapper;
import com.coolskyai.modules.product.entity.ProductResource;
import com.coolskyai.modules.product.mapper.ProductResourceMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * @Description: 文件服务
 *
 * 设计要点：
 * 1. 数据库只存 storage_type + bucket + object_key，不存完整 URL，便于后续换存储/换域名。
 * 2. 私有文件不暴露直链，下载必须先换一次性 token（5 分钟有效）。
 * 3. 非公开文件下载时校验归属：公开文件、文件所有者、或持有该商品权益的用户可下载。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class FileService {
	private static final long TOKEN_TTL_MS = 5 * 60 * 1000L;
	/**
	 * 私有文件的存储前缀。
	 * /image/** 映射的是整个上传目录，如果不做隔离，付费资源的直链可被匿名下载。
	 * 放到 _private/ 下并由 ImageGuardInterceptor 拒绝该前缀，私有文件就只能走
	 * /file/download?token= 这条带权益校验的通道。
	 */
	public static final String PRIVATE_PREFIX = "_private/";
	/**
	 * 允许上传的扩展名，避免上传可执行文件。
	 *
	 * 故意不含 svg：SVG 是 XML，可以内嵌 <script>，而公开文件走 /image/** 直链、
	 * 与站点同源。任意访客上传一个恶意 SVG 再发给管理员，管理员点开就在本站源下
	 * 执行脚本，能读到 localStorage 里的后台 token —— 一条完整的接管链路。
	 * 项目里没有任何地方需要上传 SVG（平台图标是内置的 path），所以直接排除。
	 */
	private static final List<String> ALLOWED_EXT = Arrays.asList(
			"jpg", "jpeg", "png", "gif", "webp", "bmp",
			"pdf", "md", "txt", "zip", "rar", "7z", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
			"mp4", "mp3", "wav");
	private static final long MAX_SIZE = 100L * 1024 * 1024;

	@Autowired
	private FileInfoMapper fileInfoMapper;
	@Autowired
	private FileReferenceMapper fileReferenceMapper;
	@Autowired
	private ProductResourceMapper productResourceMapper;
	@Autowired
	private com.coolskyai.modules.service.mapper.DeliveryMapper deliveryMapper;
	@Autowired
	private com.coolskyai.modules.entitlement.service.EntitlementService entitlementService;

	@Value("${upload.file.path}")
	private String uploadPath;

	@Value("${token.secretKey}")
	private String secretKey;

	/**
	 * 上传文件（当前实现仅支持本地存储）
	 */
	public FileInfo upload(MultipartFile multipartFile, String bizType, Long ownerId, boolean isPublic) throws Exception {
		if (multipartFile == null || multipartFile.isEmpty()) {
			throw new BadRequestException("请选择要上传的文件");
		}
		if (multipartFile.getSize() > MAX_SIZE) {
			throw new BadRequestException("文件不能超过 100MB");
		}
		String originalName = multipartFile.getOriginalFilename() == null ? "file" : multipartFile.getOriginalFilename();
		String ext = "";
		int dot = originalName.lastIndexOf('.');
		if (dot > -1) {
			ext = originalName.substring(dot + 1).toLowerCase();
		}
		if (!ALLOWED_EXT.contains(ext)) {
			throw new BadRequestException("不支持的文件类型：" + ext);
		}

		String uuid = UUID.randomUUID().toString().replace("-", "");
		String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
		String dir = (isPublic ? "" : PRIVATE_PREFIX) + datePath;
		String objectKey = dir + "/" + uuid + "." + ext;

		File folder = new File(uploadPath, dir);
		if (!folder.exists() && !folder.mkdirs()) {
			throw new IllegalStateException("无法创建上传目录：" + folder.getAbsolutePath());
		}
		File target = new File(uploadPath, objectKey);
		multipartFile.transferTo(target);

		byte[] bytes = Files.readAllBytes(target.toPath());
		FileInfo info = new FileInfo();
		info.setFileUuid(uuid);
		info.setOwnerId(ownerId);
		info.setBizType(bizType == null ? "OTHER" : bizType);
		info.setOriginalName(originalName);
		info.setStorageType("LOCAL");
		info.setBucket("");
		info.setObjectKey(objectKey);
		info.setFileSize((long) bytes.length);
		info.setMimeType(multipartFile.getContentType() == null ? "" : multipartFile.getContentType());
		info.setSha256(sha256(bytes));
		info.setIsPublic(isPublic);
		fileInfoMapper.insert(info);
		return info;
	}

	/**
	 * 签发一次性下载 token
	 *
	 * payload = fileId:userId:expireAt:privileged
	 * privileged=1 表示签发时是站点所有者，下载阶段不再重复校验归属
	 * （后台需要查看用户上传的付款凭证截图，而下载请求本身不带登录头）
	 */
	public String issueDownloadToken(Long fileId, Long userId) {
		long expireAt = System.currentTimeMillis() + TOKEN_TTL_MS;
		boolean privileged = com.coolskyai.util.CurrentUser.isOwner();
		String payload = fileId + ":" + userId + ":" + expireAt + ":" + (privileged ? "1" : "0");
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(encoded));
	}

	/**
	 * 解析并校验 token
	 */
	public Ticket parseToken(String token) {
		if (token == null || token.isEmpty()) {
			throw new BadRequestException("下载凭证不能为空");
		}
		String[] parts = token.split("\\.");
		if (parts.length != 2) {
			throw new BadRequestException("下载凭证格式错误");
		}
		byte[] expected = hmac(parts[0]);
		byte[] actual;
		try {
			actual = Base64.getUrlDecoder().decode(parts[1]);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("下载凭证格式错误");
		}
		if (!MessageDigest.isEqual(expected, actual)) {
			throw new BadRequestException("下载凭证无效");
		}
		String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
		String[] fields = payload.split(":");
		if (fields.length != 4) {
			throw new BadRequestException("下载凭证格式错误");
		}
		long expireAt = Long.parseLong(fields[2]);
		if (System.currentTimeMillis() > expireAt) {
			throw new BadRequestException("下载凭证已过期，请重新获取");
		}
		return new Ticket(Long.parseLong(fields[0]), Long.parseLong(fields[1]), "1".equals(fields[3]));
	}

	/**
	 * 下载凭证内容
	 */
	public static class Ticket {
		public final Long fileId;
		public final Long userId;
		public final boolean privileged;

		public Ticket(Long fileId, Long userId, boolean privileged) {
			this.fileId = fileId;
			this.userId = userId;
			this.privileged = privileged;
		}
	}

	/**
	 * 解析下载权限
	 *
	 * @return 文件实体；无权限时抛出异常
	 */
	public FileInfo resolveForDownload(Long fileId, Long userId, boolean privileged) {
		FileInfo info = fileInfoMapper.findById(fileId);
		if (info == null) {
			throw new BadRequestException("文件不存在或已删除");
		}
		if (privileged || Boolean.TRUE.equals(info.getIsPublic()) || info.getOwnerId().equals(userId)) {
			return info;
		}
		// 私有文件：若属于某个商品资源，持有该商品权益即可下载
		ProductResource resource = productResourceMapper.findByFileId(fileId);
		if (resource != null) {
			if (entitlementService.canDownload(userId, resource.getProductId())) {
				return info;
			}
			throw new BadRequestException("该文件属于付费商品，购买后即可下载");
		}
		// 交付物：项目客户本人可下载（交付文件是私有上传，没有商品权益可依托）
		if (userId != null && deliveryMapper.countByFileAndUser(fileId, userId) > 0) {
			return info;
		}
		throw new BadRequestException("无权下载该文件");
	}

	public FileInfo resolveForDownload(Long fileId, Long userId) {
		return resolveForDownload(fileId, userId, false);
	}

	public File physicalFile(FileInfo info) {
		return new File(uploadPath, info.getObjectKey());
	}

	// ---------------- 附件管理（后台，CP-BACKEND-02） ----------------

	public java.util.List<FileInfo> listForAdmin(String bizType, String keyword) {
		return fileInfoMapper.list(
				bizType == null || bizType.trim().isEmpty() ? null : bizType.trim(),
				keyword == null || keyword.trim().isEmpty() ? null : keyword.trim());
	}

	public java.util.Map<String, Object> stats() {
		java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
		data.put("count", fileInfoMapper.countAll());
		data.put("bytes", fileInfoMapper.sumSize());
		data.put("byBizType", fileInfoMapper.statsByBizType());
		return data;
	}

	/**
	 * 列出这个附件在哪里还被用着。
	 *
	 * @return 形如 ["文章《xxx》", "商品《xxx》"]，空表示没有任何地方引用
	 */
	public java.util.List<String> findReferences(FileInfo info) {
		String key = info.getObjectKey();
		java.util.List<String> refs = new java.util.ArrayList<>();
		// 正文里的图片
		for (String title : fileReferenceMapper.blogs(key)) {
			refs.add("文章《" + title + "》正文");
		}
		for (String title : fileReferenceMapper.products(key)) {
			refs.add("商品《" + title + "》详情");
		}
		for (String name : fileReferenceMapper.portfolios(key)) {
			refs.add("项目「" + name + "」详情");
		}
		for (String name : fileReferenceMapper.aboutEntries(key)) {
			refs.add("关于页「" + name + "」");
		}
		for (String name : fileReferenceMapper.moments(key)) {
			refs.add(name);
		}
		// 独立图片字段：封面、头像、二维码
		for (String title : fileReferenceMapper.blogCovers(key)) {
			refs.add("文章《" + title + "》封面");
		}
		for (String title : fileReferenceMapper.productCovers(key)) {
			refs.add("商品《" + title + "》封面");
		}
		for (String name : fileReferenceMapper.portfolioCovers(key)) {
			refs.add("项目「" + name + "」封面");
		}
		for (String name : fileReferenceMapper.userAvatars(key)) {
			refs.add("用户「" + name + "」头像");
		}
		for (String name : fileReferenceMapper.socialAccounts(key)) {
			refs.add("社交账号「" + name + "」二维码");
		}
		for (String name : fileReferenceMapper.siteSettings(key)) {
			refs.add("站点设置「" + name + "」");
		}
		return refs;
	}

	public java.util.List<String> findReferencesById(Long id) {
		FileInfo info = fileInfoMapper.findById(id);
		if (info == null) {
			throw new BadRequestException("附件不存在或已删除");
		}
		return findReferences(info);
	}

	/**
	 * 删除附件：先确认没被引用，再删磁盘文件，最后标记记录。
	 *
	 * 顺序是刻意的：删盘成功才改状态。反过来的话，删盘失败会留下
	 * 「列表里没了、文件还在磁盘上」的孤儿文件，而且再也没有入口能清掉它。
	 */
	public void deleteForAdmin(Long id) {
		FileInfo info = fileInfoMapper.findById(id);
		if (info == null) {
			throw new BadRequestException("附件不存在或已删除");
		}
		java.util.List<String> refs = findReferences(info);
		if (!refs.isEmpty()) {
			throw new BadRequestException("仍被引用，无法删除：" + String.join("、", refs));
		}
		File file = physicalFile(info);
		if (file.exists() && !file.delete()) {
			throw new BadRequestException("磁盘文件删除失败，请检查目录权限");
		}
		fileInfoMapper.markDeleted(id);
	}

	private byte[] hmac(String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			byte[] key = MessageDigest.getInstance("SHA-256")
					.digest(secretKey.getBytes(StandardCharsets.UTF_8));
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new IllegalStateException("下载凭证签名失败", e);
		}
	}

	private String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
