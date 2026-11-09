package cn.wangtao.deepseek.harness.trigger.http;

import cn.wangtao.deepseek.harness.api.IAgentApi;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import cn.wangtao.deepseek.harness.cases.agent.IAgentUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent HTTP 入口（最小版：仅阻塞式 /message + 状态/取消）。
 * <p>
 * 控制器直接实现 IAgentApi 契约：接收 API DTO → 调用 Case 层用例
 * → 统一包装为 Response 返回（execute() 做异常分类）。
 * SSE 流式入口 /stream 在引入；鉴权拦截器、更多控制器随
 * part-07 控制台篇逐步长出，本节保持最小壳。
 *
 * @author wt
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController implements IAgentApi {

    private final IAgentUseCase agentUseCase;

    public AgentController(IAgentUseCase agentUseCase) {
        this.agentUseCase = agentUseCase;
    }

    /**
     * 阻塞式对话入口。
     * <p>
     * curl 测试：
     * <pre>
     * curl -X POST http://127.0.0.1:8090/api/agent/message \
     *   -H "Content-Type: application/json" \
     *   -d '{"agentId":"agent-001","message":"你好，介绍一下自己","channelCode":"deepseek"}'
     * </pre>
     * 异常 channelCode 可观察 invalidArgument 分支：
     * <pre>
     * curl -X POST http://127.0.0.1:8090/api/agent/message \
     *   -H "Content-Type: application/json" \
     *   -d '{"agentId":"agent-001","message":"hi","channelCode":"openai"}'
     * </pre>
     */
    @Override
    @PostMapping("/message")
    public Response<AgentMessageResponseDTO> sendMessage(@RequestBody AgentMessageRequestDTO request) {
        return execute(() -> agentUseCase.sendMessage(request));
    }

    @Override
    public Response<AgentMessageResponseDTO> sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink) {
        // 流式契约：接口已定义，SSE HTTP 入口在引入；本节透传给用例层兜底实现
        return execute(() -> agentUseCase.sendMessageStreaming(request, deltaSink, reasoningSink, toolCallSink, finishSink));
    }

    @Override
    public Response<String> getAgentStatus(String agentId) {
        return execute(() -> agentUseCase.getAgentStatus(agentId));
    }

    /**
     * 取消正在运行的 Agent（最小版空实现，恒返 success）。
     * <p>
     * curl 测试：
     * <pre>
     * curl -X POST http://127.0.0.1:8090/api/agent/agent-001/cancel
     * </pre>
     */
    @Override
    @PostMapping("/{agentId}/cancel")
    public Response<Void> cancelAgent(@PathVariable("agentId") String agentId) {
        return executeAction(() -> agentUseCase.cancelAgent(agentId));
    }

    /**
     * 统一执行与异常分类：用例结果包装为 Response.success；
     * IllegalArgumentException → invalidArgument（4xx 语义），
     * 其他 RuntimeException → internalError（5xx 语义）。
     * 调用方永远拿到结构化 Response，而非裸异常。
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

    /** 无返回值版本的 execute()，用于 cancel 等动作型接口，异常分类规则相同。 */
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
