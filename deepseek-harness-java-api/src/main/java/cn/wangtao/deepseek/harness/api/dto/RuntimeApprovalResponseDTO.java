package cn.wangtao.deepseek.harness.api.dto;

import java.util.Map;

/**
 * 待处理运行时审批项。
 * <p>Agent 运行中触发需要用户授权的工具调用时挂起的审批单，前端据此渲染审批卡。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param approvalId 审批单 ID，用于后续裁决提交
 * @param sessionId 所属会话 ID
 * @param toolName 待授权的工具名称
 * @param displayCommand 展示给用户的命令文本预览
 * @param arguments 工具调用参数
 * @param createdAt 审批单创建时间
 */
public record RuntimeApprovalResponseDTO(
        String approvalId,
        String sessionId,
        String toolName,
        String displayCommand,
        Map<String, Object> arguments,
        String createdAt
) {}
