package com.coolskyai.modules.ai.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Description: 重排的严格度策略
 *
 * 「低于多少就该丢」没有唯一正确答案，取决于业务更怕漏还是更怕噪音。
 * 与其让部署方去猜两个抽象数字（min-keep / relative-floor），不如给三档语义：
 *
 *   严格：只留能直接回答问题的，可能漏掉部分相关片段
 *   平衡：默认，留直接相关 + 明显沾边的
 *   宽松：宁多勿漏，接受一些噪音
 *
 * 两项参数可以由预设推导，也可以在配置里单独覆盖 —— 覆盖时以显式值为准，
 * 这样「我就要 min-keep=5」这种需求不必被预设绑住。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Component
public class RerankPolicy {

	private static final Logger log = LoggerFactory.getLogger(RerankPolicy.class);

	@Value("${ai.rerank.strictness:balanced}")
	private String strictness;

	/** 留空表示按预设推导 */
	@Value("${ai.rerank.min-keep:}")
	private String minKeepRaw;

	/** 留空表示按预设推导 */
	@Value("${ai.rerank.relative-floor:}")
	private String relativeFloorRaw;

	/** 「模型说都不相关」时的兜底分数线，留空表示按预设推导 */
	@Value("${ai.rerank.empty-keep-score:}")
	private String emptyKeepScoreRaw;

	private int minKeep;
	private double relativeFloor;
	private double emptyKeepScore;
	private String instruction;

	@PostConstruct
	void init() {
		String preset = strictness == null ? "balanced" : strictness.trim().toLowerCase();
		int presetKeep;
		double presetFloor;
		double presetEmpty;
		switch (preset) {
			case "strict" -> {
				presetKeep = 1;
				presetFloor = 0.7;
				presetEmpty = 0.6;
				instruction = "只保留能直接回答问题的片段，沾边但没答到问题的不要输出。";
			}
			case "loose" -> {
				presetKeep = 3;
				presetFloor = 0.35;
				presetEmpty = 0.35;
				instruction = "保留与问题相关的片段，宁可多留一些，不要漏掉可能有用的内容。";
			}
			default -> {
				preset = "balanced";
				presetKeep = 2;
				presetFloor = 0.5;
				presetEmpty = 0.5;
				instruction = "只保留与问题直接相关的片段，仅共享「怎么」「如何」这类泛化词的不算相关。";
			}
		}
		this.minKeep = parseKeep(minKeepRaw, presetKeep);
		this.relativeFloor = parseFloor(relativeFloorRaw, presetFloor);
		this.emptyKeepScore = parseFloor(emptyKeepScoreRaw, presetEmpty);
		log.info("重排严格度：{}（minKeep={} relativeFloor={} emptyKeepScore={}）",
				preset, minKeep, relativeFloor, emptyKeepScore);
	}

	/**
	 * 截断后至少保留几条
	 */
	public int minKeep() {
		return minKeep;
	}

	/**
	 * http 重排的相对下限（0 表示关闭）
	 */
	public double relativeFloor() {
		return relativeFloor;
	}

	/**
	 * 「重排说都不相关」时，第一轮分数高于它才保留下限条数
	 *
	 * 严格档设得高（宁可空着也不硬答），宽松档设得低（宁可多给）。
	 */
	public double emptyKeepScore() {
		return emptyKeepScore;
	}

	/**
	 * 给对话模型重排用的判定口径
	 */
	public String instruction() {
		return instruction;
	}

	public String strictness() {
		return strictness;
	}

	private int parseKeep(String raw, int fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			int value = Integer.parseInt(raw.trim());
			return value < 0 ? fallback : value;
		} catch (NumberFormatException e) {
			log.warn("ai.rerank.min-keep 不是整数，按预设 {} 处理", fallback);
			return fallback;
		}
	}

	private double parseFloor(String raw, double fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			double value = Double.parseDouble(raw.trim());
			return value < 0 || value > 1 ? fallback : value;
		} catch (NumberFormatException e) {
			log.warn("ai.rerank.relative-floor 不是数字，按预设 {} 处理", fallback);
			return fallback;
		}
	}
}
