package com.coolskyai.modules.nav.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.nav.entity.NavMenu;

import java.util.List;

/**
 * @Description: 导航菜单持久层
 *
 * 菜单总量只有个位数，不做分页、不做模糊查询：
 * 后台一次拉全量，前端表格直接渲染，比引入 PageHelper 更省事也更快。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Mapper
public interface NavMenuMapper {

	@Select("SELECT * FROM nav_menu WHERE visible = 1 ORDER BY sort ASC, id ASC")
	List<NavMenu> listVisible();

	@Select("SELECT * FROM nav_menu ORDER BY sort ASC, id ASC")
	List<NavMenu> listAll();

	@Select("SELECT * FROM nav_menu WHERE id = #{id}")
	NavMenu findById(@Param("id") Long id);

	@Insert("INSERT INTO nav_menu (label, path, sort, visible, target) " +
			"VALUES (#{label}, #{path}, #{sort}, #{visible}, #{target})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(NavMenu menu);

	@Update("UPDATE nav_menu SET label = #{label}, path = #{path}, sort = #{sort}, " +
			"visible = #{visible}, target = #{target} WHERE id = #{id}")
	int update(NavMenu menu);

	/**
	 * 只改排序：上移/下移一次要写两行，单独开放这个方法，
	 * 避免为了换个位置把 label/path 一起回写（并发下容易覆盖别人的编辑）
	 */
	@Update("UPDATE nav_menu SET sort = #{sort} WHERE id = #{id}")
	int updateSort(@Param("id") Long id, @Param("sort") Integer sort);

	@Update("UPDATE nav_menu SET visible = #{visible} WHERE id = #{id}")
	int updateVisible(@Param("id") Long id, @Param("visible") Boolean visible);

	@Delete("DELETE FROM nav_menu WHERE id = #{id}")
	int delete(@Param("id") Long id);

	@Select("SELECT COUNT(*) FROM nav_menu")
	int countAll();
}
