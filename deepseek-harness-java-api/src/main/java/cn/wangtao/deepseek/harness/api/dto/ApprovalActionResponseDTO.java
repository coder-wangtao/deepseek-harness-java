package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 审批操作响应。
 * <p>返回对一次审批执行同意/拒绝后的处理结果。</p>
 *
 * @param sessionId 审批所属会话 ID
 * @param status 操作后的审批状态
 * @param message 操作结果说明
 * @param operatedAt 操作时间
 * @author wt
 */
public record ApprovalActionResponseDTO(
        String sessionId,
        String status,
        String message,
        Instant operatedAt
) {
}
