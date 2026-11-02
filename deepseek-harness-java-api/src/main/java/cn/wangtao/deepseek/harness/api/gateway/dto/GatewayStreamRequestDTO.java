package cn.wangtao.deepseek.harness.api.gateway.dto;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.StartWorkflowRequestDTO;

/**
 * 网关 SSE 流式订阅请求。
 * <p>按 source 选择订阅的事件流，并携带对应流的启动请求体。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param source 流来源标识，决定网关路由到哪类事件流；不可为空白
 * @param agent Agent 消息流启动请求；source 指向 Agent 流时必填
 * @param workflow 工作流启动请求；source 指向工作流时必填
 */
public record GatewayStreamRequestDTO(
        String source,
        AgentMessageRequestDTO agent,
        StartWorkflowRequestDTO workflow
) {
    public GatewayStreamRequestDTO {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must be non-blank");
        }
    }
}
