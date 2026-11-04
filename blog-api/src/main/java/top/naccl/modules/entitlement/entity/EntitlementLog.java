package top.naccl.modules.entitlement.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 权益变更流水（审计用，只增不改）
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class EntitlementLog {
	private Long id;
	private Long entitlementId;
	private Long userId;
	private Long productId;
	/** GRANT / REVOKE / EXPIRE / RENEW */
	private String action;
	private String reason;
	private Long operatorId;
	private Date createTime;
}
