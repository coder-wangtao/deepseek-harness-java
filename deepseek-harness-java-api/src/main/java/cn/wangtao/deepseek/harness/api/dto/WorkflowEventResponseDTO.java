package cn.wangtao.deepseek.harness.api.dto;

/**
 * 工作流运行事件。
 * <p>工作流执行过程中推送给调用方的一条事件（含流式输出）。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param runId 工作流运行 ID
 * @param event 事件类型，如输出、阶段推进、完成等
 * @param status 运行状态
 * @param output 事件携带的输出内容
 * @param error 事件携带的错误信息；正常时为 null
 */
public record WorkflowEventResponseDTO(
        String runId,
        String event,
        String status,
        String output,
        String error
) {
}
