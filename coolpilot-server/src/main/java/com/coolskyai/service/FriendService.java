package com.coolskyai.service;

import com.coolskyai.entity.Friend;
import com.coolskyai.model.vo.FriendInfo;

import java.util.List;

public interface FriendService {
	List<Friend> getFriendList();

	List<com.coolskyai.model.vo.Friend> getFriendVOList();

	void updateFriendPublishedById(Long friendId, Boolean published);

	void saveFriend(Friend friend);

	void updateFriend(com.coolskyai.model.dto.Friend friend);

	void deleteFriend(Long id);

	void updateViewsByNickname(String nickname);

	/**
	 * 浏览计数（按 IP + 昵称去重，1 小时内只算一次）
	 *
	 * @return true 表示本次真的 +1
	 */
	boolean addViewsOnce(String nickname, String ip);

	FriendInfo getFriendInfo(boolean cache, boolean md);

	void updateFriendInfoContent(String content);

	void updateFriendInfoCommentEnabled(Boolean commentEnabled);
}
