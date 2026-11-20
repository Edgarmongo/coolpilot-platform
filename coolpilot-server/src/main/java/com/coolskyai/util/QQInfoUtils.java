package com.coolskyai.util;

import org.springframework.web.client.RestTemplate;
import com.coolskyai.model.vo.QqResultVO;
import com.coolskyai.model.vo.QqVO;
import com.coolskyai.util.upload.UploadUtils;

/**
 * @Description: QQ 昵称与头像
 *
 * 头像直接用官方 qlogo 地址（稳定、无需鉴权），昵称要经过第三方接口，
 * 接口随时可能变或挂 —— 所以昵称取不到时返回 "nickname"，不让评论流程卡死。
 *
 * 注意：这类第三方接口会拿到访客填的 QQ 号，只应在访客明确填写 QQ 时调用。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-09-10
 */
public final class QQInfoUtils {

	private static RestTemplate restTemplate = new RestTemplate();

	/** 原接口半失效（需要 cookie），此处为社区维护的备用接口 */
	private static final String NICKNAME_API = "https://api.toubiec.cn/api/qqinfo_v4.php?qq={1}";

	/** 官方头像地址，s=100 表示 100×100 */
	private static final String AVATAR_URL = "https://q.qlogo.cn/g?b=qq&nk=%s&s=100";

	private static final String NICKNAME_FALLBACK = "nickname";

	/** QQ 号：5–11 位，首位非 0 */
	private static final String QQ_PATTERN = "^[1-9][0-9]{4,10}$";

	private QQInfoUtils() {
	}

	/**
	 * 测试用：替换掉默认的 RestTemplate（默认实例不出网也能用，替换后接 Mock）
	 */
	static void setRestTemplate(RestTemplate template) {
		QQInfoUtils.restTemplate = template;
	}

	/**
	 * 取 QQ 昵称。第三方接口不可用或返回结构异常时回落到 "nickname"
	 */
	public static String getQQNickname(String qq) {
		QqResultVO result = restTemplate.getForObject(NICKNAME_API, QqResultVO.class, qq);
		if (result == null || result.getData() == null) {
			return NICKNAME_FALLBACK;
		}
		QqVO info = JacksonUtils.convertValue(result.getData(), QqVO.class);
		if (info == null || StringUtils.isEmpty(info.getName())) {
			return NICKNAME_FALLBACK;
		}
		return info.getName();
	}

	/**
	 * 取 QQ 头像并转存到本站存储，返回可直接访问的地址
	 */
	public static String getQQAvatarUrl(String qq) throws Exception {
		return UploadUtils.upload(getImageResourceByQQ(qq));
	}

	private static UploadUtils.ImageResource getImageResourceByQQ(String qq) {
		return UploadUtils.getImageByRequest(String.format(AVATAR_URL, qq));
	}

	/**
	 * 是否像 QQ 号。只做格式判断，不保证该号码真实存在
	 */
	public static boolean isQQNumber(String number) {
		return number != null && number.matches(QQ_PATTERN);
	}
}
