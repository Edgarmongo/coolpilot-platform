package com.coolskyai.modules.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.service.dto.MilestoneSaveDTO;
import com.coolskyai.modules.service.dto.ProjectCreateDTO;
import com.coolskyai.modules.service.entity.Delivery;
import com.coolskyai.modules.service.entity.Milestone;
import com.coolskyai.modules.service.entity.Project;
import com.coolskyai.modules.service.entity.Quote;
import com.coolskyai.modules.service.entity.ServiceItem;
import com.coolskyai.modules.service.mapper.DeliveryMapper;
import com.coolskyai.modules.service.mapper.DemandMapper;
import com.coolskyai.modules.service.mapper.MilestoneMapper;
import com.coolskyai.modules.service.mapper.ProjectMapper;
import com.coolskyai.modules.service.mapper.ServiceItemMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description: 项目 / 里程碑 / 交付验收
 *
 * 状态机：
 *   project    NOT_STARTED → IN_PROGRESS → PENDING_ACCEPT → COMPLETED → ARCHIVED
 *   milestone  PENDING → DELIVERED → ACCEPTED / REJECTED（打回后可再次交付）
 *
 * 交付与验收都带「原状态条件」更新，避免两端同时点击时状态被覆盖。
 * 全部里程碑验收通过才把项目置为 COMPLETED，并把来源需求一起收口。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class ProjectService {

	private static final Set<String> PROJECT_STATUSES = Set.of(
			"NOT_STARTED", "IN_PROGRESS", "PENDING_ACCEPT", "COMPLETED", "ARCHIVED");
	private static final Set<String> DELIVERY_TYPES = Set.of("FILE", "LINK", "TEXT");
	/** 默认交付周期（天），商品没配 delivery_days 时用 */
	private static final int DEFAULT_DELIVERY_DAYS = 7;

	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private MilestoneMapper milestoneMapper;
	@Autowired
	private DeliveryMapper deliveryMapper;
	@Autowired
	private DemandMapper demandMapper;
	@Autowired
	private ServiceItemMapper serviceItemMapper;
	@Autowired
	private ServiceEventLogger eventLogger;

	// ---------------- 创建 ----------------

	/**
	 * 报价被接受后自动立项，并按交付周期生成首个里程碑（PRD 6.4 第 3 条）
	 */
	@Transactional(rollbackFor = Exception.class)
	public Project createFromQuote(Long userId, String demandNo, String demandTitle, Long demandId, Long productId,
	                               Quote quote) {
		Project project = new Project();
		project.setProjectNo(generateProjectNo());
		project.setDemandId(demandId);
		project.setQuoteId(quote.getId());
		project.setUserId(userId);
		project.setName(demandTitle);
		project.setAmount(quote.getAmount());
		project.setStatus("IN_PROGRESS");
		project.setStartDate(LocalDate.now());
		projectMapper.insert(project);

		int days = deliveryDays(productId);
		Milestone first = new Milestone();
		first.setProjectId(project.getId());
		first.setName("首期交付");
		first.setSort(1);
		first.setAmount(quote.getAmount());
		first.setDueDate(LocalDate.now().plusDays(days));
		first.setStatus("PENDING");
		first.setRemark("");
		milestoneMapper.insert(first);

		eventLogger.log("PROJECT", project.getId(), project.getProjectNo(), null, "IN_PROGRESS", "CREATE",
				0L, "系统", "由需求 " + demandNo + " 的报价自动立项，首个里程碑「首期交付」");
		return project;
	}

	/**
	 * 后台手工立项（固定价服务从订单立项时用）
	 */
	@Transactional(rollbackFor = Exception.class)
	public Project createManual(ProjectCreateDTO dto, Long operatorId, String operatorName) {
		if (dto.getName() == null || dto.getName().trim().isEmpty()) {
			throw new BadRequestException("项目名称不能为空");
		}
		if (dto.getUserId() == null) {
			throw new BadRequestException("客户不能为空");
		}
		Project project = new Project();
		project.setProjectNo(generateProjectNo());
		project.setDemandId(dto.getDemandId());
		project.setQuoteId(dto.getQuoteId());
		project.setOrderNo(dto.getOrderNo());
		project.setUserId(dto.getUserId());
		project.setName(dto.getName().trim());
		project.setAmount(dto.getAmount() == null ? BigDecimal.ZERO : dto.getAmount());
		project.setStatus("NOT_STARTED");
		project.setStartDate(dto.getStartDate());
		project.setEndDate(dto.getEndDate());
		projectMapper.insert(project);

		if (dto.getFirstMilestoneName() != null && !dto.getFirstMilestoneName().trim().isEmpty()) {
			Milestone first = new Milestone();
			first.setProjectId(project.getId());
			first.setName(dto.getFirstMilestoneName().trim());
			first.setSort(1);
			first.setAmount(project.getAmount());
			first.setDueDate(dto.getEndDate());
			first.setStatus("PENDING");
			first.setRemark("");
			milestoneMapper.insert(first);
		}

		eventLogger.log("PROJECT", project.getId(), project.getProjectNo(), null, "NOT_STARTED", "CREATE",
				operatorId, operatorName, "后台手工立项");
		return project;
	}

	// ---------------- 查询 ----------------

	public PageResult<Project> myProjects(Long userId, String status, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize);
		int total = projectMapper.count(userId, status, null);
		List<Project> list = projectMapper.page(userId, status, null, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public PageResult<Project> pageAdmin(String status, String keyword, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize);
		int total = projectMapper.count(null, status, keyword);
		List<Project> list = projectMapper.page(null, status, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 项目详情：项目 + 里程碑（含各自最新交付物）+ 全部交付物 + 流水
	 */
	public Map<String, Object> detail(String projectNo, Long viewerId, boolean isOwner) {
		Project project = projectMapper.findByNo(projectNo);
		if (project == null) {
			throw new BadRequestException("项目不存在");
		}
		if (!isOwner && !project.getUserId().equals(viewerId)) {
			throw new BadRequestException("无权查看该项目");
		}
		List<Milestone> milestones = milestoneMapper.listByProject(project.getId());
		Map<Long, Delivery> latest = new HashMap<>(milestones.size());
		for (Milestone milestone : milestones) {
			latest.put(milestone.getId(), deliveryMapper.findLatestByMilestone(milestone.getId()));
		}
		Map<String, Object> data = new HashMap<>(8);
		data.put("project", project);
		data.put("milestones", milestones);
		data.put("latestDeliveries", latest);
		data.put("deliveries", deliveryMapper.listByProject(project.getId()));
		data.put("events", eventLogger.timeline("PROJECT", project.getId()));
		return data;
	}

	// ---------------- 里程碑 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Milestone addMilestone(MilestoneSaveDTO dto, Long operatorId, String operatorName) {
		if (dto.getProjectId() == null) {
			throw new BadRequestException("项目不能为空");
		}
		Project project = projectMapper.findById(dto.getProjectId());
		if (project == null) {
			throw new BadRequestException("项目不存在");
		}
		if ("COMPLETED".equals(project.getStatus()) || "ARCHIVED".equals(project.getStatus())) {
			throw new BadRequestException("项目已结束，不能再加里程碑");
		}
		if (dto.getName() == null || dto.getName().trim().isEmpty()) {
			throw new BadRequestException("里程碑名称不能为空");
		}
		Milestone milestone = new Milestone();
		milestone.setProjectId(project.getId());
		milestone.setName(dto.getName().trim());
		milestone.setSort(dto.getSort() == null || dto.getSort() <= 0
				? milestoneMapper.maxSort(project.getId()) + 1 : dto.getSort());
		milestone.setAmount(dto.getAmount() == null ? BigDecimal.ZERO : dto.getAmount());
		milestone.setDueDate(dto.getDueDate());
		milestone.setRemark(dto.getRemark() == null ? "" : dto.getRemark());
		milestone.setStatus("PENDING");
		milestoneMapper.insert(milestone);

		eventLogger.log("PROJECT", project.getId(), project.getProjectNo(), project.getStatus(), project.getStatus(),
				"ADD_MILESTONE", operatorId, operatorName, "新增里程碑「" + milestone.getName() + "」");
		return milestone;
	}

	@Transactional(rollbackFor = Exception.class)
	public void updateMilestone(Long id, MilestoneSaveDTO dto, Long operatorId, String operatorName) {
		Milestone milestone = milestoneMapper.findById(id);
		if (milestone == null) {
			throw new BadRequestException("里程碑不存在");
		}
		if ("ACCEPTED".equals(milestone.getStatus())) {
			throw new BadRequestException("已验收的里程碑不能修改");
		}
		milestone.setName(dto.getName() == null || dto.getName().trim().isEmpty()
				? milestone.getName() : dto.getName().trim());
		milestone.setSort(dto.getSort() == null || dto.getSort() <= 0 ? milestone.getSort() : dto.getSort());
		milestone.setAmount(dto.getAmount() == null ? milestone.getAmount() : dto.getAmount());
		milestone.setDueDate(dto.getDueDate() == null ? milestone.getDueDate() : dto.getDueDate());
		milestone.setRemark(dto.getRemark() == null ? milestone.getRemark() : dto.getRemark());
		milestoneMapper.update(milestone);

		Project project = projectMapper.findById(milestone.getProjectId());
		eventLogger.log("MILESTONE", milestone.getId(), project == null ? "" : project.getProjectNo(),
				milestone.getStatus(), milestone.getStatus(), "UPDATE", operatorId, operatorName,
				"调整里程碑「" + milestone.getName() + "」");
	}

	/**
	 * 交付：写交付物 + 里程碑置 DELIVERED + 项目进入待验收
	 */
	@Transactional(rollbackFor = Exception.class)
	public Delivery deliver(Long milestoneId, String deliveryType, Long fileId, String content,
	                        Long operatorId, String operatorName, String remark) {
		Milestone milestone = milestoneMapper.findById(milestoneId);
		if (milestone == null) {
			throw new BadRequestException("里程碑不存在");
		}
		if (!"PENDING".equals(milestone.getStatus()) && !"REJECTED".equals(milestone.getStatus())
				&& !"DELIVERING".equals(milestone.getStatus())) {
			throw new BadRequestException("当前里程碑状态不可交付");
		}
		String type = deliveryType == null ? "FILE" : deliveryType.trim().toUpperCase();
		if (!DELIVERY_TYPES.contains(type)) {
			throw new BadRequestException("交付类型不支持");
		}
		if ("FILE".equals(type) && fileId == null) {
			throw new BadRequestException("请上传交付文件");
		}
		if (!"FILE".equals(type) && (content == null || content.trim().isEmpty())) {
			throw new BadRequestException("请填写交付说明或链接");
		}

		Project project = projectMapper.findById(milestone.getProjectId());
		if (project == null) {
			throw new BadRequestException("项目不存在");
		}

		Delivery delivery = new Delivery();
		delivery.setServiceOrderId(0L);
		delivery.setProjectId(project.getId());
		delivery.setMilestoneId(milestone.getId());
		delivery.setDeliveryType(type);
		delivery.setFileId(fileId);
		delivery.setContent(content);
		delivery.setDeliveredBy(operatorId == null ? 0L : operatorId);
		delivery.setDeliveredTime(new Date());
		delivery.setStatus("DELIVERED");
		deliveryMapper.insert(delivery);

		String from = milestone.getStatus();
		if (milestoneMapper.move(milestone.getId(), from, "DELIVERED", new Date(), null) == 0) {
			throw new BadRequestException("里程碑状态已变化，请刷新后重试");
		}
		projectMapper.move(project.getId(), "PENDING_ACCEPT", null);

		eventLogger.log("MILESTONE", milestone.getId(), project.getProjectNo(), from, "DELIVERED", "DELIVER",
				operatorId, operatorName, "交付里程碑「" + milestone.getName() + "」");
		eventLogger.log("PROJECT", project.getId(), project.getProjectNo(), project.getStatus(), "PENDING_ACCEPT",
				"DELIVER", operatorId, operatorName, remark == null ? "" : remark);
		return delivery;
	}

	/**
	 * 验收通过：交付物置 ACCEPTED；全部里程碑通过则项目与需求一起完成
	 */
	@Transactional(rollbackFor = Exception.class)
	public void acceptMilestone(Long milestoneId, Long userId) {
		Milestone milestone = milestoneMapper.findById(milestoneId);
		if (milestone == null) {
			throw new BadRequestException("里程碑不存在");
		}
		Project project = projectMapper.findById(milestone.getProjectId());
		if (project == null) {
			throw new BadRequestException("项目不存在");
		}
		if (!project.getUserId().equals(userId)) {
			throw new BadRequestException("无权验收该项目");
		}
		if (!"DELIVERED".equals(milestone.getStatus())) {
			throw new BadRequestException("该里程碑还没有待验收的交付物");
		}

		Delivery latest = deliveryMapper.findLatestByMilestone(milestone.getId());
		if (latest != null) {
			deliveryMapper.move(latest.getId(), "DELIVERED", "ACCEPTED", new Date());
		}
		if (milestoneMapper.move(milestone.getId(), "DELIVERED", "ACCEPTED", milestone.getDeliveredTime(), new Date()) == 0) {
			throw new BadRequestException("里程碑状态已变化，请刷新后重试");
		}
		eventLogger.log("MILESTONE", milestone.getId(), project.getProjectNo(), "DELIVERED", "ACCEPTED", "ACCEPT",
				userId, "客户", "验收通过");

		if (milestoneMapper.countUnaccepted(project.getId()) == 0) {
			projectMapper.move(project.getId(), "COMPLETED", LocalDate.now());
			eventLogger.log("PROJECT", project.getId(), project.getProjectNo(), project.getStatus(), "COMPLETED",
					"COMPLETE", 0L, "系统", "全部里程碑验收通过，项目完成");
			if (project.getDemandId() != null) {
				demandMapper.move(project.getDemandId(), "IN_PROGRESS", "COMPLETED", "", null);
				demandMapper.move(project.getDemandId(), "PENDING_ACCEPT", "COMPLETED", "", null);
			}
		}
	}

	/**
	 * 验收打回：交付物置 REJECTED，里程碑回到 REJECTED，项目退回 IN_PROGRESS
	 */
	@Transactional(rollbackFor = Exception.class)
	public void rejectMilestone(Long milestoneId, Long userId, String reason) {
		Milestone milestone = milestoneMapper.findById(milestoneId);
		if (milestone == null) {
			throw new BadRequestException("里程碑不存在");
		}
		Project project = projectMapper.findById(milestone.getProjectId());
		if (project == null) {
			throw new BadRequestException("项目不存在");
		}
		if (!project.getUserId().equals(userId)) {
			throw new BadRequestException("无权验收该项目");
		}
		if (!"DELIVERED".equals(milestone.getStatus())) {
			throw new BadRequestException("该里程碑还没有待验收的交付物");
		}
		Delivery latest = deliveryMapper.findLatestByMilestone(milestone.getId());
		if (latest != null) {
			deliveryMapper.move(latest.getId(), "DELIVERED", "REJECTED", new Date());
		}
		if (milestoneMapper.move(milestone.getId(), "DELIVERED", "REJECTED", milestone.getDeliveredTime(), null) == 0) {
			throw new BadRequestException("里程碑状态已变化，请刷新后重试");
		}
		projectMapper.move(project.getId(), "IN_PROGRESS", null);

		String remark = reason == null || reason.trim().isEmpty() ? "未通过验收" : reason.trim();
		eventLogger.log("MILESTONE", milestone.getId(), project.getProjectNo(), "DELIVERED", "REJECTED", "REJECT",
				userId, "客户", remark);
	}

	/**
	 * 项目状态手工流转（归档、暂停等）
	 */
	@Transactional(rollbackFor = Exception.class)
	public void moveProject(String projectNo, String status, Long operatorId, String operatorName) {
		if (!PROJECT_STATUSES.contains(status)) {
			throw new BadRequestException("不支持的项目状态");
		}
		Project project = projectMapper.findByNo(projectNo);
		if (project == null) {
			throw new BadRequestException("项目不存在");
		}
		if (status.equals(project.getStatus())) {
			return;
		}
		projectMapper.move(project.getId(), status, "COMPLETED".equals(status) ? LocalDate.now() : project.getEndDate());
		eventLogger.log("PROJECT", project.getId(), project.getProjectNo(), project.getStatus(), status, "MOVE",
				operatorId, operatorName, "手工调整项目状态");
	}

	// ---------------- 内部 ----------------

	private int deliveryDays(Long productId) {
		ServiceItem item = productId == null ? null : serviceItemMapper.findByProductId(productId);
		if (item == null || item.getDeliveryDays() == null || item.getDeliveryDays() <= 0) {
			return DEFAULT_DELIVERY_DAYS;
		}
		return item.getDeliveryDays();
	}

	private String generateProjectNo() {
		String prefix = "P" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		for (int i = 0; i < 20; i++) {
			String no = prefix + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
			if (projectMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成项目编号失败，请重试");
	}

	private int normalizePage(Integer pageNum) {
		return pageNum == null || pageNum < 1 ? 1 : pageNum;
	}

	private int normalizeSize(Integer pageSize) {
		if (pageSize == null || pageSize < 1) {
			return 10;
		}
		return Math.min(pageSize, 100);
	}
}
