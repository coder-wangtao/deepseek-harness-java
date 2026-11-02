package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 会话摘要响应。
 * <p>用于最近会话列表展示，含标题、预览与归属信息。</p>
 *
 * @param sessionId 会话 ID
 * @param title 会话标题
 * @param preview 最近一条消息预览
 * @param status 会话状态
 * @param createdAt 会话创建时间
 * @param agentId 关联的 Agent ID
 * @param workspaceId 所属工作区 ID
 * @author wt
 */
public record ConversationSessionSummaryResponseDTO(
        String sessionId,
        String title,
        String preview,
        String status,
        Instant createdAt,
        String agentId,
        String workspaceId
) {
}
