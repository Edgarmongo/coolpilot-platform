package com.coolskyai.modules.opensource.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 项目卡片：列表页、侧栏、相关项目共用。
 *
 * 只带列表需要的字段，正文与评估卡不进这个对象——
 * 列表一次要返回十几条，把 longtext 带进来会让响应体大出一个数量级。
 */
@NoArgsConstructor
@Getter
@Setter
public class OssProjectCard {

	private Long id;
	private String slug;
	private String name;
	private String summary;
	private String category;
	private String license;
	private String language;
	private String verdict;
	private Integer stars;
	private String coverUrl;
	private Boolean featured;
	private String researchStatus;
	private Integer articleCount;
	private Date lastVerifiedAt;
	private Date updateTime;
	private List<String> tags = new ArrayList<>();

	public void setTagsCsv(String csv) {
		this.tags = splitCsv(csv);
	}

	static List<String> splitCsv(String csv) {
		List<String> list = new ArrayList<>();
		if (csv == null || csv.isEmpty()) {
			return list;
		}
		for (String item : csv.split(",")) {
			String value = item.trim();
			if (!value.isEmpty()) {
				list.add(value);
			}
		}
		return list;
	}
}
