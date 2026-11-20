package com.coolskyai.modules.community.service;

import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.community.entity.Favorite;
import com.coolskyai.modules.community.entity.LikeRecord;
import com.coolskyai.modules.community.mapper.FavoriteMapper;
import com.coolskyai.modules.community.mapper.LikeRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收藏与点赞。
 *
 * <p>两张表都有 {@code (user_id, target_type, target_id)} 唯一索引，插入走 INSERT IGNORE ——
 * 这是幂等的根本：重复请求不会产生脏数据，也不会报错。
 *
 * <p>点赞数只在**真正插入/删除成功**时才变动。判断依据是影响行数，
 * 不是"接口被调用了"——否则连点两下会把计数加两次。
 *
 * @author CoolSkyAI
 */
@Service
public class CommunityService {

	/** 可收藏的对象类型 */
	private static final Set<String> FAVORITE_TYPES = Set.of("ARTICLE", "MOMENT", "PRODUCT", "AI_ANSWER");

	/** 可点赞的对象类型。注意评论可以点赞，但不能收藏 */
	private static final Set<String> LIKE_TYPES = Set.of("ARTICLE", "MOMENT", "COMMENT");

	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 50;

	private final FavoriteMapper favoriteMapper;
	private final LikeRecordMapper likeRecordMapper;

	public CommunityService(FavoriteMapper favoriteMapper, LikeRecordMapper likeRecordMapper) {
		this.favoriteMapper = favoriteMapper;
		this.likeRecordMapper = likeRecordMapper;
	}

	// ------------------------------------------------------------------
	// 收藏
	// ------------------------------------------------------------------

	/**
	 * 加收藏。已收藏时直接返回 true（幂等）。
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean addFavorite(Long userId, String targetType, Long targetId) {
		requireTarget(targetType, FAVORITE_TYPES, targetId);

		// AI 问答记录属于私人内容，只能收藏自己的
		if ("AI_ANSWER".equals(targetType) && favoriteMapper.countOwnedAnswer(targetId, userId) == 0) {
			throw new BadRequestException("只能收藏自己的问答记录");
		}

		Favorite favorite = new Favorite();
		favorite.setUserId(userId);
		favorite.setTargetType(targetType);
		favorite.setTargetId(targetId);
		favoriteMapper.insert(favorite);
		return true;
	}

	/**
	 * 取消收藏。未收藏时直接返回 false（幂等）。
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean removeFavorite(Long userId, String targetType, Long targetId) {
		requireTarget(targetType, FAVORITE_TYPES, targetId);
		favoriteMapper.delete(userId, targetType, targetId);
		return false;
	}

	public boolean isFavorited(Long userId, String targetType, Long targetId) {
		requireTarget(targetType, FAVORITE_TYPES, targetId);
		return favoriteMapper.exists(userId, targetType, targetId) > 0;
	}

	public PageResult<Favorite> pageFavorites(Long userId, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : pageSize;

		int total = favoriteMapper.countByUser(userId);
		List<Favorite> list = favoriteMapper.pageByUser(userId, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	// ------------------------------------------------------------------
	// 点赞
	// ------------------------------------------------------------------

	/**
	 * 点赞。返回最新的点赞总数，前端可直接覆盖显示。
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> addLike(Long userId, String targetType, Long targetId) {
		requireTarget(targetType, LIKE_TYPES, targetId);

		LikeRecord record = new LikeRecord();
		record.setUserId(userId);
		record.setTargetType(targetType);
		record.setTargetId(targetId);

		int inserted = likeRecordMapper.insert(record);
		// 重复点赞时 inserted = 0，此时不能动计数
		if (inserted > 0) {
			bumpMomentLikes(targetType, targetId, 1);
		}
		return likeSnapshot(true, targetType, targetId);
	}

	/**
	 * 取消点赞。
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> removeLike(Long userId, String targetType, Long targetId) {
		requireTarget(targetType, LIKE_TYPES, targetId);

		int deleted = likeRecordMapper.delete(userId, targetType, targetId);
		if (deleted > 0) {
			bumpMomentLikes(targetType, targetId, -1);
		}
		return likeSnapshot(false, targetType, targetId);
	}

	/**
	 * 查询当前用户是否点过赞，以及总数。
	 */
	public Map<String, Object> likeStatus(Long userId, String targetType, Long targetId) {
		requireTarget(targetType, LIKE_TYPES, targetId);
		boolean liked = userId != null && likeRecordMapper.exists(userId, targetType, targetId) > 0;
		return likeSnapshot(liked, targetType, targetId);
	}

	// ------------------------------------------------------------------
	// 内部
	// ------------------------------------------------------------------

	/**
	 * 类型与目标校验。两类不合法的提示文案不同，前端靠它区分是参数类型错还是 id 错。
	 */
	private void requireTarget(String targetType, Set<String> allowed, Long targetId) {
		if (targetType == null || !allowed.contains(targetType)) {
			throw new BadRequestException("不支持的目标类型：" + targetType);
		}
		if (targetId == null || targetId <= 0) {
			throw new BadRequestException("目标ID无效");
		}
	}

	/**
	 * 只有动态自己有冗余的点赞数需要同步；文章和评论的计数直接 count 出来，不存冗余。
	 */
	private void bumpMomentLikes(String targetType, Long targetId, int delta) {
		if ("MOMENT".equals(targetType)) {
			likeRecordMapper.changeMomentLikes(targetId, delta);
		}
	}

	private Map<String, Object> likeSnapshot(boolean liked, String targetType, Long targetId) {
		Map<String, Object> result = new HashMap<>(4);
		result.put("liked", liked);
		result.put("count", likeRecordMapper.countByTarget(targetType, targetId));
		return result;
	}
}
