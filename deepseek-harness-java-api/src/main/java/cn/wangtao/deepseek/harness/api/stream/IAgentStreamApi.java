package cn.wangtao.deepseek.harness.api.stream;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 流式对话接口：负责以 SSE 方式流式推送 Agent 对话内容。
 *
 * @author wt
 */
public interface IAgentStreamApi {

    /**
     * 向 Agent 发送一条用户消息，并通过 SSE 流式推送本回合的正文增量、推理增量与工具调用事件。
     *
     * @param request 发送消息请求，包含 agentId、消息文本、模型渠道与审批模式等；agentId 与 message 不能为空
     * @return SSE 推送器，用于向前端持续推送流式对话事件
     */
    SseEmitter sendMessageStreaming(AgentMessageRequestDTO request);
}
