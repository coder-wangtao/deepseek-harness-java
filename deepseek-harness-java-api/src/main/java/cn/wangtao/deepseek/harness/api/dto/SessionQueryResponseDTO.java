package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话查询响应。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param sessionId 会话 ID
 * @param workspaceId 所属工作区 ID
 * @param status 会话状态
 * @param modelCode 会话使用的模型编码
 * @param profileCode 会话绑定的执行档位编码
 * @param createdAt 会话创建时间
 * @param lastActivityAt 最近一次活动时间
 * @param messages 会话消息列表，每项为消息结构 Map
 */
public record SessionQueryResponseDTO(
        String sessionId,
        String workspaceId,
        String status,
        String modelCode,
        String profileCode,
        Instant createdAt,
        Instant lastActivityAt,
        List<Map<String, Object>> messages
) {
}
