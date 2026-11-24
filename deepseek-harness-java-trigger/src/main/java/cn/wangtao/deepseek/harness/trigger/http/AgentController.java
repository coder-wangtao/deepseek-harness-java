package cn.wangtao.deepseek.harness.trigger.http;

import cn.wangtao.deepseek.harness.api.IAgentApi;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import cn.wangtao.deepseek.harness.api.stream.IAgentStreamApi;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent HTTP 入口（xfg-2-3：阻塞 /message + SSE /stream + 取消 /{agentId}/cancel）。
 * <p>
 * 职责划分：阻塞式与取消委托 {@link IAgentApi}（业务契约），
 * 流式委托 {@link IAgentStreamApi}（SSE 传输契约，返回 SseEmitter
 * 由后台线程推送）。控制器本身不承载业务逻辑，只做路由转发。
 * <p>
 * 鉴权拦截器、更多控制器随 part-07 控制台篇逐步长出，本节保持最小壳。
 *
 * @author wt
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final IAgentApi agentApi;
    private final IAgentStreamApi agentStreamApi;

    public AgentController(IAgentApi agentApi, IAgentStreamApi agentStreamApi) {
        this.agentApi = agentApi;
        this.agentStreamApi = agentStreamApi;
    }

    /**
     * 阻塞式对话入口：POST /api/agent/message。
     * <p>
     * 委托 {@link IAgentApi#sendMessage}，同步等待本回合执行完成后
     * 返回完整响应（会话 ID、Agent 状态、会话消息），无流式过程。
     * <p>
     * curl 测试（正常分支）：
     * <pre>
     * curl -X POST http://127.0.0.1:8090/api/agent/message \
     *   -H "Content-Type: application/json" \
     *   -d '{"agentId":"agent-001","message":"你好，介绍一下自己","channelCode":"deepseek"}'
     * </pre>
     */
    @PostMapping("/message")
    public Response<AgentMessageResponseDTO> sendMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentApi.sendMessage(request);
    }

    /**
     * SSE 流式对话入口：POST /api/agent/stream。
     * <p>
     * 委托 {@link IAgentStreamApi#sendMessageStreaming}，返回 SseEmitter 后
     * 由实现方在后台线程逐段推送事件，前端用 EventSource/fetch 流式读取。
     * <p>
     * 技术实现：SseEmitter 是 Spring MVC 对 Server-Sent Events（SSE）协议的封装。
     * 接口返回 SseEmitter 对象后 Servlet 线程立即释放（不阻塞请求线程），
     * 实现方持有该 emitter，在后台线程中通过 emitter.send() 持续向这条
     * HTTP 长连接写出 text/event-stream 格式的分段数据，实现服务端向
     * 浏览器的单向推送（对比 WebSocket 的全双工，SSE 更轻量，只需 HTTP）。
     * 这是 AI 大模型流式输出（打字机效果）的主流后端方案之一。
     * <p>
     * curl 测试（-N 关闭 curl 缓冲，可肉眼看到事件逐段到达）：
     * <pre>
     * curl -N -X POST http://127.0.0.1:8090/api/agent/stream \
     *   -H "Content-Type: application/json" \
     *   -d '{"agentId":"agent-001","message":"你好，介绍一下自己"}'
     * </pre>
     * 预期依次收到三段式 SSE 事件：
     * <pre>
     * event: meta   data: {"agentId":"agent-001","status":"running"}   ← 会话元信息
     * event: delta  data: 这是                                       ← 多条，正文逐段增量
     * event: done   data: {"status":"finished"}                      ← 回合结束
     * </pre>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentStreamApi.sendMessageStreaming(request);
    }

    /**
     * 取消正在运行的 Agent：POST /api/agent/{agentId}/cancel。
     * <p>
     * 委托 {@link IAgentApi#cancelAgent}，仅返回状态码与说明，无业务数据。
     * <p>
     * curl 测试：
     * <pre>
     * curl -X POST http://127.0.0.1:8090/api/agent/agent-001/cancel
     * </pre>
     */
    @PostMapping("/{agentId}/cancel")
    public Response<Void> cancelAgent(@PathVariable("agentId") String agentId) {
        return agentApi.cancelAgent(agentId);
    }

}
