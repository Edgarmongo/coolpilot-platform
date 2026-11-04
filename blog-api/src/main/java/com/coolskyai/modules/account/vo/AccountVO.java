package com.coolskyai.modules.account.vo;

import lombok.Getter;
import lombok.Setter;
import com.coolskyai.entity.User;

import java.util.Date;

/**
 * @Description: 当前登录用户信息（不含密码）
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Getter
@Setter
public class AccountVO {
	private Long id;
	private String username;
	private String nickname;
	private String avatar;
	private String email;
	private String role;
	private Date createTime;

	public static AccountVO of(User user) {
		AccountVO vo = new AccountVO();
		vo.setId(user.getId());
		vo.setUsername(user.getUsername());
		vo.setNickname(user.getNickname());
		vo.setAvatar(user.getAvatar());
		vo.setEmail(user.getEmail());
		vo.setRole(user.getRole());
		vo.setCreateTime(user.getCreateTime());
		return vo;
	}
}
