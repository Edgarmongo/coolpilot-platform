package com.coolskyai.service.impl;

import com.coolskyai.model.vo.BlogInfo;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.service.RedisService;
import com.coolskyai.util.JacksonUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 读写原语。
 *
 * <p>这一层只做搬运，不含业务判断。三点约定：
 * <ul>
 *   <li>所有读写都走注入的 {@code jsonRedisTemplate}，序列化格式由它统一决定 ——
 *       换序列化器会让存量缓存全部读不出来，不要单独改某一个方法。</li>
 *   <li>RedisTemplate 的部分方法返回包装类型（Boolean / Long），key 不存在时可能为 null，
 *       这里统一兜底成基本类型，避免调用方拆箱时 NPE。</li>
 *   <li>自增因子必须为正：负数会让计数倒退，业务上只会是 bug。</li>
 * </ul>
 *
 * @author CoolSkyAI
 */
@Service
public class RedisServiceImpl implements RedisService {

	/** 过期时间统一按秒计 */
	private static final TimeUnit EXPIRE_UNIT = TimeUnit.SECONDS;

	private final RedisTemplate<Object, Object> jsonRedisTemplate;

	public RedisServiceImpl(RedisTemplate<Object, Object> jsonRedisTemplate) {
		this.jsonRedisTemplate = jsonRedisTemplate;
	}

	// ------------------------------------------------------------------
	// Hash
	// ------------------------------------------------------------------

	@Override
	public PageResult<BlogInfo> getBlogInfoPageResultByHash(String hash, Integer pageNum) {
		if (!Boolean.TRUE.equals(jsonRedisTemplate.opsForHash().hasKey(hash, pageNum))) {
			return null;
		}
		Object raw = jsonRedisTemplate.opsForHash().get(hash, pageNum);
		return JacksonUtils.convertValue(raw, PageResult.class);
	}

	@Override
	public void saveKVToHash(String hash, Object key, Object value) {
		jsonRedisTemplate.opsForHash().put(hash, key, value);
	}

	@Override
	public void saveMapToHash(String hash, Map map) {
		jsonRedisTemplate.opsForHash().putAll(hash, map);
	}

	@Override
	public Map getMapByHash(String hash) {
		return jsonRedisTemplate.opsForHash().entries(hash);
	}

	@Override
	public Object getValueByHashKey(String hash, Object key) {
		return jsonRedisTemplate.opsForHash().get(hash, key);
	}

	@Override
	public void incrementByHashKey(String hash, Object key, int increment) {
		requirePositive(increment);
		jsonRedisTemplate.opsForHash().increment(hash, key, increment);
	}

	@Override
	public void deleteByHashKey(String hash, Object key) {
		jsonRedisTemplate.opsForHash().delete(hash, key);
	}

	// ------------------------------------------------------------------
	// String / 对象
	// ------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<T> getListByValue(String key) {
		return (List<T>) jsonRedisTemplate.opsForValue().get(key);
	}

	@Override
	public <T> void saveListToValue(String key, List<T> list) {
		jsonRedisTemplate.opsForValue().set(key, list);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Map<String, T> getMapByValue(String key) {
		return (Map<String, T>) jsonRedisTemplate.opsForValue().get(key);
	}

	@Override
	public <T> void saveMapToValue(String key, Map<String, T> map) {
		jsonRedisTemplate.opsForValue().set(key, map);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public <T> T getObjectByValue(String key, Class t) {
		Object raw = jsonRedisTemplate.opsForValue().get(key);
		return (T) JacksonUtils.convertValue(raw, t);
	}

	@Override
	public void incrementByKey(String key, int increment) {
		requirePositive(increment);
		jsonRedisTemplate.opsForValue().increment(key, increment);
	}

	@Override
	public void saveObjectToValue(String key, Object object) {
		jsonRedisTemplate.opsForValue().set(key, object);
	}

	// ------------------------------------------------------------------
	// Set
	// ------------------------------------------------------------------

	@Override
	public void saveValueToSet(String key, Object value) {
		jsonRedisTemplate.opsForSet().add(key, value);
	}

	@Override
	public int countBySet(String key) {
		Long size = jsonRedisTemplate.opsForSet().size(key);
		return size == null ? 0 : size.intValue();
	}

	@Override
	public void deleteValueBySet(String key, Object value) {
		jsonRedisTemplate.opsForSet().remove(key, value);
	}

	@Override
	public boolean hasValueInSet(String key, Object value) {
		return Boolean.TRUE.equals(jsonRedisTemplate.opsForSet().isMember(key, value));
	}

	// ------------------------------------------------------------------
	// 通用
	// ------------------------------------------------------------------

	@Override
	public void deleteCacheByKey(String key) {
		jsonRedisTemplate.delete(key);
	}

	@Override
	public boolean hasKey(String key) {
		return Boolean.TRUE.equals(jsonRedisTemplate.hasKey(key));
	}

	@Override
	public void expire(String key, long time) {
		jsonRedisTemplate.expire(key, time, EXPIRE_UNIT);
	}

	/**
	 * 自增因子必须为正数。负数会让计数器倒退，正常业务里不存在这种需求，
	 * 出现只可能是调用写错了，直接抛比静默写坏数据安全。
	 */
	private void requirePositive(int increment) {
		if (increment < 0) {
			throw new RuntimeException("递增因子必须大于0");
		}
	}
}
