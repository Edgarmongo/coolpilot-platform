package top.naccl.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 访客注册信息
 * @Author: Naccl
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RegisterInfo {
	private String username;
	private String password;
	/**
	 * 昵称，可不填，默认取用户名
	 */
	private String nickname;
	/**
	 * 邮箱，可不填
	 */
	private String email;
}
