package top.naccl.model.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * @Description: 分页结果
 * @Author: Naccl
 * @Date: 2020-08-08
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class PageResult<T> {
	private Integer totalPage;//总页数
	private Integer total;//总条数（新增，向后兼容：老前端不读该字段不受影响）
	private List<T> list;//数据

	public PageResult(Integer totalPage, List<T> list) {
		this.totalPage = totalPage;
		this.list = list;
	}

	public PageResult(Integer totalPage, Integer total, List<T> list) {
		this.totalPage = totalPage;
		this.total = total;
		this.list = list;
	}
}
