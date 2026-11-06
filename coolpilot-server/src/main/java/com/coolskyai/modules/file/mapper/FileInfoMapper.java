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

	// ---------------- 附件管理（后台） ----------------

	/**
	 * 分页列表。bizType 与 keyword 都可空，空则不加条件。
	 * 用 <script> 而不是 XML：这条查询只有两个可选条件，放进 XML 反而要在
	 * 另一个文件里来回跳。
	 */
	@Select("<script>SELECT * FROM file WHERE status = 'NORMAL' " +
			"<if test='bizType != null and bizType != \"\"'> AND biz_type = #{bizType}</if> " +
			"<if test='keyword != null and keyword != \"\"'> AND original_name LIKE CONCAT('%', #{keyword}, '%')</if> " +
			"ORDER BY id DESC</script>")
	java.util.List<FileInfo> list(@Param("bizType") String bizType, @Param("keyword") String keyword);

	@Select("SELECT COUNT(*) FROM file WHERE status = 'NORMAL'")
	long countAll();

	@Select("SELECT IFNULL(SUM(file_size), 0) FROM file WHERE status = 'NORMAL'")
	long sumSize();

	@Select("SELECT biz_type AS bizType, COUNT(*) AS cnt, IFNULL(SUM(file_size), 0) AS bytes " +
			"FROM file WHERE status = 'NORMAL' GROUP BY biz_type ORDER BY cnt DESC")
	java.util.List<java.util.Map<String, Object>> statsByBizType();

	/**
	 * 软删：置为 DELETED 而不是删行。
	 * 下载记录、版本记录都还指着这条 id，删行会让历史数据悬空；
	 * 磁盘文件在 Service 里同步删掉，空间照常释放。
	 */
	@Update("UPDATE file SET status = 'DELETED', update_time = NOW() WHERE id = #{id}")
	int markDeleted(@Param("id") Long id);

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
