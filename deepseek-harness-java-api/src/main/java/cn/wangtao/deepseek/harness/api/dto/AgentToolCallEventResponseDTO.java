package cn.wangtao.deepseek.harness.api.dto;

/**
 * Agent 工具调用事件响应。
 * <p>描述一次工具调用的参数、阶段、结果与耗时，用于前端渲染工具调用过程。</p>
 *
 * @param callId 调用唯一标识
 * @param toolName 工具名称
 * @param arguments 工具调用参数（JSON 字符串）
 * @param status 调用状态
 * @param phase 事件阶段：发起为 call，回填结果后为 result
 * @param result 工具执行结果；发起阶段为 null
 * @param durationMs 执行耗时（毫秒）；发起阶段为 null
 * @author wt
 */
public record AgentToolCallEventResponseDTO(
        String callId,
        String toolName,
        String arguments,
        String status,
        String phase,
        String result,
        Long durationMs
) {
    /**
     * 便捷构造：默认阶段为 call，无结果与耗时。
     */
    public AgentToolCallEventResponseDTO(String callId, String toolName, String arguments, String status) {
        this(callId, toolName, arguments, status, "call", null, null);
    }
}
