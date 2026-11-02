package cn.wangtao.deepseek.harness.api.dto;

/**
 * 工作流启动响应。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param runId 本次工作流运行 ID，用于后续事件订阅与状态查询
 * @param status 启动后的运行状态
 */
public record WorkflowStartResponseDTO(String runId, String status) {
}
