package com.coolskyai.modules.ai.service;

import com.coolskyai.modules.ai.entity.AiSetting;
import com.coolskyai.modules.ai.mapper.AiSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: AI 模型配置的后台读写
 *
 * 读：给出「当前生效值」，密钥只给掩码；同时给出配置文件里的默认值，
 * 前端可以标出「这一项被你改过」。
 *
 * 写：密钥要真填了新值才覆盖，避免用户只改模型名、却在保存时把
 * 掩码串写回去，把 Key 覆盖成一串星号。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class AiSettingService {

	private static final Logger log = LoggerFactory.getLogger(AiSettingService.class);

	/**
	 * 前端回传的掩码里一定带这四个星号，用来判断「用户没动这一项」
	 */
	private static final String MASK_MARK = "****";

	private final AiSettingMapper settingMapper;

	private final AiRuntimeConfig runtimeConfig;

	private final AiCrypto crypto;

	private final Environment environment;

	public AiSettingService(AiSettingMapper settingMapper, AiRuntimeConfig runtimeConfig,
							AiCrypto crypto, Environment environment) {
		this.settingMapper = settingMapper;
		this.runtimeConfig = runtimeConfig;
		this.crypto = crypto;
		this.environment = environment;
	}

	public Map<String, Object> getForAdmin() {
		Map<String, String> values = new LinkedHashMap<>();
		Map<String, String> defaults = new LinkedHashMap<>();
		List<String> overridden = new ArrayList<>();

		for (String key : AiRuntimeConfig.KEY_TO_PROPERTY.keySet()) {
			boolean secret = AiRuntimeConfig.SECRET_KEYS.contains(key);
			String defaultValue = environment.getProperty(AiRuntimeConfig.KEY_TO_PROPERTY.get(key), "");
			// 默认值里的密钥同样要掩码：否则「当前值」遮住了，「默认值」又漏一遍，
			// 打开浏览器开发者工具就能看到 .env 里的 Key
			defaults.put(key, secret ? crypto.mask(defaultValue) : defaultValue);
			String effective = runtimeConfig.getRaw(key);
			// 密钥只给掩码，前端原样回传也不会泄露
			values.put(key, secret ? crypto.mask(effective) : effective);
			if (runtimeConfig.isOverridden(key)) {
				overridden.add(key);
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("values", values);
		result.put("defaults", defaults);
		result.put("overridden", overridden);
		// 当前是否可用，前端据此决定要不要提示「还没配模型」
		result.put("llmReady", !runtimeConfig.getRaw(AiRuntimeConfig.LLM_BASE_URL).isBlank()
				&& !runtimeConfig.getRaw(AiRuntimeConfig.LLM_MODEL).isBlank());
		return result;
	}

	/**
	 * 保存一组键值。未出现的键不动；非密钥填空表示「删除自定义值、回落默认」。
	 */
	@Transactional
	public void save(Map<String, String> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		int changed = 0;
		for (String key : AiRuntimeConfig.KEY_TO_PROPERTY.keySet()) {
			if (!values.containsKey(key)) {
				continue;
			}
			String raw = values.get(key);
			if (raw == null) {
				continue;
			}
			raw = raw.trim();

			if (AiRuntimeConfig.SECRET_KEYS.contains(key)) {
				// 空或掩码 = 没改；只有真填了新 Key 才覆盖
				if (raw.isEmpty() || raw.contains(MASK_MARK)) {
					continue;
				}
				upsert(key, crypto.encrypt(raw), true);
				changed++;
			} else if (raw.isEmpty()) {
				settingMapper.deleteByKey(key);
				changed++;
			} else {
				upsert(key, raw, false);
				changed++;
			}
		}

		runtimeConfig.refresh();
		log.info("AI 配置已更新，{} 项变更，已热加载", changed);
	}

	private void upsert(String key, String value, boolean encrypted) {
		AiSetting setting = new AiSetting();
		setting.setSettingKey(key);
		setting.setSettingValue(value);
		setting.setEncrypted(encrypted);
		settingMapper.upsert(setting);
	}
}
