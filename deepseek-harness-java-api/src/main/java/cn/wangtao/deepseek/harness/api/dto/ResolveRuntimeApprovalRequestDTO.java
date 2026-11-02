package cn.wangtao.deepseek.harness.api.dto;

/**
 * 运行时审批处理请求。
 * <p>针对 {@code IRuntimeApprovalApi#resolve} 挂起的工具调用审批做出裁决。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param verdict 审批裁决结果，如同意 / 拒绝该次工具调用
 */
public record ResolveRuntimeApprovalRequestDTO(String verdict) {}
