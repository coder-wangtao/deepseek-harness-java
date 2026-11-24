package cn.wangtao.deepseek.harness.trigger.service;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;
import cn.wangtao.deepseek.harness.api.stream.IAgentStreamApi;
import cn.wangtao.deepseek.harness.cases.agent.IAgentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * <p>
 * 【这个类是干啥的？】
 * 它是"回调式流式"与"HTTP SSE 推送"之间的翻译官：
 * <pre>
 *   用例层(回调 Sink) ──翻译──▶ SseEmitter(SSE 事件) ──▶ 前端
 * </pre>
 * 用例层只管往 4 个 Consumer 里塞增量，不关心怎么传给浏览器；
 * 本类把每个回调映射成一种 SSE 事件名，由 Spring 推给前端。
 * <p>
 * 执行流程：
 * 1. 创建 SseEmitter（0L = 不超时）；
 * 2. 在独立线程中调用用例的流式通道，delta/reasoning/finish 各自映射为 SSE 事件；
 * 3. 用例返回后补发最终聚合消息并完成 emitter。
 *
 * <p>SSE 事件命名：message.delta / message.reasoning / message.finish / message.done。
 *
 * @author wt
 */
@Service
public class AgentStreamApi implements IAgentStreamApi {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamApi.class);

    private final IAgentUseCase agentUseCase;

    public AgentStreamApi(IAgentUseCase agentUseCase) {
        this.agentUseCase = agentUseCase;
    }

    /**
     * 核心：SSE 流式推送。
     * <p>
     * 【为什么不直接在当前线程调 用例？】
     * SSE 的关键是"先立刻把 SseEmitter 还给 Spring（返回给前端建立连接），
     * 再慢慢往里推数据"。如果同步执行，前端要等到全部生成完才能拿到响应头，
     * 流式就失去意义了。所以开一个后台线程慢慢执行+推送。
     * <p>
     * 四个回调与 SSE 事件的映射关系：
     * <pre>
     *   deltaSink     → event: message.delta     （正文增量，一条一推）
     *   reasoningSink → event: message.reasoning （思考过程增量）
     *   toolCallSink  → event: message.toolcall  （工具调用事件）
     *   finishSink    → event: message.finish    （回合结束信号 [DONE]）
     *   ── 用例返回后 ──
     *   最终聚合文本  → event: message.done      （完整回答，供客户端校验）
     * </pre>
     */
    @Override
    public SseEmitter sendMessageStreaming(AgentMessageRequestDTO request) {
        // 0L 表示永不超时，由 emitter.complete() 主动收尾；此行先返回给 Spring，前端连接即刻建立
        SseEmitter emitter = new SseEmitter(0L); //Spring MVC 提供的 SSE 工具

        Thread worker = new Thread(() -> {
            try {
                // 调用用例的流式通道：传入四个 lambda 回调——
                // 用例每产生一段增量就会调用对应 lambda，lambda 内部再转成 SSE 事件推给前端。
                // 这就是"回调式流式"与"SSE 传输"的桥接点。
                AgentMessageResponseDTO result = agentUseCase.sendMessageStreaming(
                        request,
                        delta -> send(emitter, "message.delta", delta),
                        reasoning -> send(emitter, "message.reasoning", reasoning),
                        event -> send(emitter, "message.toolcall", event.toString()),
                        () -> send(emitter, "message.finish", "[DONE]")
                );
                // 走到这里说明用例同步执行完毕（流已结束），补发最终聚合消息：
                // 前端可以拿它和收到的 delta 累加结果比对，校验没有丢段
                if (result.messages() != null && !result.messages().isEmpty()) {
                    Object text = result.messages().get(0).get("text");
                    send(emitter, "message.done", text == null ? "" : text.toString());
                } else {
                    send(emitter, "message.done", "");
                }
                emitter.complete(); // 正常收尾：关闭 SSE 连接
            } catch (Exception exception) {
                log.error("[AgentStream] 流式发送失败 Agent={}", request.agentId(), exception);
                emitter.completeWithError(exception); // 异常收尾：Spring 会向前端发送错误并断开
            }
        }, "agent-stream-" + request.agentId()); // 线程名带上 agentId，方便日志排查
        worker.setDaemon(true); // daemon 线程：JVM 退出时不阻塞在未完成的推送上
        worker.start();
        return emitter;
    }

    /**
     * 单条 SSE 事件发送 + 兜底。
     * <p>
     * 【为什么 send 也要 try-catch？】推送过程中客户端可能随时关掉页面
     * （连接断开后 emitter.send 会抛异常）。这里选择"吞掉单条失败、只记警告"，
     * 避免一次网络抖动把整个流式任务炸掉——多推几条失败无伤大雅。
     */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception exception) {
            log.warn("[AgentStream] SSE 发送失败（客户端可能已断开）：{}", exception.getMessage());
        }
    }
}
