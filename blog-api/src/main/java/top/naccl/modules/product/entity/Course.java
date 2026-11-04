package top.naccl.modules.product.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 课程扩展
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Course {
	private Long id;
	private Long productId;
	private String description;
	private Integer lessonCount;
	private Integer totalDuration;
	private Date createTime;
	private Date updateTime;
}
