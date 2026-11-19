package com.coolskyai.modules.opensource.enums;

/**
 * 项目所属技术领域。
 *
 * 为什么不建表：5 个值、单人运营、一年不变，建表 + 管理页 + 关联查询的成本
 * 远大于收益。值域写在这里，前台按 i18n 映射中文，接口只传 code。
 *
 * 注意：这里回答的是「这个项目属于哪个技术领域」，
 * 不是「从什么角度分析」——后者是文章的 relation_type，两者不要混。
 */
public enum OssCategory {

	AI_PROJECT("ai-project", "AI 开源项目", "AI Projects"),
	AGENT("agent", "AI Agent 与自动化", "Agents & Automation"),
	DATA_SEARCH("data-search", "数据采集与搜索", "Data & Search"),
	DEVTOOL("devtool", "开发者工具", "Developer Tools"),
	ENTERPRISE("enterprise", "企业级开源软件", "Enterprise OSS");

	private final String code;
	private final String labelZh;
	private final String labelEn;

	OssCategory(String code, String labelZh, String labelEn) {
		this.code = code;
		this.labelZh = labelZh;
		this.labelEn = labelEn;
	}

	public String getCode() {
		return code;
	}

	public String getLabelZh() {
		return labelZh;
	}

	public String getLabelEn() {
		return labelEn;
	}

	/** 找不到返回 null，由调用方决定是回退默认值还是报错 */
	public static OssCategory of(String code) {
		if (code == null) {
			return null;
		}
		for (OssCategory item : values()) {
			if (item.code.equals(code)) {
				return item;
			}
		}
		return null;
	}

	public static boolean isValid(String code) {
		return of(code) != null;
	}

	/** 数据库默认值，写错 code 时的兜底领域 */
	public static final String DEFAULT_CODE = "ai-project";
}
