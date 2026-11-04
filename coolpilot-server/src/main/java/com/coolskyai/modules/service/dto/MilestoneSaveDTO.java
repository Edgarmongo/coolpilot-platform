package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @Description: 新增/修改里程碑
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class MilestoneSaveDTO {
	private Long projectId;
	private String name;
	private Integer sort;
	private BigDecimal amount;
	private LocalDate dueDate;
	private String remark;
}
