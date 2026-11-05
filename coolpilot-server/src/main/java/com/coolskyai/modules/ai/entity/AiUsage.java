package com.coolskyai.modules.ai.entity;

import java.time.LocalDateTime;

/**
 * @Description: 一次模型调用的用量记录
 *
 * 只记计量信息，不存请求与响应正文 —— 正文在 ai_message 里，
 * 这里再存一份既占空间又多一处可能泄露用户内容的地方。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
public class AiUsage {

	/** 场景：正式问答 */
	public static final String SCENE_CHAT = "CHAT";

	/** 场景：追问改写（把「那退款呢」补全成独立问题） */
	public static final String SCENE_REWRITE = "REWRITE";

	/** 场景：LLM 重排 */
	public static final String SCENE_RERANK = "RERANK";

	/** 场景：向量化（重建索引时批量产生） */
	public static final String SCENE_EMBED = "EMBED";

	/** 场景：后台点「测试连接」 */
	public static final String SCENE_TEST = "TEST";

	private Long id;

	private String scene;

	private String model;

	private Integer promptTokens;

	private Integer completionTokens;

	private Integer totalTokens;

	private Integer durationMs;

	private Boolean success;

	/**
	 * 1 = token 是按字符估算的（接口没返回 usage），统计时要能区分，
	 * 否则会把估算值当成精确值去对账。
	 */
	private Boolean estimated;

	private Long userId;

	private LocalDateTime createTime;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getScene() {
		return scene;
	}

	public void setScene(String scene) {
		this.scene = scene;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public void setPromptTokens(Integer promptTokens) {
		this.promptTokens = promptTokens;
	}

	public Integer getCompletionTokens() {
		return completionTokens;
	}

	public void setCompletionTokens(Integer completionTokens) {
		this.completionTokens = completionTokens;
	}

	public Integer getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(Integer totalTokens) {
		this.totalTokens = totalTokens;
	}

	public Integer getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(Integer durationMs) {
		this.durationMs = durationMs;
	}

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}

	public Boolean getEstimated() {
		return estimated;
	}

	public void setEstimated(Boolean estimated) {
		this.estimated = estimated;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}
}
