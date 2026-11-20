package com.coolskyai.modules.dashboard.service;

import com.coolskyai.exception.BadRequestException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 驾驶舱统计的时间区间
 *
 * <p>三件事都在这里定死，其他地方不许各写一套：
 * <ol>
 *   <li><b>区间左闭右开</b> {@code [start, end)} —— 相邻区间不重复统计边界那一秒；</li>
 *   <li><b>桶序列由服务端补齐</b> —— SQL 只返回有数据的桶，缺的在这里补零，
 *       否则前端折线会把两个有数据的日子连成直线，掩盖中间的空档；</li>
 *   <li><b>粒度跨度受限</b> —— 小时粒度最多 7 天，避免一次查询扫全表。</li>
 * </ol>
 *
 * <p>为什么桶的 key 用「时间字符串」而不是 epoch：SQL 侧用 DATE_FORMAT 输出字符串，
 * Java 侧按同样规则生成，两边不用做时区换算，对齐最稳。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-21
 */
public final class DashboardPeriod {

	/** 业务时区，与 PRD §3.0 一致 */
	private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

	private static final DateTimeFormatter PLAIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter DAY_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public enum Granularity {
		HOUR, DAY, WEEK, MONTH;

		public static Granularity of(String text) {
			if (text == null || text.isBlank()) {
				return DAY;
			}
			try {
				return valueOf(text.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new BadRequestException("不支持的统计粒度：" + text);
			}
		}
	}

	private final LocalDateTime start;
	private final LocalDateTime end;
	private final Granularity granularity;

	private DashboardPeriod(LocalDateTime start, LocalDateTime end, Granularity granularity) {
		this.start = start;
		this.end = end;
		this.granularity = granularity;
	}

	// ------------------------------------------------------------------
	// 构造与校验
	// ------------------------------------------------------------------

	public static DashboardPeriod of(String startTime, String endTime, String granularity) {
		Granularity g = Granularity.of(granularity);

		LocalDateTime end = parse(endTime, "结束时间");
		LocalDateTime start = parse(startTime, "开始时间");

		// 缺省区间按粒度给：小时粒度给 30 天会直接撞上「最多 7 天」的校验，
		// 让调用方在没传区间时也可能报错，是接口自己给自己下套
		if (start == null && end == null) {
			end = LocalDateTime.now(ZONE).withSecond(0).withNano(0);
			start = end.minus(defaultSpan(g));
		} else if (start == null) {
			start = end.minus(defaultSpan(g));
		} else if (end == null) {
			end = start.plus(defaultSpan(g));
		}

		if (!start.isBefore(end)) {
			throw new BadRequestException("开始时间必须早于结束时间");
		}
		assertSpanAllowed(start, end, g);
		return new DashboardPeriod(start, end, g);
	}

	/** 各粒度的缺省跨度，保证缺省值一定落在 {@link #assertSpanAllowed} 的范围内 */
	private static Duration defaultSpan(Granularity g) {
		return switch (g) {
			case HOUR -> Duration.ofHours(24);
			case DAY -> Duration.ofDays(30);
			case WEEK -> Duration.ofDays(7 * 12);
			case MONTH -> Duration.ofDays(365);
		};
	}

	/**
	 * 解析时间。支持 {@code yyyy-MM-dd HH:mm:ss}、{@code yyyy-MM-dd}、以及带偏移的 ISO 串。
	 * 非法输入直接报错，不静默纠正 —— 悄悄改成别的日期比报错更难排查。
	 */
	private static LocalDateTime parse(String text, String label) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String v = text.trim();
		try {
			return LocalDateTime.parse(v, PLAIN);
		} catch (DateTimeParseException ignored) {
			// 继续尝试下一种格式
		}
		try {
			return LocalDateTime.parse(v);
		} catch (DateTimeParseException ignored) {
			// 继续
		}
		try {
			return LocalDate.parse(v, DAY_ONLY).atStartOfDay();
		} catch (DateTimeParseException ignored) {
			// 继续
		}
		try {
			return java.time.OffsetDateTime.parse(v).atZoneSameInstant(ZONE).toLocalDateTime();
		} catch (DateTimeParseException e) {
			throw new BadRequestException(label + "格式不正确，应为 yyyy-MM-dd HH:mm:ss");
		}
	}

