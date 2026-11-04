package com.coolskyai.modules.file.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.file.entity.FileInfo;

/**
 * @Description: 文件元数据持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface FileInfoMapper {

	@Insert("INSERT INTO file (file_uuid, owner_id, biz_type, original_name, storage_type, bucket, object_key, " +
			"file_size, mime_type, sha256, is_public, status, create_time, update_time) " +
			"VALUES (#{fileUuid}, #{ownerId}, #{bizType}, #{originalName}, #{storageType}, #{bucket}, #{objectKey}, " +
			"#{fileSize}, #{mimeType}, #{sha256}, #{isPublic}, 'NORMAL', NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(FileInfo fileInfo);

	@Select("SELECT * FROM file WHERE id = #{id} AND status = 'NORMAL'")
	FileInfo findById(@Param("id") Long id);

	/**
	 * 迁移用：历史上私有文件与公开图片混在同一目录，需搬进 _private/
	 */
	@Select("SELECT * FROM file WHERE is_public = 0 AND object_key NOT LIKE '_private/%'")
	java.util.List<FileInfo> listPrivateLegacy();

	@Update("UPDATE file SET object_key = #{objectKey}, update_time = NOW() WHERE id = #{id}")
	int updateObjectKey(@Param("id") Long id, @Param("objectKey") String objectKey);

	@Select("SELECT * FROM file WHERE file_uuid = #{uuid} AND status = 'NORMAL'")
	FileInfo findByUuid(@Param("uuid") String uuid);

	@Insert("INSERT INTO file_version (file_id, version, object_key, file_size, sha256, change_log, create_time) " +
			"SELECT #{fileId}, IFNULL(MAX(version), 0) + 1, #{objectKey}, #{fileSize}, #{sha256}, #{changeLog}, NOW() " +
			"FROM file_version WHERE file_id = #{fileId}")
	int insertVersion(@Param("fileId") Long fileId,
	                  @Param("objectKey") String objectKey,
	                  @Param("fileSize") Long fileSize,
	                  @Param("sha256") String sha256,
	                  @Param("changeLog") String changeLog);

	@Insert("INSERT INTO download_record (file_id, user_id, ip, user_agent, create_time) " +
			"VALUES (#{fileId}, #{userId}, #{ip}, #{userAgent}, NOW())")
	int insertDownload(@Param("fileId") Long fileId,
	                   @Param("userId") Long userId,
	                   @Param("ip") String ip,
	                   @Param("userAgent") String userAgent);
}
