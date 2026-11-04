package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
import com.coolskyai.entity.Friend;

import java.util.List;

/**
 * @Description: 友链持久层接口
 * @Author: CoolSkyAI
 * @Date: 2020-09-08
 */
@Mapper
@Repository
public interface FriendMapper {
	List<Friend> getFriendList();

	List<com.coolskyai.model.vo.Friend> getFriendVOList();

	int updateFriendPublishedById(Long id, Boolean published);

	int saveFriend(Friend friend);

	int updateFriend(com.coolskyai.model.dto.Friend friend);

	int deleteFriend(Long id);

	int updateViewsByNickname(String nickname);
}
