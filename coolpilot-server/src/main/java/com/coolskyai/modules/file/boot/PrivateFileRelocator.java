package com.coolskyai.modules.file.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import com.coolskyai.modules.file.entity.FileInfo;
import com.coolskyai.modules.file.mapper.FileInfoMapper;
import com.coolskyai.modules.file.service.FileService;

import java.io.File;
import java.util.List;

/**
 * @Description: 私有文件目录迁移（幂等）
 *
 * 早期版本把私有文件和公开图片混存于上传根目录，
 * 导致 /image/** 可以匿名下载付费资源（已实测确认）。
 *
 * 现在私有文件统一放到 _private/ 下、并由拦截器拒绝直连。
 * 这个 Runner 负责把历史文件搬到新位置并更新 object_key；
 * 已经迁移过的记录会被查询条件排除，可以反复启动。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class PrivateFileRelocator implements ApplicationRunner {
	private static final Logger logger = LoggerFactory.getLogger(PrivateFileRelocator.class);

	@Value("${upload.file.path}")
	private String uploadPath;

	@Autowired
	private FileInfoMapper fileInfoMapper;

	@Override
	public void run(ApplicationArguments args) {
		List<FileInfo> legacy;
		try {
			legacy = fileInfoMapper.listPrivateLegacy();
		} catch (Exception e) {
			// 迁移失败不应阻断启动，否则一次数据异常就再也起不来
			logger.warn("私有文件迁移跳过：{}", e.getMessage());
			return;
		}
		if (legacy == null || legacy.isEmpty()) {
			return;
		}
		int moved = 0;
		int missing = 0;
		for (FileInfo info : legacy) {
			String oldKey = info.getObjectKey();
			String newKey = FileService.PRIVATE_PREFIX + oldKey;
			File source = new File(uploadPath, oldKey);
			File target = new File(uploadPath, newKey);
			if (source.exists()) {
				File parent = target.getParentFile();
				if (parent != null && !parent.exists() && !parent.mkdirs()) {
					logger.warn("无法创建目录 {}，跳过文件 {}", parent, oldKey);
					continue;
				}
				if (!source.renameTo(target)) {
					logger.warn("移动失败 {} -> {}", oldKey, newKey);
					continue;
				}
				moved++;
			} else {
				// 磁盘上没有（可能已被删除），只把库里的路径改到新前缀，保持一致性
				missing++;
			}
			fileInfoMapper.updateObjectKey(info.getId(), newKey);
		}
		logger.info("私有文件迁移完成：移动 {} 个，记录路径修正 {} 个", moved, missing);
	}
}
