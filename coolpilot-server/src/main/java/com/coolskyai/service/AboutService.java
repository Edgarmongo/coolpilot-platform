package com.coolskyai.service;

import java.util.Map;

public interface AboutService {
	/**
	 * @param lang zh-CN / en；英文站优先取 titleEn / contentEn，为空回落中文
	 */
	Map<String, String> getAboutInfo(String lang);

	Map<String, String> getAboutSetting();

	void updateAbout(Map<String, String> map);

	boolean getAboutCommentEnabled();
}
