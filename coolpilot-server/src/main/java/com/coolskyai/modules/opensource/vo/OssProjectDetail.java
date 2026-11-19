package com.coolskyai.modules.opensource.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目档案详情。
 *
 * 评估卡原文（assessmentJson）原样透传，不在后端解析：
 *   - 它是人工在后台填的 JSON，格式出错是可能的；
 *   - 用哪个 JSON 库、出错怎么兜底，属于展示层的事，放在页面里一处 try/catch 就够；
 *   - 后端解析后再序列化一次，等于多一次可能改变结构的往返。
 * 前台约定：解析失败时只把评估卡区域显示成「评估数据异常」，其余区块照常。
 */
@NoArgsConstructor
@Getter
@Setter
public class OssProjectDetail {

	private OssProjectCard project;

	private String repoUrl;
	private String officialUrl;
	private String docsUrl;

	/** description（Markdown）转成的 HTML */
	private String contentHtml;

	private List<OssArticleItem> articles = new ArrayList<>();

	/** 同标签的相关项目，最多 3 个 */
	private List<OssProjectCard> related = new ArrayList<>();

	/** 评估卡原始 JSON，见 docs/DATA-MODEL-OSS.md §2.6 */
	private String assessmentJson;
}
