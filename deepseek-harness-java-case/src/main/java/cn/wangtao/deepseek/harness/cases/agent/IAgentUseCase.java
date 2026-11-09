package cn.wangtao.deepseek.harness.cases.agent;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;

/**
 * Agent 交互用例契约。
 * <p>
 * 用例位于 Case 层，直接复用 API 层 DTO 作为入参/出参（最小壳阶段），
 * 后续章节若引入独立 Command/Result 对象，由 trigger 层门面完成映射。
 *
 * @author wt
 */
public interface IAgentUseCase {

    /**
     * 向 Agent 发送消息；如果 Agent 不存在则先创建。
     *
     * @param request Agent 消息发送请求
     * @return Agent 消息发送响应
     */
    AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request);

    /**
     * 使用实时增量接收器发送消息，用于流式输出。
     * <p>
     * 四个 sink 的职责：deltaSink 推文本增量、reasoningSink 推推理增量、
     * toolCallSink 推工具调用事件、finishSink 在流结束时回调。
     * 最小版用阻塞方式兜底实现，真流式在引入。
     */
    AgentMessageResponseDTO sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink);

    /**
     * 查询 Agent 当前状态。
     *
     * @param agentId Agent ID
     * @return 状态字符串，例如 idle、running、not-found
     */
    String getAgentStatus(String agentId);

    /**
     * 取消正在运行的 Agent。
     *
     * @param agentId Agent ID
     */
    void cancelAgent(String agentId);
}
