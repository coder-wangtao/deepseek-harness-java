package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话恢复响应。
 * <p>返回从事件溯源记录重建出的会话运行时快照。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param sessionId 会话 ID
 * @param restored 是否恢复成功
 * @param agentPhase 恢复后的 Agent 阶段
 * @param turnCount 已恢复的轮次数量
 * @param stepCount 已恢复的步骤数量
 * @param lastTurnEndReason 最后一轮的结束原因
 * @param messages 重建出的会话消息列表，每项为消息结构 Map
 * @param restoredAt 恢复完成时间
 */
public record SessionRestoreResponseDTO(
        String sessionId,
        boolean restored,
        String agentPhase,
        int turnCount,
        int stepCount,
        String lastTurnEndReason,
        List<Map<String, Object>> messages,
        Instant restoredAt
) {
}
