package com.coolskyai.modules.ai.mapper;

import com.coolskyai.modules.ai.entity.AiSetting;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Description: AI 运行时配置读写
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Mapper
public interface AiSettingMapper {

	@Select("SELECT id, setting_key, setting_value, encrypted, update_time FROM ai_setting")
	List<AiSetting> getList();

	/**
	 * 按主键键 upsert：保存时前端一次提交整组，逐个覆盖最省心
	 */
	@Insert("INSERT INTO ai_setting (setting_key, setting_value, encrypted) "
			+ "VALUES (#{settingKey}, #{settingValue}, #{encrypted}) "
			+ "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), "
			+ "encrypted = VALUES(encrypted)")
	int upsert(AiSetting setting);

	@Delete("DELETE FROM ai_setting WHERE setting_key = #{settingKey}")
	int deleteByKey(@Param("settingKey") String settingKey);

	/**
	 * 变更指纹：行数 + 最后修改时间。
	 *
	 * 轮询只查这一个很小的一行，比每次把整表读出来比对便宜得多；
	 * 新增/删除会改变行数，改值会推进 update_time（V20 里是 ON UPDATE CURRENT_TIMESTAMP）。
	 */
	@Select("SELECT CONCAT(COUNT(*), ':', IFNULL(MAX(update_time), '-')) FROM ai_setting")
	String fingerprint();
}
