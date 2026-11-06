package com.coolskyai.modules.ai.service;

import com.coolskyai.modules.ai.entity.AiSetting;
import com.coolskyai.modules.ai.mapper.AiSettingMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Description: AI 运行时配置
 *
 * 两层来源，后者优先：
 *   1. application-dev.properties 里的 ai.*（部署侧 .env 注入的默认值）
 *   2. ai_setting 表（后台在线改）
 *
 * 表里没有的键一律回落配置文件，所以升级这一版不需要先灌数据，
 * 之前靠 .env 跑起来的部署行为完全不变。
 *
 * 用进程内快照而不是 Redis：配置极少变、当前是单实例部署，
 * 省掉序列化与失效同步的复杂度。改完调 refresh() 立即可见。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Component
public class AiRuntimeConfig {

	private static final Logger log = LoggerFactory.getLogger(AiRuntimeConfig.class);

	/** 对话模型 */
	public static final String LLM_BASE_URL = "llm.baseUrl";
	public static final String LLM_API_KEY = "llm.apiKey";
	public static final String LLM_MODEL = "llm.model";
	public static final String LLM_TIMEOUT = "llm.timeoutSeconds";
	public static final String LLM_MAX_TOKENS = "llm.maxTokens";
	public static final String LLM_TEMPERATURE = "llm.temperature";
	public static final String LLM_EXTRA_BODY = "llm.extraBody";

	/** 向量模型 */
	public static final String EMB_PROVIDER = "embedding.provider";
	public static final String EMB_BASE_URL = "embedding.baseUrl";
	public static final String EMB_API_KEY = "embedding.apiKey";
	public static final String EMB_MODEL = "embedding.model";
	public static final String EMB_DIM = "embedding.dim";
	public static final String EMB_BATCH = "embedding.batchSize";

	/** 重排模型 */
	public static final String RERANK_PROVIDER = "rerank.provider";
	public static final String RERANK_BASE_URL = "rerank.baseUrl";
	public static final String RERANK_API_KEY = "rerank.apiKey";
	public static final String RERANK_MODEL = "rerank.model";
	public static final String RERANK_CANDIDATES = "rerank.candidates";
	public static final String RERANK_STRICTNESS = "rerank.strictness";

	/**
	 * 键 → 配置文件属性。新增可热改项时两边都要加，否则读到的永远是空。
	 */
	public static final Map<String, String> KEY_TO_PROPERTY;

	/**
	 * 需要加密落库的键。前端回传时是掩码，只有真填了新值才覆盖。
	 */
	public static final Set<String> SECRET_KEYS = Set.of(LLM_API_KEY, EMB_API_KEY, RERANK_API_KEY);

	static {
		Map<String, String> map = new LinkedHashMap<>();
		map.put(LLM_BASE_URL, "ai.llm.base-url");
		map.put(LLM_API_KEY, "ai.llm.api-key");
		map.put(LLM_MODEL, "ai.llm.model");
		map.put(LLM_TIMEOUT, "ai.llm.timeout-seconds");
		map.put(LLM_MAX_TOKENS, "ai.llm.max-tokens");
		map.put(LLM_TEMPERATURE, "ai.llm.temperature");
		map.put(LLM_EXTRA_BODY, "ai.llm.extra-body");

		map.put(EMB_PROVIDER, "ai.embedding.provider");
		map.put(EMB_BASE_URL, "ai.embedding.base-url");
		map.put(EMB_API_KEY, "ai.embedding.api-key");
		map.put(EMB_MODEL, "ai.embedding.model");
		map.put(EMB_DIM, "ai.embedding.dim");
		map.put(EMB_BATCH, "ai.embedding.batch-size");

		map.put(RERANK_PROVIDER, "ai.rerank.provider");
		map.put(RERANK_BASE_URL, "ai.rerank.base-url");
		map.put(RERANK_API_KEY, "ai.rerank.api-key");
		map.put(RERANK_MODEL, "ai.rerank.model");
		map.put(RERANK_CANDIDATES, "ai.rerank.candidates");
		map.put(RERANK_STRICTNESS, "ai.rerank.strictness");
		KEY_TO_PROPERTY = Map.copyOf(map);
	}

	private final Environment environment;

	private final AiSettingMapper settingMapper;

	private final AiCrypto crypto;

	/**
	 * 数据库覆盖值快照。整体替换而不是原地改，读的时候不用加锁。
	 */
	private volatile Map<String, String> overrides = Map.of();

	/**
	 * 上一次加载时的变更指纹。用来判断「库里是否被人改过」——
	 * 直接 SQL 改配置不会经过本应用，只能靠轮询发现。
	 */
	private volatile String lastFingerprint;

