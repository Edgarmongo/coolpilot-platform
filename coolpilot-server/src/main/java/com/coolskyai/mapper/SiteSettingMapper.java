package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
import com.coolskyai.entity.SiteSetting;

import java.util.List;

/**
 * @Description: 站点设置持久层接口
 * @Author: CoolSkyAI
 * @Date: 2020-08-03
 */
@Mapper
@Repository
public interface SiteSettingMapper {
	List<SiteSetting> getList();

	List<SiteSetting> getFriendInfo();

	String getWebTitleSuffix();

	/**
	 * 按 name_en 取单个设置值（通知收件邮箱等）
	 */
	@Select("SELECT value FROM site_setting WHERE name_en = #{nameEn} LIMIT 1")
	String getValueByNameEn(@Param("nameEn") String nameEn);

	int updateSiteSetting(SiteSetting siteSetting);

	int deleteSiteSettingById(Integer id);

	int saveSiteSetting(SiteSetting siteSetting);

	int updateFriendInfoContent(String content);

	int updateFriendInfoCommentEnabled(Boolean commentEnabled);
}
