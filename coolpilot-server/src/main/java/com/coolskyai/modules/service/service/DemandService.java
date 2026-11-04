package com.coolskyai.modules.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.notify.service.NotifyService;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.service.dto.DemandCreateDTO;
import com.coolskyai.modules.service.dto.DemandReviewDTO;
import com.coolskyai.modules.service.dto.QuoteSaveDTO;
import com.coolskyai.modules.service.entity.Demand;
import com.coolskyai.modules.service.entity.Project;
import com.coolskyai.modules.service.entity.Quote;
import com.coolskyai.modules.service.mapper.DemandMapper;
import com.coolskyai.modules.service.mapper.ProjectMapper;
import com.coolskyai.modules.service.mapper.QuoteMapper;
import com.coolskyai.entity.User;
import com.coolskyai.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description: 需求与报价
 *
 * 状态机（PRD 6.3）：
 *   PENDING_REVIEW → PENDING_CONTACT → EVALUATING → PENDING_QUOTE
 *   → PENDING_CONFIRM → IN_PROGRESS → PENDING_ACCEPT → COMPLETED
 *   异常：REJECTED / CANCELLED / DISPUTED
 *
 * 三条硬规则：
 *   1. 定制类服务不允许「付款即交付」，必须走需求 → 报价 → 确认。
 *   2. 报价可多版本，同一需求 version 唯一，历史版本不删（改价有据可查）。
 *   3. 用户只能看到自己的需求，联系人字段仅平台所有者可见。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class DemandService {

	private static final Set<String> PROJECT_TYPES = Set.of("BACKEND", "AI", "DEPLOY", "CONSULT", "OTHER");
	/** 需求状态流转白名单，未列出的流转一律拒绝 */
	private static final Map<String, Set<String>> FLOW = new HashMap<>(16);
	/** 用户可自行取消的状态 */
	private static final Set<String> CANCELABLE = Set.of(
			"PENDING_REVIEW", "PENDING_CONTACT", "EVALUATING", "PENDING_QUOTE", "PENDING_CONFIRM");

	static {
		FLOW.put("PENDING_REVIEW", Set.of("PENDING_CONTACT", "PENDING_QUOTE", "REJECTED", "CANCELLED"));
		FLOW.put("PENDING_CONTACT", Set.of("EVALUATING", "PENDING_QUOTE", "CANCELLED", "REJECTED"));
		FLOW.put("EVALUATING", Set.of("PENDING_QUOTE", "PENDING_CONTACT", "CANCELLED"));
		FLOW.put("PENDING_QUOTE", Set.of("PENDING_CONFIRM", "CANCELLED", "REJECTED"));
		FLOW.put("PENDING_CONFIRM", Set.of("IN_PROGRESS", "PENDING_QUOTE", "CANCELLED", "REJECTED"));
		FLOW.put("IN_PROGRESS", Set.of("PENDING_ACCEPT", "DISPUTED"));
		FLOW.put("PENDING_ACCEPT", Set.of("COMPLETED", "IN_PROGRESS", "DISPUTED"));
		FLOW.put("DISPUTED", Set.of("IN_PROGRESS", "COMPLETED", "CANCELLED"));
		FLOW.put("COMPLETED", Set.of());
		FLOW.put("REJECTED", Set.of());
		FLOW.put("CANCELLED", Set.of());
	}

	@Autowired
	private DemandMapper demandMapper;
	@Autowired
	private QuoteMapper quoteMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private ServiceEventLogger eventLogger;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private NotifyService notifyService;

	// ---------------- 用户侧 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Demand create(Long userId, DemandCreateDTO dto) {
		if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
			throw new BadRequestException("需求标题不能为空");
		}
		if (dto.getTitle().trim().length() > 255) {
			throw new BadRequestException("需求标题不能超过 255 个字符");
		}
		String projectType = dto.getProjectType() == null ? "" : dto.getProjectType().trim().toUpperCase();
		if (!PROJECT_TYPES.contains(projectType)) {
			throw new BadRequestException("请选择有效的项目类型");
		}
		if (dto.getDescription() == null || dto.getDescription().trim().isEmpty()) {
			throw new BadRequestException("需求描述不能为空");
		}
		BigDecimal min = dto.getBudgetMin();
		BigDecimal max = dto.getBudgetMax();
		if (min != null && min.compareTo(BigDecimal.ZERO) < 0 || max != null && max.compareTo(BigDecimal.ZERO) < 0) {
			throw new BadRequestException("预算不能为负数");
		}
		if (min != null && max != null && min.compareTo(max) > 0) {
			throw new BadRequestException("预算下限不能大于上限");
		}

		Long productId = null;
		if (dto.getProductId() != null) {
			Product product = productMapper.findById(dto.getProductId());
			if (product == null || !"SERVICE".equals(product.getProductType())) {
				throw new BadRequestException("关联的服务商品不存在");
			}
			productId = product.getId();
		}

		Demand demand = new Demand();
		demand.setDemandNo(generateDemandNo());
		demand.setUserId(userId);
		demand.setProductId(productId);
		demand.setTitle(dto.getTitle().trim());
		demand.setProjectType(projectType);
		demand.setTechStack(dto.getTechStack() == null ? "" : dto.getTechStack().trim());
		demand.setBudgetMin(min);
		demand.setBudgetMax(max);
		demand.setBudgetNegotiable(Boolean.TRUE.equals(dto.getBudgetNegotiable()));
		demand.setExpectDate(dto.getExpectDate());
		demand.setDescription(dto.getDescription().trim());
		demand.setAttachmentFileId(dto.getAttachmentFileId());
		demand.setContact(resolveContact(userId, dto.getContact()));
		demand.setStatus("PENDING_REVIEW");
		demand.setRejectReason("");
		demandMapper.insert(demand);

		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), null, "PENDING_REVIEW", "SUBMIT",
				userId, "客户", "提交需求");

		User client = userMapper.findById(userId);
		String who = client == null || client.getNickname() == null ? "客户" : client.getNickname();
		notifyService.toOwner("DEMAND", "有新的服务需求待审核",
				who + " 提交了需求「" + demand.getTitle() + "」（" + demand.getDemandNo() + "），类型 "
						+ projectType + "，预算 " + budgetText(demand) + "。请到后台审核。",
				"/service/demand", "DEMAND", demand.getId());
		return demandMapper.findByNo(demand.getDemandNo());
	}

	public PageResult<Demand> myDemands(Long userId, String status, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize);
		int total = demandMapper.count(userId, status, null);
		List<Demand> list = demandMapper.page(userId, status, null, (page - 1) * size, size);
		list.forEach(item -> item.setContact(null));
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 需求详情：需求 + 报价 + 项目 + 流水
	 * 用户视角隐藏联系人、草稿报价与其他人的信息。
	 */
	public Map<String, Object> detail(String demandNo, Long viewerId, boolean isOwner) {
		Demand demand = demandMapper.findByNo(demandNo);
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!isOwner && !demand.getUserId().equals(viewerId)) {
			throw new BadRequestException("无权查看该需求");
		}
		List<Quote> quotes = quoteMapper.listByDemand(demand.getId());
		if (!isOwner) {
			List<Quote> visible = new ArrayList<>();
			for (Quote quote : quotes) {
				if (!"DRAFT".equals(quote.getStatus())) {
					visible.add(quote);
				}
			}
			quotes = visible;
			demand.setContact(null);
		}
		Map<String, Object> data = new HashMap<>(8);
		data.put("demand", demand);
		data.put("quotes", quotes);
		data.put("projects", projectMapper.listByDemand(demand.getId()));
		data.put("timeline", eventLogger.demandTimeline(demandNo));
		return data;
	}

	@Transactional(rollbackFor = Exception.class)
	public void cancel(String demandNo, Long userId) {
		Demand demand = demandMapper.findByNo(demandNo);
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!demand.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该需求");
		}
		if ("CANCELLED".equals(demand.getStatus())) {
			throw new BadRequestException("需求已取消");
		}
		if (!CANCELABLE.contains(demand.getStatus())) {
			throw new BadRequestException("需求已进入实施阶段，不能取消，请联系平台");
		}
		String from = demand.getStatus();
		moveOrThrow(demand, "CANCELLED", "");
		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), from, "CANCELLED", "CANCEL",
				userId, "客户", "客户取消需求");
	}

	// ---------------- 后台：审核 ----------------

	public PageResult<Demand> pageAdmin(String status, String keyword, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize);
		int total = demandMapper.count(null, status, keyword);
		List<Demand> list = demandMapper.page(null, status, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	@Transactional(rollbackFor = Exception.class)
	public void review(String demandNo, DemandReviewDTO dto, Long operatorId, String operatorName) {
		Demand demand = demandMapper.findByNo(demandNo);
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!"PENDING_REVIEW".equals(demand.getStatus())) {
			throw new BadRequestException("该需求已审核过，请刷新");
		}
		boolean approve = Boolean.TRUE.equals(dto.getApprove());
		if (!approve && (dto.getReason() == null || dto.getReason().trim().isEmpty())) {
			throw new BadRequestException("拒绝需求必须填写原因");
		}
		String target = approve ? "PENDING_QUOTE" : "REJECTED";
		String reason = approve ? "" : dto.getReason().trim();
		if (demandMapper.move(demand.getId(), "PENDING_REVIEW", target, reason, new Date()) == 0) {
			throw new BadRequestException("需求状态已变化，请刷新后重试");
		}
		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), "PENDING_REVIEW", target,
				approve ? "APPROVE" : "REJECT", operatorId, operatorName, reason);

		if (approve) {
			notifyService.toUser(demand.getUserId(), "DEMAND", "需求已通过审核",
					"需求「" + demand.getTitle() + "」已通过审核，平台会尽快给出报价，报价单发出后会再通知你。",
					"/demand/" + demand.getDemandNo(), "DEMAND", demand.getId());
		} else {
			notifyService.toUser(demand.getUserId(), "DEMAND", "需求未通过审核",
					"需求「" + demand.getTitle() + "」未通过审核，原因：" + reason
							+ "。可以修改后重新提交，或直接提交意见反馈。",
					"/demand/" + demand.getDemandNo(), "DEMAND", demand.getId());
		}
	}

	/**
	 * 后台手工推进需求状态（联系中 / 评估中 / 争议 等）
	 */
	@Transactional(rollbackFor = Exception.class)
	public void moveStatus(String demandNo, String status, String remark, Long operatorId, String operatorName) {
		Demand demand = demandMapper.findByNo(demandNo);
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (status == null || status.equals(demand.getStatus())) {
			return;
		}
		String from = demand.getStatus();
		moveOrThrow(demand, status, remark == null ? "" : remark);
		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), from, status, "MOVE",
				operatorId, operatorName, remark == null ? "" : remark);
	}

	// ---------------- 后台：报价 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Quote createQuote(QuoteSaveDTO dto, Long operatorId, String operatorName) {
		if (dto.getDemandId() == null) {
			throw new BadRequestException("需求不能为空");
		}
		Demand demand = demandMapper.findById(dto.getDemandId());
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!"PENDING_QUOTE".equals(demand.getStatus())) {
			throw new BadRequestException("当前需求状态不可报价");
		}
		if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new BadRequestException("报价金额必须大于 0");
		}

		Quote quote = new Quote();
		quote.setQuoteNo(generateQuoteNo());
		quote.setDemandId(demand.getId());
		quote.setVersion(quoteMapper.maxVersion(demand.getId()) + 1);
		quote.setAmount(dto.getAmount());
		quote.setCurrency(dto.getCurrency() == null || dto.getCurrency().trim().isEmpty()
				? "CNY" : dto.getCurrency().trim().toUpperCase());
		quote.setScope(dto.getScope());
		quote.setValidUntil(dto.getValidUntil());
		quote.setStatus("DRAFT");
		quoteMapper.insert(quote);

		eventLogger.log("QUOTE", quote.getId(), quote.getQuoteNo(), null, "DRAFT", "QUOTE",
				operatorId, operatorName, "创建第 " + quote.getVersion() + " 版报价");
		return quote;
	}

	@Transactional(rollbackFor = Exception.class)
	public Quote updateQuote(Long quoteId, QuoteSaveDTO dto, Long operatorId, String operatorName) {
		Quote quote = quoteMapper.findById(quoteId);
		if (quote == null) {
			throw new BadRequestException("报价不存在");
		}
		if (!"DRAFT".equals(quote.getStatus())) {
			throw new BadRequestException("只有草稿报价可以修改");
		}
		if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new BadRequestException("报价金额必须大于 0");
		}
		quote.setAmount(dto.getAmount());
		quote.setScope(dto.getScope());
		quote.setValidUntil(dto.getValidUntil());
		if (dto.getCurrency() != null && !dto.getCurrency().trim().isEmpty()) {
			quote.setCurrency(dto.getCurrency().trim().toUpperCase());
		}
		if (quoteMapper.updateDraft(quote) == 0) {
			throw new BadRequestException("报价状态已变化，请刷新后重试");
		}
		eventLogger.log("QUOTE", quote.getId(), quote.getQuoteNo(), "DRAFT", "DRAFT", "UPDATE",
				operatorId, operatorName, "修改报价金额为 " + quote.getAmount());
		return quoteMapper.findById(quoteId);
	}

	/**
	 * 发送报价：报价 DRAFT→SENT，需求 PENDING_QUOTE→PENDING_CONFIRM
	 */
	@Transactional(rollbackFor = Exception.class)
	public void sendQuote(Long quoteId, Long operatorId, String operatorName) {
		Quote quote = quoteMapper.findById(quoteId);
		if (quote == null) {
			throw new BadRequestException("报价不存在");
		}
		if (!"DRAFT".equals(quote.getStatus())) {
			throw new BadRequestException("该报价已发送");
		}
		Demand demand = demandMapper.findById(quote.getDemandId());
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!"PENDING_QUOTE".equals(demand.getStatus())) {
			throw new BadRequestException("当前需求状态不可发送报价");
		}
		if (quoteMapper.move(quote.getId(), "DRAFT", "SENT") == 0) {
			throw new BadRequestException("报价状态已变化，请刷新后重试");
		}
		if (demandMapper.move(demand.getId(), "PENDING_QUOTE", "PENDING_CONFIRM", "", null) == 0) {
			throw new BadRequestException("需求状态已变化，请刷新后重试");
		}
		eventLogger.log("QUOTE", quote.getId(), quote.getQuoteNo(), "DRAFT", "SENT", "SEND",
				operatorId, operatorName, "发出报价，等待客户确认");
		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), "PENDING_QUOTE", "PENDING_CONFIRM",
				"SEND_QUOTE", operatorId, operatorName, "报价单 " + quote.getQuoteNo());

		notifyService.toUser(demand.getUserId(), "DEMAND", "收到新的报价",
				"需求「" + demand.getTitle() + "」的报价单 " + quote.getQuoteNo()
						+ " 已发出：¥" + quote.getAmount()
						+ (quote.getValidUntil() == null ? "" : "，有效期至 " + quote.getValidUntil())
						+ "。请确认或提出你的想法。",
				"/demand/" + demand.getDemandNo(), "QUOTE", quote.getId());
	}

	/**
	 * 客户接受报价：报价置 ACCEPTED，其他报价失效，需求进入实施，自动立项
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> acceptQuote(Long quoteId, Long userId, String nickname) {
		Quote quote = quoteMapper.findById(quoteId);
		if (quote == null) {
			throw new BadRequestException("报价不存在");
		}
		Demand demand = demandMapper.findById(quote.getDemandId());
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!demand.getUserId().equals(userId)) {
			throw new BadRequestException("无权确认该报价");
		}
		if (!"SENT".equals(quote.getStatus())) {
			throw new BadRequestException("该报价已失效，请刷新");
		}
		if (!"PENDING_CONFIRM".equals(demand.getStatus())) {
			throw new BadRequestException("当前需求状态不可确认报价");
		}
		if (quote.getValidUntil() != null && quote.getValidUntil().isBefore(java.time.LocalDate.now())) {
			quoteMapper.move(quote.getId(), "SENT", "EXPIRED");
			throw new BadRequestException("报价已过期，请让平台重新报价");
		}
		if (quoteMapper.move(quote.getId(), "SENT", "ACCEPTED") == 0) {
			throw new BadRequestException("报价状态已变化，请刷新后重试");
		}
		quoteMapper.expireOthers(demand.getId(), quote.getId());
		if (demandMapper.move(demand.getId(), "PENDING_CONFIRM", "IN_PROGRESS", "", null) == 0) {
			throw new BadRequestException("需求状态已变化，请刷新后重试");
		}
		eventLogger.log("QUOTE", quote.getId(), quote.getQuoteNo(), "SENT", "ACCEPTED", "ACCEPT",
				userId, nickname, "客户接受报价");
		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), "PENDING_CONFIRM", "IN_PROGRESS",
				"ACCEPT_QUOTE", userId, nickname, "确认报价单 " + quote.getQuoteNo());

		Project project = projectService.createFromQuote(userId, demand.getDemandNo(), demand.getTitle(),
				demand.getId(), demand.getProductId(), quote);

		notifyService.toOwner("DEMAND", "客户已确认报价",
				"需求「" + demand.getTitle() + "」的报价单 " + quote.getQuoteNo()
						+ " 已被客户接受，金额 ¥" + quote.getAmount()
						+ "，已自动立项 " + project.getProjectNo() + "，请按里程碑推进。",
				"/service/project", "PROJECT", project.getId());

		Map<String, Object> data = new HashMap<>(4);
		data.put("quote", quoteMapper.findById(quote.getId()));
		data.put("project", project);
		return data;
	}

	@Transactional(rollbackFor = Exception.class)
	public void rejectQuote(Long quoteId, Long userId, String reason, String nickname) {
		Quote quote = quoteMapper.findById(quoteId);
		if (quote == null) {
			throw new BadRequestException("报价不存在");
		}
		Demand demand = demandMapper.findById(quote.getDemandId());
		if (demand == null) {
			throw new BadRequestException("需求不存在");
		}
		if (!demand.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该报价");
		}
		if (!"SENT".equals(quote.getStatus())) {
			throw new BadRequestException("该报价已失效，请刷新");
		}
		if (quoteMapper.move(quote.getId(), "SENT", "REJECTED") == 0) {
			throw new BadRequestException("报价状态已变化，请刷新后重试");
		}
		demandMapper.move(demand.getId(), "PENDING_CONFIRM", "PENDING_QUOTE", "", null);
		String remark = reason == null || reason.trim().isEmpty() ? "客户拒绝报价" : reason.trim();
		eventLogger.log("QUOTE", quote.getId(), quote.getQuoteNo(), "SENT", "REJECTED", "REJECT",
				userId, nickname, remark);
		eventLogger.log("DEMAND", demand.getId(), demand.getDemandNo(), "PENDING_CONFIRM", "PENDING_QUOTE",
				"REJECT_QUOTE", userId, nickname, remark);

		notifyService.toOwner("DEMAND", "客户拒绝了报价",
				"需求「" + demand.getTitle() + "」的报价单 " + quote.getQuoteNo()
						+ " 被拒绝，原因：" + remark + "。可以调整方案后重新报价（会自动生成新版本）。",
				"/service/demand", "DEMAND", demand.getId());
	}

	// ---------------- 内部 ----------------

	private void moveOrThrow(Demand demand, String target, String remark) {
		Set<String> allowed = FLOW.getOrDefault(demand.getStatus(), Set.of());
		if (!allowed.contains(target)) {
			throw new BadRequestException("不允许从 " + demand.getStatus() + " 流转到 " + target);
		}
		if (demandMapper.move(demand.getId(), demand.getStatus(), target,
				"REJECTED".equals(target) ? remark : demand.getRejectReason(), null) == 0) {
			throw new BadRequestException("需求状态已变化，请刷新后重试");
		}
	}

	/**
	 * 联系方式：用户没填时回落到账号邮箱，保证平台总能联系到人
	 */
	/**
	 * 预算区间文案，前后端都能看懂的说法
	 */
	private String budgetText(Demand demand) {
		BigDecimal min = demand.getBudgetMin();
		BigDecimal max = demand.getBudgetMax();
		if (min == null && max == null) {
			return "未填写";
		}
		if (min != null && max != null) {
			return "¥" + min.stripTrailingZeros().toPlainString() + " ~ ¥" + max.stripTrailingZeros().toPlainString();
		}
		BigDecimal single = min == null ? max : min;
		return "¥" + single.stripTrailingZeros().toPlainString();
	}

	private String resolveContact(Long userId, String contact) {
		if (contact != null && !contact.trim().isEmpty()) {
			return contact.trim();
		}
		User user = userMapper.findById(userId);
		return user == null || user.getEmail() == null ? "" : user.getEmail();
	}

	private String generateDemandNo() {
		String prefix = "D" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		for (int i = 0; i < 20; i++) {
			String no = prefix + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
			if (demandMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成需求号失败，请重试");
	}

	private String generateQuoteNo() {
		String prefix = "Q" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		for (int i = 0; i < 20; i++) {
			String no = prefix + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
			if (quoteMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成报价单号失败，请重试");
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
