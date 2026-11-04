package top.naccl.constant;

/**
 * JWT常量
 *
 * @author: Naccl
 * @date: 2022-01-23
 */
public class JwtConstants {
	/**
	 * 博主token前缀
	 */
	public static final String ADMIN_PREFIX = "admin:";

	/**
	 * 前台访客token前缀
	 * 访客与博主是两套身份：访客由前台自助注册产生，仅用于记录登录态，不具备任何后台权限
	 */
	public static final String VISITOR_PREFIX = "visitor:";
}
