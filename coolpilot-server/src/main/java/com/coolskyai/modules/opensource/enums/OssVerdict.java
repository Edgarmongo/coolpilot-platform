package com.coolskyai.modules.opensource.enums;

/**
 * 结论档：五档，不给总分。
 *
 * 一个数字无法表达「在什么条件下值得做什么」，所以栏目的结论是一个档位 + 条件说明，
 * 而不是评分。判定条件见研究仓 COMMERCIAL-REVIEW-STANDARD.md §4。
 */
public enum OssVerdict {

	WATCH("watch", "值得持续关注", "Worth watching"),
	DEPLOY("deploy", "值得部署使用", "Worth deploying"),
	EXTEND("extend", "值得二次开发", "Worth building on"),
	COMMERCIAL("commercial", "值得商业化探索", "Worth exploring commercially"),
	HOLD("hold", "暂不建议投入", "Hold off");

	private final String code;
	private final String labelZh;
	private final String labelEn;

	OssVerdict(String code, String labelZh, String labelEn) {
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

	public static OssVerdict of(String code) {
		if (code == null) {
			return null;
		}
		for (OssVerdict item : values()) {
			if (item.code.equals(code)) {
				return item;
			}
		}
		return null;
	}

	public static boolean isValid(String code) {
		return of(code) != null;
	}
}
