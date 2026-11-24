package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * Agent 对话 API 契约（xfg-2-2 最小版：仅阻塞式 /message + 状态/取消）。
 * <p>
 * 控制器直接实现该契约：接收 API DTO → 调用 Case 层用例
 * → 统一包装为 Response 返回（execute() 做异常分类）。
 * SSE 流式入口 /stream 在 xfg-2-3 引入；鉴权拦截器、更多控制器随
 * part-07 控制台篇逐步长出，本节保持最小壳。
 *
 * @author wt
 */
public interface IAgentApi {
    /**
     * 向指定 Agent 发送一条用户消息，同步等待本回合执行完成。
     *
     * @param request 发送消息请求，包含 agentId、消息文本、模型渠道与审批模式等；agentId 与 message 不能为空
     * @return 本回合执行结果，包含会话 ID、Agent 状态、会话消息与产出文件清单
     */
    Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request);

    /**
     * 向指定 Agent 发送一条用户消息，并在执行过程中通过回调增量推送流式内容。
     *
     * @param request 发送消息请求，包含 agentId、消息文本、模型渠道与审批模式等；agentId 与 message 不能为空
     * @param deltaSink 正文增量文本回调，每收到一段正文增量触发一次
     * @param reasoningSink 推理增量文本回调，每收到一段推理（思考）增量触发一次
     * @param toolCallSink 工具调用事件回调，每次工具调用状态变化推送一个事件
     * @param finishSink 回合结束回调，本回合执行完成时触发一次
     * @return 本回合执行结果，包含会话 ID、Agent 状态、会话消息与产出文件清单
     */
    Response<AgentMessageResponseDTO> sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink);

    /**
     * 查询指定 Agent 的当前运行状态。
     *
     * @param agentId Agent ID
     * @return Agent 状态文本，例如 idle、running
     */
    Response<String> getAgentStatus(String agentId);

    /**
     * 取消指定 Agent 的正在执行的运行。
     *
     * @param agentId Agent ID
     * @return 取消结果，仅包含状态码与说明，无业务数据
     */
    Response<Void> cancelAgent(String agentId);
}
