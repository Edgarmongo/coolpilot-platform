package com.coolskyai.exception;

/**
 * @Description: 分享链接已过期
 *
 * 单独一个异常是为了让前端能区分「不存在/被撤销」和「已过期」：
 * 前者无解，后者告诉访问者「让作者重新分享一次」才有意义。
 * 状态码用 410（Gone）——资源曾经存在、现在没了，语义比 400 准。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public class ShareExpiredException extends RuntimeException {
	public ShareExpiredException(String message) {
		super(message);
	}
}
