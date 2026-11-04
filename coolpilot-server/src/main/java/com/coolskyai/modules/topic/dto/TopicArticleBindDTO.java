package com.coolskyai.modules.topic.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * @Description: 专题文章绑定（整组替换）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class TopicArticleBindDTO {
	private List<Long> articleIds;
}
