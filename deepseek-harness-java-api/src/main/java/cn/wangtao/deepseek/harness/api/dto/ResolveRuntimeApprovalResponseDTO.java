package cn.wangtao.deepseek.harness.api.dto;

/**
 * 运行时审批处理响应。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param resolved 是否成功将审批项落为终态；false 表示审批不存在或已被处理
 * @param verdict 最终生效的裁决结果
 * @param error 处理失败时的错误说明；成功时为 null
 */
public record ResolveRuntimeApprovalResponseDTO(boolean resolved, String verdict, String error) {}
