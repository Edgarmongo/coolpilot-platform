package com.coolskyai.modules.social.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 社交账号（关注我）
 *
 * 与 site_setting type=2 的区别：那张表只有「一行一个链接」，
 * 这里多了二维码、排序、启用开关 —— 引流真正需要的是这三个。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SocialAccount {
	private Long id;
	/** GITHUB/TELEGRAM/QQ/BILIBILI/NETEASE/EMAIL/WECHAT/DOUYIN/XIAOHONGSHU/ZHIHU/CUSTOM */
	private String platform;
	private String accountName;
	private String intro;
	/** 主页链接；纯二维码平台（如微信群）可以留空 */
	private String homeUrl;
	/** 二维码图片地址 */
	private String qrUrl;
	/** 前端图标标识，与 platform 分开是为了允许「同平台多个号」用不同图标 */
	private String icon;
	private Integer sort;
	private Boolean enabled;
	private Date createTime;
	private Date updateTime;
}
