package com.coolskyai.modules.service;

import com.coolskyai.modules.service.entity.Project;
import com.coolskyai.modules.service.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目列表查询的回归测试。
 *
 * <p>为支持「逾期里程碑」跳转，{@code ProjectMapper.COLUMNS} 与分页查询都改过。
 * 常量被带 <code>&lt;script&gt;</code> 和不带的查询共用，一旦转义写错，
 * 整个项目管理页会直接报 SQL 语法错误 —— 这是最容易连带打翻现有功能的一处改动，所以单独守住。
 *
 * <p>需要数据库才跑。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-21
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class ProjectQueryTest {

	@Autowired
	private ProjectMapper projectMapper;

	@Test
	void listQueriesWorkWithAndWithoutOverdue() {
		// 不带筛选：项目管理页的默认查询，必须照常可用
		List<Project> all = assertDoesNotThrow(() -> projectMapper.page(null, null, null, null, 0, 10),
				"默认分页查询不应报错");
		assertNotNull(all);

		// 只看逾期：仪表盘待办跳转用
		List<Project> overdue = assertDoesNotThrow(() -> projectMapper.page(null, null, null, true, 0, 10),
				"逾期筛选查询不应报错");
		assertNotNull(overdue);
		assertTrue(overdue.stream().allMatch(p -> p.getOverdueCount() != null && p.getOverdueCount() > 0),
				"逾期筛选出来的项目，逾期里程碑数必须大于 0");

		// 计数与列表口径必须一致
		int total = assertDoesNotThrow(() -> projectMapper.count(null, null, null, true));
		assertTrue(total >= overdue.size(), "逾期总数不应小于当页条数");

		// 不带筛选的计数
		assertDoesNotThrow(() -> projectMapper.count(null, null, null, null));
	}

	@Test
	void singleProjectQueriesStillWork() {
		// COLUMNS 变了，所有复用它的单条查询都要能解析
		assertDoesNotThrow(() -> projectMapper.findById(-1L));
		assertDoesNotThrow(() -> projectMapper.findByNo("NOT_EXIST"));
		assertDoesNotThrow(() -> projectMapper.listByDemand(-1L));
	}
}
