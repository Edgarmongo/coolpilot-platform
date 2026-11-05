package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import com.coolskyai.constant.Role;
import com.coolskyai.entity.User;

import java.util.List;
import java.util.Map;

/**
 * @Description: 用户持久层接口
 * @Author: CoolSkyAI
 * @Date: 2020-07-19
 */
@Mapper
@Repository
public interface UserMapper {
	User findByUsername(String username);

	User findById(Long id);

	/**
	 * 平台所有者 id，用于把后台事件写成站内消息。
	 * 只取第一条：数据上应当只有一个 ROLE_owner。
	 */
	@Select("SELECT id FROM user WHERE role = #{role} ORDER BY id LIMIT 1")
	Long findOwnerIdByRole(@Param("role") String role);

	int updateUserByUsername(String username, User user);

	/**
	 * 按主键更新资料。用 id 而不是 username，是为了让访客 token
	 * （subject 为 visitor:{userId}）也能定位到自己。
	 */
	@Update("UPDATE user SET nickname = #{nickname}, avatar = #{avatar}, email = #{email}, update_time = NOW() " +
			"WHERE id = #{id}")
	int updateProfile(@Param("id") Long id, @Param("nickname") String nickname,
	                  @Param("avatar") String avatar, @Param("email") String email);

	@Update("UPDATE user SET password = #{password}, update_time = NOW() WHERE id = #{id}")
	int updatePassword(@Param("id") Long id, @Param("password") String password);

	/**
	 * 记录最后登录时间
	 *
	 * 登录成功后才写，登出/失败不写 —— 这个字段的语义是「最近一次成功登录」。
	 */
	@Update("UPDATE user SET last_login_time = NOW() WHERE id = #{id}")
	int touchLastLogin(@Param("id") Long id);

	/**
	 * 启停前台账号
	 *
	 * 带 role 条件：即使有人伪造 id 也改不到所有者账号。
	 */
	@Update("UPDATE user SET status = #{status}, update_time = NOW() " +
			"WHERE id = #{id} AND role = '" + Role.USER + "'")
	int updateStatus(@Param("id") Long id, @Param("status") Integer status);

	/**
	 * 后台用户列表：带上订单数与累计支付金额，省得再点进订单页去数
	 *
	 * keyword / status 用「条件里判空」的写法，避免动态 SQL 拼接。
	 */
	@Select("SELECT u.id, u.username, u.nickname, u.email, u.avatar, u.role, u.status, " +
			"u.create_time AS createTime, u.last_login_time AS lastLoginTime, " +
			"(SELECT COUNT(*) FROM orders o WHERE o.user_id = u.id) AS orderCount, " +
			"(SELECT COALESCE(SUM(o.pay_amount), 0) FROM orders o WHERE o.user_id = u.id " +
			" AND o.status IN ('PAID', 'COMPLETED', 'REFUNDING', 'REFUNDED')) AS paidAmount " +
			"FROM user u WHERE u.role = 'ROLE_user' " +
			"AND (#{keyword} IS NULL OR #{keyword} = '' OR u.username LIKE CONCAT('%', #{keyword}, '%') " +
			" OR u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%')) " +
			"AND (#{status} IS NULL OR u.status = #{status}) " +
			"ORDER BY u.id DESC LIMIT #{offset}, #{size}")
	List<Map<String, Object>> pageUsers(@Param("keyword") String keyword, @Param("status") Integer status,
	                                    @Param("offset") int offset, @Param("size") int size);

	@Select("SELECT COUNT(*) FROM user u WHERE u.role = 'ROLE_user' " +
			"AND (#{keyword} IS NULL OR #{keyword} = '' OR u.username LIKE CONCAT('%', #{keyword}, '%') " +
			" OR u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%')) " +
			"AND (#{status} IS NULL OR u.status = #{status})")
	int countUsers(@Param("keyword") String keyword, @Param("status") Integer status);

	/**
	 * 新增用户（前台访客注册）
	 *
	 * @param user 用户信息，需已设置 role
	 * @return 影响行数
	 */
	int saveUser(User user);
}
