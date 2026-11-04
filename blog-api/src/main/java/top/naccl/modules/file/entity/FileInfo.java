package top.naccl.modules.file.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 文件元数据
 * 类名不用 File，避免与 java.io.File 冲突
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class FileInfo {
	private Long id;
	private String fileUuid;
	private Long ownerId;
	/** AVATAR / PRODUCT / DEMAND / DELIVERY / OTHER */
	private String bizType;
	private String originalName;
	/** LOCAL / MINIO / S3 */
	private String storageType;
	private String bucket;
	private String objectKey;
	private Long fileSize;
	private String mimeType;
	private String sha256;
	private Boolean isPublic;
	/** NORMAL / ARCHIVED */
	private String status;
	private Date createTime;
	private Date updateTime;
}
