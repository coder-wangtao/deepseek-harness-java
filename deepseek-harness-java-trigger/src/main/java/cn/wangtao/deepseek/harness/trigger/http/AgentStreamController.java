package cn.wangtao.deepseek.harness.trigger.http;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.stream.IAgentStreamApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 流式对话控制器：把 api 契约中的 SSE 流式接口暴露为 HTTP 端点。
 *
 * @author 小傅哥
 * @website bugstack.cn
 */
@RestController
@RequestMapping("/api/agent")
public class AgentStreamController {

    private final IAgentStreamApi agentStreamApi;

    public AgentStreamController() {
        this.agentStreamApi = new AgentStreamApi();
    }

    /**
     * curl -N -X POST http://127.0.0.1:8090/api/agent/stream \
     *   -H "Content-Type: application/json" \
     *   -d '{"agentId":"agent-001","message":"你好"}'
     *
     * POST /api/agent/stream —— SSE 流式对话。
     * produces 声明 text/event-stream，前端用 EventSource/fetch 流式读取。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentStreamApi.sendMessageStreaming(request);
    }

    static class AgentStreamApi implements IAgentStreamApi {

        private static final Logger log = LoggerFactory.getLogger(AgentStreamApi.class);

        /**
         * 演示用回答内容：按空格拆成一段一段增量推送，模拟模型逐 token 输出
         */
        private static final String MOCK_ANSWER =
                "这是 一条 流式 回应： Agent 正在 通过 SSE 一段 一段 地 把 结果 推 给 你，而不是 攒完 一次 返回。";

        /**
         * 缓存型 daemon 线程池：每条流占一个线程直到推送结束
         */
        private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sse-stream-worker");
            t.setDaemon(true);
            return t;
        });

        @Override
        public SseEmitter sendMessageStreaming(AgentMessageRequestDTO request) {
            log.info("[SSE] 收到流式请求 agentId={}", request.agentId());
            SseEmitter emitter = new SseEmitter(0L);   // 0 = 不设超时，由 complete 主动收尾
            sseExecutor.execute(() -> emitStream(request, emitter));
            return emitter;
        }

        /**
         * 模拟 Agent 执行：meta → 逐段 delta → done，三段式 SSE 事件
         */
        private void emitStream(AgentMessageRequestDTO request, SseEmitter emitter) {
            try {
                // 1. meta 事件：告诉前端本次会话信息
                emitter.send(SseEmitter.event().name("meta")
                        .data("{\"agentId\":\"" + request.agentId() + "\",\"status\":\"running\"}"));

                // 2. delta 事件：逐段推送正文增量（真实场景由模型逐 token 回调驱动）
                for (String delta : MOCK_ANSWER.split(" ")) {
                    emitter.send(SseEmitter.event().name("delta").data(delta));
                    Thread.sleep(200);   // 模拟模型生成耗时，让"流式"肉眼可见
                }

                // 3. done 事件：回合结束
                emitter.send(SseEmitter.event().name("done").data("{\"status\":\"finished\"}"));
                emitter.complete();
                log.info("[SSE] 推送完成 agentId={}", request.agentId());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
                log.warn("[SSE] 推送中断 agentId={}", request.agentId(), e);
            }
        }
    }

}
