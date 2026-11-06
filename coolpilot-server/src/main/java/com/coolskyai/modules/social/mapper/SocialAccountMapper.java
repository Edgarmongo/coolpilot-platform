package com.coolskyai.modules.social.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.social.entity.SocialAccount;

import java.util.List;

/**
 * @Description: 社交账号持久层
 *
 * 数量是个位数，不做分页；后台一次拉全量，表格直接渲染。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Mapper
public interface SocialAccountMapper {

	@Select("SELECT * FROM social_account WHERE enabled = 1 ORDER BY sort ASC, id ASC")
	List<SocialAccount> listEnabled();

	@Select("SELECT * FROM social_account ORDER BY sort ASC, id ASC")
	List<SocialAccount> listAll();

	@Select("SELECT * FROM social_account WHERE id = #{id}")
	SocialAccount findById(@Param("id") Long id);

	@Insert("INSERT INTO social_account (platform, account_name, intro, home_url, qr_url, icon, sort, enabled) " +
			"VALUES (#{platform}, #{accountName}, #{intro}, #{homeUrl}, #{qrUrl}, #{icon}, #{sort}, #{enabled})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(SocialAccount account);

	@Update("UPDATE social_account SET platform = #{platform}, account_name = #{accountName}, intro = #{intro}, " +
			"home_url = #{homeUrl}, qr_url = #{qrUrl}, icon = #{icon}, sort = #{sort}, enabled = #{enabled} " +
			"WHERE id = #{id}")
	int update(SocialAccount account);

	/**
	 * 只改排序：上移/下移一次写两行，单独开放避免整行回写覆盖别人的编辑
	 */
	@Update("UPDATE social_account SET sort = #{sort} WHERE id = #{id}")
	int updateSort(@Param("id") Long id, @Param("sort") Integer sort);

	@Update("UPDATE social_account SET enabled = #{enabled} WHERE id = #{id}")
	int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

	@Delete("DELETE FROM social_account WHERE id = #{id}")
	int delete(@Param("id") Long id);
}
