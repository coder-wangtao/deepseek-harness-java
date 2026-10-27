package cn.wangtao.deepseek.harness.domain.model.llm;

import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ReasoningBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolCallBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolResultBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolSchema;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 LLM 对话客户端：领域对象 ↔ OpenAI 兼容报文 的双向转换器（仅测试代码使用）。
 *
 * <p>本类是单测中唯一的"基础设施"，职责就两件事，方向相反：
 * <ol>
 *   <li><b>请求方向</b>：领域 {@link Message} 列表（含工具定义 {@link ToolDefinition}）
 *       序列化为 {@code /v1/chat/completions} 请求报文；</li>
 *   <li><b>响应方向</b>：响应报文反序列化为领域对象——
 *       {@code content} → {@link TextBlock}、{@code reasoning_content} → {@link ReasoningBlock}、
 *       {@code tool_calls} → {@link ToolCallBlock}（arguments 保持原始 JSON 字符串，谁消费谁解析），
 *       最终包装为 {@link Message#createAssistant} + {@link MessageSource.ModelMessageSource}。</li>
 * </ol>
 *
 * <p>纯 JDK 实现（HttpClient + MiniJson），不加任何依赖，与 types 模块零依赖纪律一致。</p>
 *
 */
public final class LlmChatClient {

    /** 本地 LLM 网关地址（OpenAI 兼容协议）。 */
    private static final String BASE_URL = "https://api.moonshot.cn/v1";
    /** 网关鉴权 Token。 */
    private static final String API_KEY = "sk-djOGgK0CmAil04SF14mwfLOPT9KfyTxY0Ykz1TWjfEtTFRIE";
    /** 模型名。 */
    private static final String MODEL = "kimi-k2.6";
    /** 来源标识：模型回复统一打上这个 provider/model 溯源标签。 */
    private static final String PROVIDER = "kimi";

    /** 请求超时：本地网关首次调用可能有冷启动，给足余量。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(100))
            .build();

    /**
     * 一次对话的结果：模型回复（领域 Message，含内容块）+ 结束原因。
     * <p>finishReason 为 {@code stop} 表示正常收尾；为 {@code tool_calls} 表示模型请求执行工具。</p>
     */
    public record ChatResult(Message assistantMessage, String finishReason) {}

    /** 纯文本对话（不携带工具）。 */
    public ChatResult chat(List<Message> messages) {
        return chat(messages, List.of());
    }

    /** 携带工具定义的对话：模型可能直接回复，也可能返回 tool_calls 请求执行工具。 */
    public ChatResult chat(List<Message> messages, List<ToolDefinition> tools) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("messages", toWireMessages(messages));
            if (!tools.isEmpty()) {
                body.put("tools", toWireTools(tools));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/chat/completions"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(MiniJson.write(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("LLM API HTTP " + response.statusCode() + ": " + response.body());
            }
            return toDomain(response.body());
        } catch (Exception e) {
            throw new RuntimeException("LLM API 调用失败: " + BASE_URL + "/chat/completions", e);
        }
    }

    // ── 请求方向：领域 Message → wire 报文 ──────────────────────────────

    /** 领域 Message 列表 → wire messages 数组。 */
    private static List<Object> toWireMessages(List<Message> messages) {
        List<Object> wire = new ArrayList<>();
        for (Message m : messages) {
            wire.add(toWireMessage(m));
        }
        return wire;
    }

    /**
     * 单条领域 Message → wire message 对象。
     * <ul>
     *   <li>role=user/assistant/system：content 取所有 TextBlock 拼接；
     *       助手消息中的 {@link ToolCallBlock} 额外还原为 OpenAI {@code tool_calls} 数组；</li>
     *   <li>role=tool：由 {@link ToolResultBlock} 还原为 {@code tool_call_id + content}。</li>
     * </ul>
     */
    private static Map<String, Object> toWireMessage(Message m) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("role", m.role());
        if ("tool".equals(m.role())) {
            // 领域侧工具结果消息的唯一内容块必为 ToolResultBlock（见 Message.createToolResult）
            ToolResultBlock block = (ToolResultBlock) m.content().get(0);
            wire.put("tool_call_id", block.toolCallId());
            wire.put("content", joinText(block.content()));
        } else {
            wire.put("content", joinText(m.content()));
            List<Object> toolCalls = new ArrayList<>();
            for (ContentBlock b : m.content()) {
                if (b instanceof ToolCallBlock call) {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", call.name());
                    function.put("arguments", call.arguments()); // 原样透传，不解析不重排
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("id", call.id());
                    tc.put("type", "function");
                    tc.put("function", function);
                    toolCalls.add(tc);
                }
            }
            if (!toolCalls.isEmpty()) {
                wire.put("tool_calls", toolCalls);
            }
        }
        return wire;
    }

    /** 工具定义列表 → wire tools 数组。经由 {@link ToolSchema} 走一遭，顺带验证其用途。 */
    private static List<Object> toWireTools(List<ToolDefinition> tools) {
        List<Object> wire = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            ToolSchema schema = new ToolSchema(tool.name(), tool.description(), tool.parameters());
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", schema.name());
            function.put("description", schema.description());
            function.put("parameters", schema.parameters());
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "function");
            w.put("function", function);
            wire.add(w);
        }
        return wire;
    }

    /** 拼接文本块。本轻量实现只透传文本；ImageBlock 等多模态块不进入 wire（见单测说明）。 */
    private static String joinText(List<ContentBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : blocks) {
            if (b instanceof TextBlock text) {
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    // ── 响应方向：wire 报文 → 领域对象 ─────────────────────────────────

    /** 响应报文 → {@link ChatResult}：content/reasoning_content/tool_calls 各归各的内容块。 */
    @SuppressWarnings("unchecked")
    private static ChatResult toDomain(String responseBody) {
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(responseBody);
        Map<String, Object> choice = (Map<String, Object>) ((List<Object>) root.get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        List<ContentBlock> blocks = new ArrayList<>();
        // 1. 思维链 → ReasoningBlock（与可见文本分离，前端可独立折叠展示）
        String reasoning = str(message.get("reasoning_content"));
        if (reasoning != null && !reasoning.isBlank()) {
            blocks.add(new ReasoningBlock(reasoning));
        }
        // 2. 可见文本 → TextBlock
        String content = str(message.get("content"));
        if (content != null && !content.isBlank()) {
            blocks.add(new TextBlock(content));
        }
        // 3. 工具调用 → ToolCallBlock（arguments 保持模型输出的原始 JSON 字符串）
        Object rawToolCalls = message.get("tool_calls");
        if (rawToolCalls instanceof List<?> toolCalls) {
            for (Object tc : toolCalls) {
                Map<String, Object> call = (Map<String, Object>) tc;
                Map<String, Object> function = (Map<String, Object>) call.get("function");
                blocks.add(new ToolCallBlock(
                        str(call.get("id")),
                        str(function.get("name")),
                        str(function.get("arguments"))));
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(new TextBlock(""));
        }

        // 4. 包装为领域助手消息：来源打上 provider/model 溯源标签
        Message assistant = Message.createAssistant(
                blocks, new MessageSource.ModelMessageSource(PROVIDER, MODEL));
        return new ChatResult(assistant, str(choice.get("finish_reason")));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
