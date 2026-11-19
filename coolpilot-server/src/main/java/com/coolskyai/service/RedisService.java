package com.coolskyai.service;

import com.coolskyai.model.vo.BlogInfo;
import com.coolskyai.model.vo.PageResult;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public interface RedisService {
	/**
	 * 列表型缓存的读穿模式：命中直接返回，未命中调用 {@code loader} 回源并写回。
	 *
	 * <p>把「查缓存 → 判空 → 回源 → 写缓存」这四步收到一处，
	 * 各业务服务不必再各写一遍，也就不会各写错一遍。
	 *
	 * @param key    缓存键
	 * @param loader 回源逻辑，仅在未命中时执行
	 * @return 缓存或回源结果；loader 返回 null 时原样返回，不写缓存
	 */
	default <T> List<T> cacheThrough(String key, Supplier<List<T>> loader) {
		List<T> cached = getListByValue(key);
		if (cached != null) {
			return cached;
		}
		List<T> fresh = loader.get();
		if (fresh != null) {
			saveListToValue(key, fresh);
		}
		return fresh;
	}

	PageResult<BlogInfo> getBlogInfoPageResultByHash(String hash, Integer pageNum);

	void saveKVToHash(String hash, Object key, Object value);

	void saveMapToHash(String hash, Map map);

	Map getMapByHash(String hash);

	Object getValueByHashKey(String hash, Object key);

	void incrementByHashKey(String hash, Object key, int increment);

	void deleteByHashKey(String hash, Object key);

	<T> List<T> getListByValue(String key);

	<T> void saveListToValue(String key, List<T> list);

	<T> Map<String, T> getMapByValue(String key);

	<T> void saveMapToValue(String key, Map<String, T> map);

	<T> T getObjectByValue(String key, Class t);

	void incrementByKey(String key, int increment);

	void saveObjectToValue(String key, Object object);

	void saveValueToSet(String key, Object value);

	int countBySet(String key);

	void deleteValueBySet(String key, Object value);

	boolean hasValueInSet(String key, Object value);

	void deleteCacheByKey(String key);

	boolean hasKey(String key);

	void expire(String key, long time);
}
