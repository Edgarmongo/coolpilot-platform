package com.coolskyai.service.impl;

import com.coolskyai.entity.Moment;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.MomentMapper;
import com.coolskyai.service.MomentService;
import com.coolskyai.util.markdown.MarkdownUtils;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 动态业务。
 *
 * <p>动态是一段可以直接写 Markdown 的短文，没有标题、没有分类，
 * 所以这里的逻辑主要是两件事：分页，以及「私密内容不能出现在公开列表里」。
 *
 * @author CoolSkyAI
 */
@Service
public class MomentServiceImpl implements MomentService {

	/** 前台每页条数。动态是碎片内容，一页给多了反而没人翻。 */
	private static final int PAGE_SIZE = 5;

	/** 按发布时间倒序，最新的在最上面 */
	private static final String ORDER_BY = "create_time desc";

	/** 私密动态在公开列表中的占位内容：说清楚状态，不泄露任何正文 */
	private static final String PRIVATE_PLACEHOLDER = "<p>此条为私密动态，仅发布者可见！</p>";

	private final MomentMapper momentMapper;

	public MomentServiceImpl(MomentMapper momentMapper) {
		this.momentMapper = momentMapper;
	}

	@Override
	public List<Moment> getMomentList() {
		return momentMapper.getMomentList();
	}

	/**
	 * 分页取动态并渲染正文。
	 *
	 * @param adminIdentity 后台视角为 true，可以看到全部内容；前台只能看已发布的
	 */
	@Override
	public List<Moment> getMomentVOList(Integer pageNum, boolean adminIdentity) {
		PageHelper.startPage(pageNum, PAGE_SIZE, ORDER_BY);
		List<Moment> moments = momentMapper.getMomentList();
		for (Moment moment : moments) {
			boolean visible = adminIdentity || Boolean.TRUE.equals(moment.getPublished());
			moment.setContent(visible
					? MarkdownUtils.markdownToHtmlExtensions(moment.getContent())
					: PRIVATE_PLACEHOLDER);
		}
		return moments;
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void addLikeByMomentId(Long momentId) {
		requireAffected(momentMapper.addLikeByMomentId(momentId), "操作失败");
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateMomentPublishedById(Long momentId, Boolean published) {
		requireAffected(momentMapper.updateMomentPublishedById(momentId, published), "操作失败");
	}

	@Override
	public Moment getMomentById(Long id) {
		Moment moment = momentMapper.getMomentById(id);
		if (moment == null) {
			throw new NotFoundException("动态不存在");
		}
		return moment;
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteMomentById(Long id) {
		requireAffected(momentMapper.deleteMomentById(id), "删除失败");
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveMoment(Moment moment) {
		requireAffected(momentMapper.saveMoment(moment), "动态添加失败");
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateMoment(Moment moment) {
		requireAffected(momentMapper.updateMoment(moment), "动态修改失败");
	}

	/**
	 * 影响行数不为 1 说明没命中目标记录，视为失败并回滚。
	 */
	private void requireAffected(int rows, String message) {
		if (rows != 1) {
			throw new PersistenceException(message);
		}
	}
}
