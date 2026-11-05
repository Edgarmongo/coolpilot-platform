package com.coolskyai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.coolskyai.constant.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @Description: 用户实体类
 * @Author: CoolSkyAI
 * @Date: 2020-07-19
 */

@NoArgsConstructor
@Getter
@Setter
@ToString
public class User implements UserDetails {
	private Long id;
	private String username;
	private String password;
	private String nickname;
	private String avatar;
	private String email;
	private Date createTime;
	private Date updateTime;
	private String role;
	/** 0 正常，1 已禁用（只有前台注册用户可被禁用） */
	private Integer status;
	/** 最后一次登录时间 */
	private Date lastLoginTime;

	@JsonIgnore
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		List<GrantedAuthority> authorityList = new ArrayList<>();
		// 兼容库里可能残留的历史角色值（ROLE_admin / ROLE_visitor）
		authorityList.add(new SimpleGrantedAuthority(Role.normalize(role)));
		return authorityList;
	}

	@JsonIgnore
	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@JsonIgnore
	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@JsonIgnore
	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@JsonIgnore
	@Override
	public boolean isEnabled() {
		return true;
	}
}
