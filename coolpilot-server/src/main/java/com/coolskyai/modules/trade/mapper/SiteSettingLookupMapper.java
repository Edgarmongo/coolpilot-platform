package com.coolskyai.modules.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @Description: 只读查询站点设置
 *
 * 收款码、PayPal 链接这类运营信息放数据库，后台可随时更换，不写死在代码里。
 * 这里只做读，不改写遗留的 site_setting 链路。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface SiteSettingLookupMapper {

	@Select("SELECT `value` FROM site_setting WHERE name_en = #{nameEn} LIMIT 1")
	String findValue(@Param("nameEn") String nameEn);
}