	private ScheduledExecutorService poller;

	public AiRuntimeConfig(Environment environment, AiSettingMapper settingMapper, AiCrypto crypto) {
		this.environment = environment;
		this.settingMapper = settingMapper;
		this.crypto = crypto;
	}

	@PostConstruct
	void init() {
		try {
			refresh();
		} catch (Exception e) {
			// 表还没建（迁移没跑）时不能让应用起不来，按配置文件运行即可
			log.warn("加载 AI 运行时配置失败，本次按配置文件默认值运行：{}", e.getMessage());
		}
		startPoller();
	}

	/**
	 * 轮询库里的变更。
	 *
	 * 没有它的话，直接 SQL 改 ai_setting 完全不生效（内存里是快照），
	 * 只能重启容器 —— 运维改一个 Key 要停一次服务，代价太高。
	 * 每次只查「行数 + 最后修改时间」，10 秒一次的开销可以忽略。
	 *
	 * 间隔用 ai.config.poll-seconds 控制，设 0 关闭（多实例部署时可关掉，
	 * 改为通过接口改配置）。
	 */
	private void startPoller() {
		int seconds = environment.getProperty("ai.config.poll-seconds", Integer.class, 10);
		if (seconds <= 0) {
			log.info("AI 配置轮询已关闭（ai.config.poll-seconds=0）");
			return;
		}
		poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "ai-config-poller");
			// 守护线程：别拦住 JVM 退出
			thread.setDaemon(true);
			return thread;
		});
		poller.scheduleWithFixedDelay(this::refreshIfChanged, seconds, seconds, TimeUnit.SECONDS);
		log.info("AI 配置轮询已启动：每 {} 秒检查一次 ai_setting", seconds);
	}

	/**
	 * 指纹变了才重新加载，避免每次轮询都重建一遍 Map
	 */
	public void refreshIfChanged() {
		try {
			String current = settingMapper.fingerprint();
			if (current != null && current.equals(lastFingerprint)) {
				return;
			}
			refresh();
			log.info("检测到 ai_setting 变更（指纹 {}），AI 配置已重新加载", current);
		} catch (Exception e) {
			// 库抖动或表被删：降级为 debug，别周期性刷错误日志
			log.debug("AI 配置轮询失败：{}", e.getMessage());
		}
	}

	@PreDestroy
	void shutdown() {
		if (poller != null) {
			poller.shutdownNow();
		}
	}

	/**
	 * 从数据库重读覆盖值。保存配置后调用，立即生效。
	 */
	public synchronized void refresh() {
		// 先记指纹再读内容：如果中途有人改，下次轮询会发现指纹不一致再刷一遍，
		// 不会出现「内容读了、指纹记成新的」导致漏掉一次变更
		String fingerprint = null;
		try {
			fingerprint = settingMapper.fingerprint();
		} catch (Exception e) {
			log.debug("读取 ai_setting 指纹失败：{}", e.getMessage());
		}
		List<AiSetting> settings = settingMapper.getList();
		Map<String, String> merged = new HashMap<>(settings.size());
		for (AiSetting setting : settings) {
			String value = Boolean.TRUE.equals(setting.getEncrypted())
					? crypto.decrypt(setting.getSettingValue())
					: setting.getSettingValue();
			// 解密失败（换过 JWT_SECRET）当作没配，避免拿乱码去请求
			if (value != null) {
				merged.put(setting.getSettingKey(), value);
			}
		}
		this.overrides = merged;
		this.lastFingerprint = fingerprint;
	}

	public String get(String key) {
		String override = overrides.get(key);
		if (override != null && !override.isBlank()) {
			return override.trim();
		}
		return environment.getProperty(KEY_TO_PROPERTY.getOrDefault(key, ""), "");
	}

	public String getRaw(String key) {
		String value = get(key);
		return value == null ? "" : value;
	}

	public int getInt(String key, int fallback) {
		String value = getRaw(key);
		if (value.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			log.warn("配置 {} 不是整数（{}），使用默认值 {}", key, value, fallback);
			return fallback;
		}
	}

	public double getDouble(String key, double fallback) {
		String value = getRaw(key);
		if (value.isBlank()) {
			return fallback;
		}
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			log.warn("配置 {} 不是数字（{}），使用默认值 {}", key, value, fallback);
			return fallback;
		}
	}

	/**
	 * 当前生效值是否来自数据库（前端用来标「已改」）
	 */
	public boolean isOverridden(String key) {
		String override = overrides.get(key);
		return override != null && !override.isBlank();
	}
}
