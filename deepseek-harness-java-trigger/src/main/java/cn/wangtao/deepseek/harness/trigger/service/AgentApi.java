package cn.wangtao.deepseek.harness.trigger.service;

import cn.wangtao.deepseek.harness.api.IAgentApi;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import cn.wangtao.deepseek.harness.cases.agent.IAgentUseCase;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 【这个类是干啥的？】
 * 它是 HTTP 控制器与业务用例之间的"中间人"：
 * AgentController 收到请求后调用本类，本类再调用 Case 层的用例。
 * 好处是职责分层——控制器只管路由，业务规则全部收敛在 Case 层，
 * 本类专职做"协议适配"：把业务结果统一包成 Response 结构。
 * <p>
 * 执行流程：
 * 1. 接收触发层传入的 API DTO；
 * 2. 调用 Case 层用例；
 * 3. 统一包装为 Response 返回。
 *
 * @author wt
 */
@Service
public class AgentApi implements IAgentApi {

    private final IAgentUseCase agentUseCase;

    public AgentApi(IAgentUseCase agentUseCase) {
        this.agentUseCase = agentUseCase;
    }

    /**
     * 阻塞式对话：同步等待 Agent 执行完本回合，一次性返回完整结果。
     * <p>
     * execute() 负责异常分类（见下方私有方法注释），
     * 这里只关心"把请求转交用例、把结果转回调用方"。
     */
    @Override
    public Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request) {
        return execute(() -> agentUseCase.sendMessage(request));
    }

    /**
     * 流式对话（回调式）：方法本身仍同步返回最终聚合结果，
     * 但执行过程中的增量内容通过四个"水槽"（Sink）实时流出去：
     * <ul>
     *   <li>deltaSink     —— 正文增量：模型每生成一小段文本就回调一次</li>
     *   <li>reasoningSink —— 思考增量：推理模型输出的思考过程文本</li>
     *   <li>toolCallSink  —— 工具调用事件：Agent 调用工具时推送状态变化</li>
     *   <li>finishSink    —— 结束信号：本回合执行完毕时触发一次</li>
     * </ul>
     * 本类不关心 Sink 背后是谁（可能是 SSE 推送、控制台打印），
     * 只负责把调用方传入的回调原样透传给用例——传输细节由上层决定。
     */
    @Override
    public Response<AgentMessageResponseDTO> sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink) {
        return execute(() -> agentUseCase.sendMessageStreaming(request, deltaSink, reasoningSink, toolCallSink, finishSink));
    }

    /** 查询 Agent 当前状态（idle / running 等），同样走统一异常包装。 */
    @Override
    public Response<String> getAgentStatus(String agentId) {
        return execute(() -> agentUseCase.getAgentStatus(agentId));
    }

    /** 取消正在运行的 Agent。无返回数据的动作型接口，用 executeAction 包装。 */
    @Override
    public Response<Void> cancelAgent(String agentId) {
        return executeAction(() -> agentUseCase.cancelAgent(agentId));
    }

    /**
     * 统一执行与异常分类（带返回值版本）。
     * <p>
     * 【为什么要包一层？】保证调用方永远拿到结构化的 Response，
     * 而不是裸抛异常导致 HTTP 500 大白页：
     * <ul>
     *   <li>正常       → Response.success(结果)</li>
     *   <li>非法参数   → invalidArgument（4xx 语义，是调用方的错）</li>
     *   <li>其他异常   → internalError（5xx 语义，是服务内部的错）</li>
     * </ul>
     * Supplier<T> 是 Java 函数式接口：传入一段"生产 T 的逻辑"，
     * execute 负责执行它并兜住异常。
     */
    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }

    /** 无返回值版本的 execute()（Runnable），用于 cancel 这类动作型接口，异常分类规则相同。 */
    private Response<Void> executeAction(Runnable action) {
        try {
            action.run();
            return Response.success(null);
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }

}
