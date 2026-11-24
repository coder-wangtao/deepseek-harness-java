package cn.wangtao.deepseek.harness.cases.agent;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;
import cn.wangtao.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import cn.wangtao.deepseek.harness.domain.llm.service.LlmGenerationService;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 交互用例 —— 最小对话版（xfg-2-2）。
 * <p>
 * 本节只实现"问一句答一句"的最小链路：
 * HTTP 请求 → 本用例 → LlmGenerationService（LLM 端口）→ 组装响应。
 * 策略树编排（AgentMessageFactory）、ReAct 循环、会话持久化
 * 在 part-03 及之后章节逐步替换本实现，对外契约（IAgentUseCase）保持不变。
 *
 * @author 小傅哥
 * @website bugstack.cn
 */
@Service
public class AgentUseCase implements IAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentUseCase.class);

    /**
     * 渠道编码 → 模型提供方/模型名 的最小映射（模型设置在 2-5 变成可配置）。
     */
    private static final Map<String, String[]> CHANNELS = Map.of(
            "deepseek", new String[]{"deepseek", "glm-5.3-flash"}
    );

    private final LlmGenerationService llmGenerationService;
    private final ILlmRuntimePort llmRuntimePort;

    public AgentUseCase(LlmGenerationService llmGenerationService, ILlmRuntimePort llmRuntimePort) {
        this.llmGenerationService = llmGenerationService;
        this.llmRuntimePort = llmRuntimePort;
    }

    @Override
    public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) {
        log.info("[UseCase] Agent={} 收到消息发送请求（channel={} streaming=false）",
                request.agentId(), request.channelCode());

        // ① 渠道解析：channelCode → [provider, model]，未知渠道在此抛出参数异常
        String[] channel = resolveChannel(request.channelCode());

        // ② 组装生成选项：把用户消息包装为领域模型 Message.createUser(TextBlock)，
        //    其余能力（工具/系统提示词/历史消息）留给后续章节填充，本节传 null
        GenerateOptions options = new GenerateOptions(
                channel[0],
                channel[1],
                request.reasoningEffort(),
                List.of(Message.createUser(List.of(new TextBlock(request.message())),
                        new MessageSource.UserSource())),
                null, null, null, request.maxTokens(), null, request.agentId(), null, null, null, null
        );

        // ③ 委托领域服务生成回复：generate() 返回 CompletableFuture，join() 阻塞等待
        //    2-1 的"流式订阅 → chunk 组装"链路在本行内完成，用例层无感
        Message answer = llmGenerationService.generate(options).join();

        // ④ 组装响应：领域 Message → API 层 messages 结构 [{role, text}]；
        //    sessionId 本节用 agentId 占位，会话持久化在后续章节引入
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("text", extractText(answer));
        messages.add(assistantMessage);

        return new AgentMessageResponseDTO(
                request.agentId(),
                request.agentId() + "-session",
                "idle",
                messages,
                null,
                null
        );

    }

    /**
     * 流式对话用例：AI 边生成、前端边显示（"打字机"效果）的核心实现。
     * <p>
     * 模型服务器吐出 token → SDK 逐块回调 → Flow 响应式流
     * （Publisher/Subscriber，JDK9+ 内置的"生产者-消费者"异步通知机制）把每块
     * StreamChunk 送进本方法 → 按类型分发给 4 个 sink 回调 → trigger 层把 sink
     * 收到的内容包成 SSE 事件推给浏览器。整条链路是"推"的模式，数据生产出来
     * 立刻被逐层转发，前端就能看到文字一段段长出来。
     * <p>
     * 【关键设计】
     * <ul>
     *   <li>双轨输出：流式过程走 sink（增量推给前端），同时用 StringBuilder
     *       累计完整回答，流结束后方法照常返回结构化响应——调用方既能拿到
     *       过程也能拿到结果，一个方法同时服务流式与阻塞两种需求。</li>
     *   <li>线程协调：Publisher 在独立线程推送，本方法所在线程用 CountDownLatch
     *       + await(300s) 等流结束（onComplete/onError 时 countDown），避免
     *       方法提前返回拿到不完整的 fullText，也防止异常时永久挂死。</li>
     *   <li>背压（消费者反向给生产者施压、控制生产节奏的机制，名字借的是水管逆流的物理意象）：onSubscribe 里 request(Long.MAX_VALUE) 表示不限速——SSE 消费
     *       速度远快于模型生成速度，无需流控；若换成高频消费场景需改用真实
     *       需求量。</li>
     *   <li>sealed chunk 分型：TextDelta（正文增量）/ ReasoningDelta（思考过程，
     *       推理模型才有）/ Finish（结束信号），instanceof 模式匹配逐类分发，
     *       新增事件类型时编译器会强制补全分支。</li>
     * </ul>
     *
     * @param deltaSink     正文增量回调，trigger 层转成 SSE delta 事件
     * @param reasoningSink 思考过程增量回调，转成 SSE reasoning 事件
     * @param toolCallSink  工具调用事件回调（Agent 调用工具时触发）
     * @param finishSink    流结束信号，trigger 层借此关闭 SSE 连接
     */
    @Override
    public AgentMessageResponseDTO sendMessageStreaming(
            AgentMessageRequestDTO request,
            Consumer<String> deltaSink,
            Consumer<String> reasoningSink,
            Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink) {

        log.info("[UseCase] Agent={} 收到流式消息发送请求（channel={}）",
                request.agentId(), request.channelCode());
        String[] channel = resolveChannel(request.channelCode());

        GenerateOptions options = new GenerateOptions(
                channel[0], channel[1], request.reasoningEffort(),
                List.of(Message.createUser(List.of(new TextBlock(request.message())), new cn.wangtao.deepseek.harness.domain.model.entity.MessageSource.UserSource())),
                null, null, null, request.maxTokens(), null, request.agentId(), null, null, null, null
        );

        // 流式：LLM 端口的 StreamChunk 增量直接透传给 sink，由 trigger 层推给 SSE
        StringBuilder fullText = new StringBuilder();

        var done = new CountDownLatch(1); // 完成信号：onComplete/onError 时 countDown，主线程 await 等流结束再组装最终响应

        // JDK9+ 响应式流（java.util.concurrent.Flow）：Publisher 发布 StreamChunk 增量，
        // Subscriber 消费——这是「模型逐 token 回调」到「sink 逐段推送」的桥接层
        Flow.Publisher<StreamChunk> stream = llmRuntimePort.stream(options);

        stream.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                // 背压控制：request(Long.MAX_VALUE) 表示不限速，有多少增量收多少（SSE 场景无需限流）
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk chunk) {
                // 每到一个增量块，按类型分发给对应 sink，由 trigger 层转成 SSE 事件推给前端
                if (chunk instanceof StreamChunk.TextDelta td) {
                    // 正文增量：累计到 fullText（用于流结束后组装完整回答），同时推给 deltaSink
                    fullText.append(td.text());
                    deltaSink.accept(td.text());
                } else if (chunk instanceof StreamChunk.ReasoningDelta rd) {
                    // 思考过程增量（推理模型才有）：只透传，不累计
                    reasoningSink.accept(rd.text());
                } else if (chunk instanceof StreamChunk.Finish) {
                    // 流结束信号：通知 trigger 层可以收尾（关闭 SSE）
                    finishSink.run();
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("[UseCase] 流式请求失败 Agent={}", request.agentId(), t);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        // 阻塞等待流结束（Publisher 在独立线程上推送），最多 300s 防止挂死；
        // 等到后 fullText 已含完整回答，方法才能返回结构化响应给阻塞式调用方
        try {
            done.await(300, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("text", fullText.toString());

        messages.add(assistantMessage);

        return new AgentMessageResponseDTO(
                request.agentId(),
                request.agentId() + "-session",
                "idle",
                messages,
                null,
                null
        );
    }

    @Override
    public String getAgentStatus(String agentId) {
        return "idle";
    }

    @Override
    public void cancelAgent(String agentId) {
        // 最小版无可运行 Agent，忽略
    }

    /**
     * 渠道解析：channelCode → [provider, model]。
     * <p>
     * 空白值默认走 deepseek；未知编码抛 {@link IllegalArgumentException}，
     * 由触发层包装为 Response.invalidArgument（4xx 语义）。
     */
    private String[] resolveChannel(String channelCode) {
        String code = channelCode == null || channelCode.isBlank() ? "deepseek" : channelCode;
        String[] channel = CHANNELS.get(code);
        if (channel == null) {
            throw new IllegalArgumentException("unknown channelCode: " + code);
        }
        return channel;
    }

    /**
     * 从领域 Message 的内容块中提取纯文本（只拼接 TextBlock，忽略推理/工具块）。
     */
    private String extractText(Message message) {
        StringBuilder text = new StringBuilder();
        for (var block : message.content()) {
            if (block instanceof TextBlock tb) {
                text.append(tb.text());
            }
        }
        return text.toString();
    }

}
