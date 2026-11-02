package cn.wangtao.deepseek.harness.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Agent 消息发送响应。
 *
 * @param agentId Agent ID
 * @param sessionId 会话 ID
 * @param status Agent 状态，例如 idle、running
 * @param messages 会话消息
 * @param artifacts 本回合写文件类工具产出的文件绝对路径清单（权威来源，可为 null）
 * @param error 回合异常信息（驱动器线程死亡等，正常完成为 null；异常时仍携带已收集的部分消息）
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 */
public record AgentMessageResponseDTO(
        String agentId,
        String sessionId,
        String status,
        List<Map<String, Object>> messages,
        List<String> artifacts,
        String error
) {
}
