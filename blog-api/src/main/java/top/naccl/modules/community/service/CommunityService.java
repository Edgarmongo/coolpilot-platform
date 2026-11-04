package top.naccl.modules.community.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.naccl.exception.BadRequestException;
import top.naccl.model.vo.PageResult;
import top.naccl.modules.community.entity.Favorite;
import top.naccl.modules.community.entity.LikeRecord;
import top.naccl.modules.community.mapper.FavoriteMapper;
import top.naccl.modules.community.mapper.LikeRecordMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 收藏与点赞
 *
 * 幂等说明：收藏/点赞表都有 (user_id, target_type, target_id) 唯一索引，
 * 插入使用 INSERT IGNORE，重复提交不会产生脏数据也不会报错。
 *
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Service
public class CommunityService {
	private static final Set<String> FAVORITE_TYPES = Set.of("ARTICLE", "MOMENT", "PRODUCT");
	private static final Set<String> LIKE_TYPES = Set.of("ARTICLE", "MOMENT", "COMMENT");

	@Autowired
	private FavoriteMapper favoriteMapper;
	@Autowired
	private LikeRecordMapper likeRecordMapper;

	private void checkType(String targetType, Set<String> allowed) {
		if (targetType == null || !allowed.contains(targetType)) {
			throw new BadRequestException("不支持的目标类型：" + targetType);
		}
	}

	/**
	 * 添加收藏（幂等：已收藏时直接返回 true）
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean addFavorite(Long userId, String targetType, Long targetId) {
		checkType(targetType, FAVORITE_TYPES);
		if (targetId == null || targetId <= 0) {
			throw new BadRequestException("目标ID无效");
		}
		Favorite favorite = new Favorite();
		favorite.setUserId(userId);
		favorite.setTargetType(targetType);
		favorite.setTargetId(targetId);
		favoriteMapper.insert(favorite);
		return true;
	}

	/**
	 * 取消收藏（幂等：未收藏时直接返回 false）
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean removeFavorite(Long userId, String targetType, Long targetId) {
		checkType(targetType, FAVORITE_TYPES);
		favoriteMapper.delete(userId, targetType, targetId);
		return false;
	}

	public boolean isFavorited(Long userId, String targetType, Long targetId) {
		checkType(targetType, FAVORITE_TYPES);
		return favoriteMapper.exists(userId, targetType, targetId) > 0;
	}

	public PageResult<Favorite> pageFavorites(Long userId, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1 || pageSize > 50) ? 10 : pageSize;
		int total = favoriteMapper.countByUser(userId);
		List<Favorite> list = favoriteMapper.pageByUser(userId, (page - 1) * size, size);
		int totalPage = (total + size - 1) / size;
		return new PageResult<>(totalPage, total, list);
	}

	/**
	 * 点赞（幂等）
	 *
	 * @return {liked: true, count: 点赞总数}
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> addLike(Long userId, String targetType, Long targetId) {
		checkType(targetType, LIKE_TYPES);
		if (targetId == null || targetId <= 0) {
			throw new BadRequestException("目标ID无效");
		}
		LikeRecord record = new LikeRecord();
		record.setUserId(userId);
		record.setTargetType(targetType);
		record.setTargetId(targetId);
		int inserted = likeRecordMapper.insert(record);
		// 只有真正插入成功才累加，重复点赞不会被重复计数
		if (inserted > 0 && "MOMENT".equals(targetType)) {
			likeRecordMapper.changeMomentLikes(targetId, 1);
		}
		return likeResult(true, targetType, targetId);
	}

	/**
	 * 取消点赞（幂等）
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> removeLike(Long userId, String targetType, Long targetId) {
		checkType(targetType, LIKE_TYPES);
		int deleted = likeRecordMapper.delete(userId, targetType, targetId);
		if (deleted > 0 && "MOMENT".equals(targetType)) {
			likeRecordMapper.changeMomentLikes(targetId, -1);
		}
		return likeResult(false, targetType, targetId);
	}

	private Map<String, Object> likeResult(boolean liked, String targetType, Long targetId) {
		Map<String, Object> result = new HashMap<>(4);
		result.put("liked", liked);
		result.put("count", likeRecordMapper.countByTarget(targetType, targetId));
		return result;
	}

	public Map<String, Object> likeStatus(Long userId, String targetType, Long targetId) {
		checkType(targetType, LIKE_TYPES);
		Map<String, Object> result = new HashMap<>(4);
		result.put("liked", userId != null && likeRecordMapper.exists(userId, targetType, targetId) > 0);
		result.put("count", likeRecordMapper.countByTarget(targetType, targetId));
		return result;
	}
}
