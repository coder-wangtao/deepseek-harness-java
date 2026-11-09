package cn.wangtao.deepseek.harness.cases.agent;

import cn.wangtao.deepseek.harness.api.dto.AgentMessageRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.AgentToolCallEventResponseDTO;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.wangtao.deepseek.harness.domain.llm.service.LlmGenerationService;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class AgentUseCase implements IAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentUseCase.class);

    /** 渠道编码 → 模型提供方/模型名 的最小映射（模型设置在 2-5 变成可配置）。 */
    private static final Map<String, String[]> CHANNELS = Map.of(
            "deepseek", new String[]{"deepseek", "deepseek-v4-flash"}
    );

    private final LlmGenerationService llmGenerationService;

    public AgentUseCase(LlmGenerationService llmGenerationService) {
        this.llmGenerationService = llmGenerationService;
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

    @Override
    public AgentMessageResponseDTO sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink) {
        // 流式在分支引入；本节先按阻塞方式兜底，保证契约可用：
        // 阻塞拿完整回答 → deltaSink 一次性推全文 → finishSink 收尾，
        // 下游拿到的是"一次到齐的流"，与真流式语义兼容
        AgentMessageResponseDTO response = sendMessage(request);
        deltaSink.accept(extractSingleText(response));
        finishSink.run();
        return response;
    }

    @Override
    public String getAgentStatus(String agentId) {
        // 最小版没有 Agent 生命周期管理，恒返 idle；running/not-found 随会话模块引入
        return "idle";
    }

    @Override
    public void cancelAgent(String agentId) {
        // 最小版无可运行 Agent，忽略；ReAct 循环引入后实现真正的中断
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

    /** 从领域 Message 的内容块中提取纯文本（只拼接 TextBlock，忽略推理/工具块）。 */
    private String extractText(Message message) {
        StringBuilder text = new StringBuilder();
        for (var block : message.content()) {
            if (block instanceof TextBlock tb) {
                text.append(tb.text());
            }
        }
        return text.toString();
    }

    /** 取流式兜底响应用的首条文本；无消息时返回空串。 */
    private String extractSingleText(AgentMessageResponseDTO response) {
        if (response.messages() == null || response.messages().isEmpty()) {
            return "";
        }
        Object text = response.messages().get(0).get("text");
        return text == null ? "" : text.toString();
    }
}
