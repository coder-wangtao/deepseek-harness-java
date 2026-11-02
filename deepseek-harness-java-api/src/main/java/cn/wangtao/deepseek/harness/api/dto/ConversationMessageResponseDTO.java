package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话消息响应。
 * <p>按会话回放历史消息，既提供纯文本内容，也保留结构化消息块。</p>
 *
 * @param role 消息角色（如 user / assistant）
 * @param content 消息文本内容
 * @param blocks 结构化消息块列表；每项为键值对描述的段落
 * @param occurredAt 消息发生时间
 * @author wt
 */
public record ConversationMessageResponseDTO(
        String role,
        String content,
        List<Map<String, Object>> blocks,
        Instant occurredAt
) {
}
