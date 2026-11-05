package com.coolskyai.modules.ai.entity;

import java.time.LocalDateTime;

/**
 * @Description: AI 运行时配置的一行
 *
 * 键用点分命名（llm.model / embedding.provider），与配置文件里的
 * ai.llm.model 一一对应，读的时候按同一个名字回落。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
public class AiSetting {

	private Long id;

	private String settingKey;

	private String settingValue;

	/**
	 * 1 = 值已加密（API Key 这类）。读取时解密，返回给前端时只给掩码。
	 */
	private Boolean encrypted;

	private LocalDateTime updateTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSettingKey() {
		return settingKey;
	}

	public void setSettingKey(String settingKey) {
		this.settingKey = settingKey;
	}

	public String getSettingValue() {
		return settingValue;
	}

	public void setSettingValue(String settingValue) {
		this.settingValue = settingValue;
	}

	public Boolean getEncrypted() {
		return encrypted;
	}

	public void setEncrypted(Boolean encrypted) {
		this.encrypted = encrypted;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}
}
