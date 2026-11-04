package com.coolskyai.modules.ai.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.coolskyai.modules.ai.entity.KbChunk;
import com.coolskyai.modules.ai.entity.KbDocument;

import java.util.Date;
import java.util.List;

/**
 * @Description: 知识库持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface KbMapper {

	@Delete("DELETE FROM kb_chunk")
	int deleteAllChunks();

	@Delete("DELETE FROM kb_document")
	int deleteAllDocuments();

	@Insert("INSERT INTO kb_document (kb_id, source_type, source_id, product_id, title, url, is_public, status, " +
			"chunk_count, update_time) VALUES (#{kbId}, #{sourceType}, #{sourceId}, #{productId}, #{title}, #{url}, " +
			"#{isPublic}, #{status}, #{chunkCount}, NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertDocument(KbDocument document);

	@Insert("INSERT INTO kb_chunk (doc_id, kb_id, chunk_index, content, product_id, is_public, embedding, dim, model, " +
			"create_time) VALUES (#{docId}, #{kbId}, #{chunkIndex}, #{content}, #{productId}, #{isPublic}, " +
			"#{embedding}, #{dim}, #{model}, NOW())")
	int insertChunk(KbChunk chunk);

	@Select("SELECT * FROM kb_document")
	List<KbDocument> listDocuments();

	@Select("SELECT * FROM kb_chunk")
	List<KbChunk> listAllChunks();

	@Select("SELECT COUNT(*) FROM kb_document")
	int countDocuments();

	@Select("SELECT COUNT(*) FROM kb_chunk")
	int countChunks();

	@Select("SELECT MAX(update_time) FROM kb_document")
	Date lastIndexedAt();

	@Select("SELECT COUNT(*) FROM kb_document WHERE source_type = #{sourceType}")
	int countBySourceType(@Param("sourceType") String sourceType);
}
