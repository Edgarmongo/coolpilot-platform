package com.coolskyai.modules.product.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.product.dto.ProductSaveDTO;
import com.coolskyai.modules.product.entity.Course;
import com.coolskyai.modules.product.entity.Lesson;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.product.entity.ProductCategory;
import com.coolskyai.modules.product.entity.ProductResource;
import com.coolskyai.modules.product.mapper.CourseMapper;
import com.coolskyai.modules.product.mapper.LessonMapper;
import com.coolskyai.modules.product.mapper.ProductCategoryMapper;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.product.mapper.ProductResourceMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description: 知识商品
 *
 * 价格规则：金额由服务端校验，不接受前端传入的负数或非法类型。
 * 上架规则：DRAFT/OFF_SALE 不可被前台查到；上下架通过独立接口控制。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Service
public class ProductService {
	private static final Set<String> TYPES = Set.of("DOC", "COURSE", "COLUMN", "CODE", "SERVICE");
	private static final Set<String> STATUSES = Set.of("DRAFT", "ON_SALE", "OFF_SALE", "ARCHIVED");
	private static final Set<String> PRICE_MODES = Set.of("FIXED", "STARTING", "CUSTOM");

	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private ProductCategoryMapper productCategoryMapper;
	@Autowired
	private CourseMapper courseMapper;
	@Autowired
	private LessonMapper lessonMapper;
	@Autowired
	private ProductResourceMapper productResourceMapper;

	// ---------------- 前台 ----------------

