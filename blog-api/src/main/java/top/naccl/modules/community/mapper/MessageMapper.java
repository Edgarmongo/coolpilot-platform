package top.naccl.modules.community.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.naccl.modules.community.entity.Message;

import java.util.List;

/**
 * @Description: 站内消息持久层
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Mapper
public interface MessageMapper {

	@Insert("INSERT INTO message (user_id, msg_type, title, content, biz_type, biz_id, link_url, is_read, create_time) " +
			"VALUES (#{userId}, #{msgType}, #{title}, #{content}, #{bizType}, #{bizId}, #{linkUrl}, b'0', NOW())")
	int insert(Message message);

	@Select("SELECT * FROM message WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{offset}, #{size}")
	List<Message> pageByUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

	@Select("SELECT COUNT(*) FROM message WHERE user_id = #{userId}")
	int countByUser(@Param("userId") Long userId);

	@Select("SELECT COUNT(*) FROM message WHERE user_id = #{userId} AND is_read = b'0'")
	int countUnread(@Param("userId") Long userId);

	@Update("UPDATE message SET is_read = b'1', read_time = NOW() WHERE id = #{id} AND user_id = #{userId}")
	int markRead(@Param("id") Long id, @Param("userId") Long userId);
}