	private static void assertSpanAllowed(LocalDateTime start, LocalDateTime end, Granularity g) {
		long hours = Duration.between(start, end).toHours();
		switch (g) {
			case HOUR -> {
				if (hours > 24L * 7) {
					throw new BadRequestException("小时粒度最多查询 7 天");
				}
			}
			case DAY -> {
				if (hours > 24L * 90) {
					throw new BadRequestException("日粒度最多查询 90 天，更长时间请改用周或月");
				}
			}
			case WEEK -> {
				if (hours > 24L * 366) {
					throw new BadRequestException("周粒度最多查询 1 年");
				}
			}
			default -> {
				// 月粒度不限跨度，但桶数仍受 buckets() 的保护
			}
		}
	}

	// ------------------------------------------------------------------
	// 对外的文本形态：传给 SQL 用
	// ------------------------------------------------------------------

	public String startText() {
		return PLAIN.format(start);
	}

	public String endText() {
		return PLAIN.format(end);
	}

	public Granularity granularity() {
		return granularity;
	}

	/** 粒度决定 SQL 用日聚合还是小时聚合 */
	public boolean hourly() {
		return granularity == Granularity.HOUR;
	}

	/**
	 * 上一等长区间，用于环比。
	 * 用 Duration 而不是「减一个月」——季度末和月末的长度不一样，等长才不会比错。
	 */
	public DashboardPeriod previous() {
		Duration span = Duration.between(start, end);
		return new DashboardPeriod(start.minus(span), start, granularity);
	}

	// ------------------------------------------------------------------
	// 桶序列
	// ------------------------------------------------------------------

	/**
	 * 生成完整的桶 key 序列（左闭右开），用于给 SQL 结果补零。
	 *
	 * <p>key 形态与 {@link #bucketFormat()} 的输出一致：
	 * 小时/日/周/月都归到「起点时间」，例如周桶是周一 00:00:00。
	 */
	public List<String> buckets() {
		List<String> keys = new ArrayList<>();
		LocalDateTime cursor = alignStart(start);
		// 兜底：桶数上限，防止月粒度填一个 20 年跨度把内存打满
		int guard = 0;
		while (cursor.isBefore(end) && guard++ < 5000) {
			keys.add(PLAIN.format(cursor));
			cursor = next(cursor);
		}
		if (keys.isEmpty()) {
			keys.add(PLAIN.format(alignStart(start)));
		}
		return keys;
	}

	/** SQL 侧生成 bucket key 用的格式串，与 {@link #buckets()} 一一对应 */
	public String bucketFormat() {
		return hourly() ? "%Y-%m-%d %H:00:00" : "%Y-%m-%d 00:00:00";
	}

	private LocalDateTime alignStart(LocalDateTime t) {
		return switch (granularity) {
			case HOUR -> t.truncatedTo(ChronoUnit.HOURS);
			case DAY -> t.toLocalDate().atStartOfDay();
			case WEEK -> mondayOf(t.toLocalDate()).atStartOfDay();
			case MONTH -> t.toLocalDate().withDayOfMonth(1).atStartOfDay();
		};
	}

	private LocalDateTime next(LocalDateTime cursor) {
		return switch (granularity) {
			case HOUR -> cursor.plusHours(1);
			case DAY -> cursor.plusDays(1);
			case WEEK -> cursor.plusWeeks(1);
			case MONTH -> cursor.plusMonths(1);
		};
	}

	private static LocalDate mondayOf(LocalDate date) {
		return date.minusDays(date.getDayOfWeek().getValue() - 1L);
	}

	/**
	 * 把「日」桶 key 归到当前粒度。
	 *
	 * <p>趋势 SQL 一律按日聚合（一次查询同时服务日/周/月三种粒度），
	 * 周和月在这里做二次合并，避免为每种粒度各写一套 SQL。
	 */
	public String bucketOf(LocalDate date) {
		return PLAIN.format(alignStart(date.atStartOfDay()));
	}

	@Override
	public String toString() {
		return "DashboardPeriod{" + startText() + " ~ " + endText() + ", " + granularity + '}';
	}
}