	public PageResult<Product> pageOnSale(String type, Long categoryId, String keyword, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize, 12);
		int total = productMapper.countOnSale(type, categoryId, keyword);
		List<Product> list = productMapper.pageOnSale(type, categoryId, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 商品详情（前台），浏览量 +1
	 */
	public Map<String, Object> detail(String productNo) {
		Product product = productMapper.findOnSaleByNo(productNo);
		if (product == null) {
			throw new BadRequestException("商品不存在或已下架");
		}
		productMapper.increaseView(product.getId());

		Map<String, Object> data = new HashMap<>(8);
		data.put("product", product);
		// 详情用与文章一致的 Markdown 渲染链，保证排版统一
		data.put("detailHtml", com.coolskyai.util.markdown.MarkdownUtils.markdownToHtml(
				product.getDetail() == null ? "" : product.getDetail()));
		data.put("resources", productResourceMapper.listByProduct(product.getId()));
		if ("COURSE".equals(product.getProductType())) {
			Course course = courseMapper.findByProductId(product.getId());
			data.put("course", course);
		}
		return data;
	}

	/**
	 * 课程目录（前台）
	 */
	public Map<String, Object> catalog(String productNo) {
		Product product = productMapper.findOnSaleByNo(productNo);
		if (product == null) {
			throw new BadRequestException("商品不存在或已下架");
		}
		Course course = courseMapper.findByProductId(product.getId());
		List<Lesson> lessons = course == null ? List.of() : lessonMapper.listByCourse(course.getId());
		// 目录只返回标题与试看标记，不返回正文，避免绕过购买
		Map<String, Object> data = new HashMap<>(4);
		data.put("course", course);
		data.put("lessons", lessons);
		return data;
	}

	public List<ProductCategory> categories(String type) {
		return productCategoryMapper.listByType(type);
	}

	// ---------------- 后台 ----------------

	public PageResult<Product> pageAdmin(String type, String status, String keyword, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize, 10);
		int total = productMapper.countAdmin(type, status, keyword);
		List<Product> list = productMapper.pageAdmin(type, status, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public Map<String, Object> adminDetail(Long id) {
		Product product = productMapper.findById(id);
		if (product == null) {
			throw new BadRequestException("商品不存在");
		}
		Map<String, Object> data = new HashMap<>(8);
		data.put("product", product);
		data.put("resources", productResourceMapper.listByProduct(id));
		Course course = courseMapper.findByProductId(id);
		data.put("course", course);
		if (course != null) {
			data.put("lessons", lessonMapper.listByCourse(course.getId()));
		}
		return data;
	}

	@Transactional(rollbackFor = Exception.class)
	public Long create(ProductSaveDTO dto, Long ownerId) {
		Product product = buildProduct(dto, null);
		product.setProductNo(generateProductNo());
		product.setStatus("DRAFT");
		product.setOwnerId(ownerId);
		productMapper.insert(product);

		if ("COURSE".equals(product.getProductType())) {
			Course course = new Course();
			course.setProductId(product.getId());
			course.setDescription(dto.getCourseDescription() == null ? "" : dto.getCourseDescription());
			courseMapper.insert(course);
		}
		return product.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void update(Long id, ProductSaveDTO dto) {
		Product exists = productMapper.findById(id);
		if (exists == null) {
			throw new BadRequestException("商品不存在");
		}
		Product product = buildProduct(dto, id);
		productMapper.update(product);

		if ("COURSE".equals(product.getProductType())) {
			Course course = courseMapper.findByProductId(id);
			if (course == null) {
				course = new Course();
				course.setProductId(id);
				course.setDescription(dto.getCourseDescription() == null ? "" : dto.getCourseDescription());
				courseMapper.insert(course);
			} else if (dto.getCourseDescription() != null) {
				courseMapper.updateDescription(id, dto.getCourseDescription());
			}
		}
	}

	public void changeStatus(Long id, String status) {
		if (!STATUSES.contains(status)) {
			throw new BadRequestException("非法状态：" + status);
		}
		productMapper.changeStatus(id, status);
	}

	// ---------------- 课时 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Long saveLesson(Lesson lesson) {
		if (lesson.getCourseId() == null) {
			throw new BadRequestException("课程ID不能为空");
		}
		if (lesson.getTitle() == null || lesson.getTitle().trim().isEmpty()) {
			throw new BadRequestException("课时标题不能为空");
		}
		if (lesson.getSort() == null) {
			lesson.setSort(0);
		}
		if (lesson.getDuration() == null) {
			lesson.setDuration(0);
		}
		if (lesson.getIsFreePreview() == null) {
			lesson.setIsFreePreview(false);
		}
		if (lesson.getContentType() == null) {
			lesson.setContentType("DOC");
		}
		if (lesson.getId() == null) {
			lessonMapper.insert(lesson);
		} else {
			lessonMapper.update(lesson);
		}
		courseMapper.refreshStats(lesson.getCourseId());
		return lesson.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void deleteLesson(Long id) {
		Lesson lesson = lessonMapper.findById(id);
		if (lesson == null) {
			return;
		}
		lessonMapper.delete(id);
		courseMapper.refreshStats(lesson.getCourseId());
	}

	// ---------------- 资源绑定 ----------------

	public Long bindResource(ProductResource resource) {
		if (resource.getProductId() == null || resource.getFileId() == null) {
			throw new BadRequestException("商品ID与文件ID不能为空");
		}
		if (resource.getResourceType() == null) {
			resource.setResourceType("DOC");
		}
		if (resource.getVersion() == null) {
			resource.setVersion("v1");
		}
		if (resource.getSort() == null) {
			resource.setSort(0);
		}
		resource.setIsLatest(true);
		productResourceMapper.insert(resource);
		return resource.getId();
	}

	// ---------------- 内部 ----------------

	private Product buildProduct(ProductSaveDTO dto, Long id) {
		if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
			throw new BadRequestException("商品标题不能为空");
		}
		if (dto.getProductType() == null || !TYPES.contains(dto.getProductType())) {
			throw new BadRequestException("非法商品类型：" + dto.getProductType());
		}
		String priceMode = dto.getPriceMode() == null ? "FIXED" : dto.getPriceMode();
		if (!PRICE_MODES.contains(priceMode)) {
			throw new BadRequestException("非法定价方式：" + priceMode);
		}
		BigDecimal price = dto.getPrice() == null ? BigDecimal.ZERO : dto.getPrice();
		if (price.compareTo(BigDecimal.ZERO) < 0) {
			throw new BadRequestException("价格不能为负数");
		}
		if (dto.getOriginalPrice() != null && dto.getOriginalPrice().compareTo(BigDecimal.ZERO) < 0) {
			throw new BadRequestException("原价不能为负数");
		}

		Product product = new Product();
		product.setId(id);
		product.setProductType(dto.getProductType());
		product.setCategoryId(dto.getCategoryId() == null ? 0L : dto.getCategoryId());
		product.setTitle(dto.getTitle().trim());
		product.setSubtitle(dto.getSubtitle() == null ? "" : dto.getSubtitle());
		product.setCover(dto.getCover() == null ? "" : dto.getCover());
		product.setSummary(dto.getSummary() == null ? "" : dto.getSummary());
		product.setDetail(dto.getDetail() == null ? "" : dto.getDetail());
		product.setPrice(price);
		product.setOriginalPrice(dto.getOriginalPrice());
		product.setPriceMode(priceMode);
		product.setSort(dto.getSort() == null ? 0 : dto.getSort());
		product.setIsTop(dto.getIsTop() != null && dto.getIsTop());
		return product;
	}

	private String generateProductNo() {
		String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		for (int i = 0; i < 20; i++) {
			String no = "P" + date + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
			if (productMapper.countByNo(no) == 0) {
				return no;
			}
		}
		throw new IllegalStateException("生成商品编号失败，请重试");
	}

	private int normalizePage(Integer pageNum) {
		return (pageNum == null || pageNum < 1) ? 1 : pageNum;
	}

	private int normalizeSize(Integer pageSize, int max) {
		if (pageSize == null || pageSize < 1) {
			return Math.min(10, max);
		}
		return Math.min(pageSize, max);
	}
}
