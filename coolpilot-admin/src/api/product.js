import request, {rawRequest} from '@/util/request'

// ---------------- 商品 ----------------

export function getProductList(params) {
	return request({url: 'products', method: 'GET', params})
}

export function getProductDetail(id) {
	return request({url: `product/${id}`, method: 'GET'})
}

export function createProduct(data) {
	return request({url: 'product', method: 'POST', data})
}

export function updateProduct(id, data) {
	return request({url: `product/${id}`, method: 'PUT', data})
}

/**
 * 状态：DRAFT 草稿 / ON_SALE 已上架 / OFF_SALE 已下架 / ARCHIVED 已归档
 */
export function changeProductStatus(id, status) {
	return request({url: `product/${id}/status`, method: 'PUT', params: {status}})
}

// ---------------- 课时 ----------------

export function saveLesson(data) {
	return request({url: 'lesson', method: 'POST', data})
}

export function deleteLesson(id) {
	return request({url: `lesson/${id}`, method: 'DELETE'})
}

// ---------------- 资源 ----------------

export function bindResource(productId, data) {
	return request({url: `product/${productId}/resource`, method: 'POST', data})
}

// ---------------- 其他 ----------------

/**
 * 商品分类是公开接口（/productCategories），不在 /admin 下，必须用裸实例
 */
export function getProductCategories() {
	return rawRequest({url: 'productCategories', method: 'GET'})
}

/**
 * 复用后台的文件上传：返回 fileId（资源绑定要用）与 url（可直接放进 markdown）
 */
export function uploadFile(formData) {
	return request({
		url: 'file/upload',
		method: 'POST',
		data: formData,
		headers: {'Content-Type': 'multipart/form-data'}
	})
}
