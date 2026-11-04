package top.naccl.modules.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 人工转账凭证提交入参
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ManualConfirmDTO {
	private String payerName;
	/** yyyy-MM-dd HH:mm:ss，可空 */
	private String transferTime;
	private Long proofFileId;
}
