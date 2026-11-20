package com.coolskyai.util.quartz;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import com.coolskyai.util.common.SpringContextUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 反射调用的三种形态：有参、无参、私有方法。
 * 用 mock 的 ApplicationContext 顶替容器，不启 Spring。
 */
class ScheduleRunnableTest {

	/** 被调用的目标：记录调用次数与参数 */
	static class DemoTask {
		int noArgCalls;
		int stringCalls;
		String lastParam;

		public void noArg() {
			noArgCalls++;
		}

		public void withArg(String param) {
			stringCalls++;
			lastParam = param;
		}

		private void secret() {
			noArgCalls += 10;
		}

		public void boom() {
			throw new IllegalStateException("业务炸了");
		}
	}

	private final DemoTask task = new DemoTask();

	@BeforeEach
	void setUp() {
		ApplicationContext context = mock(ApplicationContext.class);
		when(context.getBean("demoTask")).thenReturn(task);
		new SpringContextUtils().setApplicationContext(context);
	}

	@AfterEach
	void tearDown() {
		// 静态上下文会留给下一个测试类，清掉避免串味
		Mockito.reset();
	}

	@Test
	void 无参方法被调用() throws Exception {
		new ScheduleRunnable("demoTask", "noArg", null).run();
		assertThat(task.noArgCalls).isEqualTo(1);
	}

	@Test
	void 带参方法收到参数() throws Exception {
		new ScheduleRunnable("demoTask", "withArg", "hello").run();
		assertThat(task.stringCalls).isEqualTo(1);
		assertThat(task.lastParam).isEqualTo("hello");
	}

	@Test
	void 空参数串按无参方法处理() throws Exception {
		new ScheduleRunnable("demoTask", "noArg", "  ").run();
		assertThat(task.noArgCalls).isEqualTo(1);
	}

	@Test
	void 私有方法也能调用() throws Exception {
		new ScheduleRunnable("demoTask", "secret", null).run();
		assertThat(task.noArgCalls).isEqualTo(10);
	}

	@Test
	void 业务异常被包成统一异常并保留根因() throws Exception {
		ScheduleRunnable runnable = new ScheduleRunnable("demoTask", "boom", null);

		RuntimeException thrown = null;
		try {
			runnable.run();
		} catch (RuntimeException e) {
			thrown = e;
		}

		assertThat(thrown).isNotNull();
		assertThat(thrown).hasMessage("执行定时任务失败");
		assertThat(thrown.getCause()).isInstanceOf(java.lang.reflect.InvocationTargetException.class);
	}

	@Test
	void 方法不存在时构造阶段就失败() {
		assertThatThrownBy(() -> new ScheduleRunnable("demoTask", "notExists", null))
				.isInstanceOf(NoSuchMethodException.class);
	}

	@Test
	void 缺少bean名时直接报参数错误() {
		assertThatThrownBy(() -> new ScheduleRunnable("", "noArg", null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
