package cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek;

import cn.wangtao.deepseek.harness.domain.channel.service.OpenAiCompatibleUriResolver;
import cn.wangtao.deepseek.harness.domain.llm.adapter.LlmAdapter;
import cn.wangtao.deepseek.harness.domain.llm.adapter.ResolvedRetryPolicy;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.*;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ReasoningBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolCallBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolResultBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.Flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeepSeek（OpenAI 兼容协议）大模型适配器 —— 最小可用版。
 *
 * <h2>设计目的</h2>
 * <p>
 * 本类是 {@code LlmAdapter} SPI 的第一个真实实现，演示"适配器"的全部职责：
 * 把领域层的 {@link GenerateOptions} 翻译成 OpenAI 兼容 HTTP 请求，
 * 把上游 SSE 响应翻译回领域事件 {@link StreamChunk}。因为 WaLiAPI 网关、
 * One-API、绝大多数国产大模型都兼容 OpenAI 协议，这个适配器实际上
 * 是"通用 OpenAI 兼容适配器"——上一个任务里它直连 WaLiAPI + GLM 就是证明。
 *
 * <h2>执行流程</h2>
 * <ol>
 *   1. 序列化领域消息为 OpenAI chat/completions 请求体（{@link #serializeRequest}）；
 *   2. 以 SSE 流式请求上游，逐行解析并归一化为领域事件（{@link #translateSse}）；
 *   3. 上游空流时回退一次非流式请求（{@link #doNonStreamFallback}）；
 *   4. 统一以 Finish 事件收尾。
 * </ol>
 *
 * <h2>一次 stream() 调用的运行距离</h2>
 * <pre>
 *   stream(genOptions)                    ← new 线程异步执行，立即返回 Publisher
 *     └─ doStream
 *          ├─ 解析生效的 apiKey/baseUrl（请求级覆盖优先于全局配置）
 *          ├─ serializeRequest            ← 领域消息 → JSON 请求体
 *          ├─ resolveChatCompletionsUri   ← 拼端点（channel 领域服务）
 *          ├─ httpClient.send             ← POST，SSE 响应以 InputStream 流式读取
 *          ├─ translateSse                ← 逐行解析 data: 分片 → BlockStart/TextDelta/
 *          │                                ReasoningDelta/ToolCallDelta/BlockEnd/
 *          │                                Usage/Finish 逐个 publish
 *          └─ [DONE] 到达 → 关闭全部块 → 发 Finish → 流关闭
 * </pre>
 *
 * <p>本节（xfg-2-1）只保留最小链路：模型目录来自配置（不做上游发现）、
 * 不做多模态图片序列化、不做协议路由（2-4 引入）。这些能力在后续章节逐步长出。</p>
 *
 * @author wt
 */
public class DeepSeekAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAdapter.class);

    public static final int DEFAULT_STREAM_IDLE_TIMEOUT_MS = 300_000;
    public static final int DEFAULT_CONTEXT_WINDOW = 1_000_000;
    public static final int DEFAULT_MAX_TOKENS = 8_192;

    /**
     * 单次 LLM HTTP 请求总超时（秒），可用系统属性 harness.llm.request-timeout-seconds 覆盖。
     */
    private static final long REQUEST_TIMEOUT_SECONDS = Long.parseLong(
            System.getProperty("harness.llm.request-timeout-seconds",
                    System.getenv().getOrDefault("HARNESS_LLM_REQUEST_TIMEOUT_SECONDS", "300")));

    /**
     * 渠道配置（地址/Key/模型目录/重试策略），构造时注入后不可变。
     */
    private final DeepSeekAdapterOptions options;
    /**
     * JSON 序列化器（请求体构建与 SSE 分片解析共用）。
     */
    private final ObjectMapper objectMapper;
    /**
     * JDK 内置 HTTP 客户端，连接超时 15 秒，线程安全可复用。
     */
    private final HttpClient httpClient;

    /**
     * 构造适配器。
     *
     * @param options      渠道配置
     * @param objectMapper JSON 序列化器
     */
    public DeepSeekAdapter(DeepSeekAdapterOptions options, ObjectMapper objectMapper) {
        this.options = options;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 返回本适配器服务的 provider 编码（SPI 注册 key）。
     *
     * <p>端口用这个 key 建立路由表：渠道配置里的 {@code provider="deepseek"}
     * → 本实现。纯本地契约，无 HTTP 对应。</p>
     *
     * <pre>
     * 入参：无
     * 出参："deepseek"
     * 数据案例：DeepSeekAdapterOptions(provider="deepseek", baseUrl="http://127.0.0.1:8777", ...)
     *           注册后 stream/listModels 等调用都会路由到本类。
     * </pre>
     */
    @Override
    public String providerId() {
        return "deepseek";
    }

    /**
     * 返回 provider 展示信息（控制台模型列表/渠道页用）。
     *
     * <p>纯本地契约，无 HTTP 对应；name 是给人看的显示名，不参与路由。</p>
     *
     * <pre>
     * 入参：provider = "deepseek"
     * 出参：LlmProviderInfo[id="deepseek", name="DeepSeek"]
     * 数据案例：控制台渠道下拉框显示 "DeepSeek"，底层仍用 "deepseek" 匹配。
     * </pre>
     */
    @Override
    public LlmProviderInfo providerInfo(String provider) {
        return new LlmProviderInfo(provider, "DeepSeek");
    }

    /**
     * 返回渠道配置声明的重试策略（可空），端口据此决定失败后是否自动重建流。
     *
     * <p>纯本地契约，无 HTTP 对应；数据来自 {@link DeepSeekAdapterOptions}。
     * 触发场景：请求返回 429/5xx 等可重试错误时，端口读取本策略重试。</p>
     *
     * <pre>
     * 入参：provider = "deepseek"
     * 出参：ResolvedRetryPolicy（如 maxAttempts=3, backoffMs=500）或 null（不重试）
     * 数据案例：上游返回 HTTP 429 {"error":{"message":"rate limit reached"}}
     *           → 端口按策略等 500ms 重建流，下游收到 Retry(1, 3, "rate limit reached")。
     * </pre>
     */
    @Override
    public ResolvedRetryPolicy retryPolicy(String provider) {
        return options.retryPolicy();
    }

    /**
     * 列出模型目录（xfg-2-1 最小版：来自本地配置，不发 HTTP 请求）。
     *
     * <p>上游的对应端点是 GET /v1/models，后续章节引入上游发现时切换。</p>
     *
     * <pre>
     * 入参：provider = "deepseek"
     * 出参：配置 models() 逐条映射，如配置了 glm-5.3-flash / deepseek-chat：
     *       [LlmModelInfo[provider="deepseek", id="glm-5.3-flash", name="glm-5.3-flash",
     *        description=..., inputModalities=["text"]], ...]
     *       name 为空时回退用 id；模态最小版固定 ["text"]（不支持图片输入）。
     * </pre>
     */
    @Override
    public List<LlmModelInfo> listModels(String provider) {
        return options.models().stream()
                .map(m -> new LlmModelInfo(
                        provider,
                        m.id(),
                        m.name() != null ? m.name() : m.id(),
                        m.description(),
                        List.of("text")
                ))
                .toList();
    }

    /**
     * 解析单个模型的精确元数据（上下文窗口/输出上限），供路由层做
     * 上下文长度预检与参数默认值填充。
     *
     * <p>最小版同样读本地配置：模型在目录里用配置值，不在则回退
     * 渠道级默认值（defaultContextWindow / maxTokens）。</p>
     *
     * <pre>
     * 入参：provider = "deepseek", model = "glm-5.3-flash"
     * 出参：LlmResolvedModelInfo[
     *        id="glm-5.3-flash", name=配置名或 id, description=配置描述或 null,
     *        inputModalities=["text"],
     *        context=LlmModelContext[contextWindow=131072],   ← 模型配置优先，否则渠道默认
     *        defaultMaxTokens=8192, reasoning=null]           ← 推理元数据暂不支持
     * 数据案例：配置 models=[{id:"glm-5.3-flash", contextWindow:131072}]、
     *           defaultContextWindow=1000000、maxTokens=8192 时如上；
     *           请求 model="unknown" 时 contextWindow 回退 1000000。
     * </pre>
     */
    @Override
    public LlmResolvedModelInfo resolveModel(String provider, String model) {
        var configured = options.models().stream()
                .filter(m -> m.id().equals(model))
                .findFirst();
        int contextWindow = configured.flatMap(DirectSeekCatalogModel::contextWindowOpt)
                .orElse(Integer.valueOf(options.defaultContextWindow()));
        Integer maxTokens = configured.flatMap(DirectSeekCatalogModel::maxTokensOpt)
                .orElse(Integer.valueOf(options.maxTokens()));
        return new LlmResolvedModelInfo(
                provider,
                model,
                configured.map(DirectSeekCatalogModel::name).orElse(model),
                configured.flatMap(DirectSeekCatalogModel::descriptionOpt).orElse(null),
                List.of("text"),
                new LlmModelContext(contextWindow),
                maxTokens,
                null // reasoning info not supported yet
        );
    }

    /**
     * 流式生成入口：起独立线程执行真实请求，立即返回可订阅的事件流。
     *
     * <p>设计说明：HTTP + SSE 读取是阻塞 IO，放在独立线程保证调用方
     * 拿到 Publisher 后不被卡住；任何异常（含 HTTP 错误、解析失败）
     * 都以 Finish(Error) 事件发布，保证订阅者始终能收到终态。</p>
     *
     * @param genOptions 生成参数
     * @return 模型输出事件流
     */
    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions genOptions) {
        SubmissionPublisher<StreamChunk> publisher = new SubmissionPublisher<>(); //创建了一个发布者
        new Thread(() -> {
            try {
                doStream(genOptions, publisher);
            } catch (Exception e) {
                publishError(publisher, e);
            } finally {
                publisher.close();
            }
        }).start();
        return publisher;
    }

    /**
     * 执行真实流式请求（在独立线程中运行）。
     *
     * <p>运行距离：解析生效配置 → 序列化请求体 → 发送 SSE POST →
     * 逐行翻译事件 → 空流时降级为非流式请求。</p>
     *
     * @param genOptions 生成参数
     * @param publisher  事件发布器
     */
    //向一个兼容 OpenAI API 的大模型服务发送 HTTP 流式请求（Streaming），
    // 接收服务器返回的 SSE（Server-Sent Events）数据，并将数据转换成程序内部的 StreamChunk，通过 SubmissionPublisher 发布出去。
    private void doStream(GenerateOptions genOptions, SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey() != null && !genOptions.apiKey().isBlank()
                ? genOptions.apiKey() : options.resolveApiKey();
        String baseUrl = genOptions.baseUrl() != null && !genOptions.baseUrl().isBlank()
                ? genOptions.baseUrl() : options.baseUrl();
        String effectiveModel = resolveEffectiveModel(genOptions.model());
        ObjectNode body = serializeRequest(withModel(genOptions, effectiveModel));
        String payload = objectMapper.writeValueAsString(body);
        if (log.isDebugEnabled()) {
            JsonNode tools = body.path("tools");
            log.debug(String.valueOf(tools.size()));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveChatCompletionsUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        if (genOptions.sessionId() != null) {
            builder.header("x-deepseek-harness-session-id", genOptions.sessionId());
        }

        HttpResponse<java.io.InputStream> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new LlmAdapterException(
                    extractErrorMessage(errorBody),
                    httpErrorCode(response.statusCode(), errorBody),
                    response.statusCode()
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            boolean streamed = translateSse(reader, publisher);
            if (!streamed) {
                log.warn("Upstream returned empty stream for model={}, falling back to non-stream request", effectiveModel);
                doNonStreamFallback(genOptions, effectiveModel, publisher);
            }
        }
    }

    /**
     * 非流式兜底请求：上游接受 stream=true 却不吐数据时，
     * 改发 stream=false 的普通请求，把一次性完整响应拆成流式事件发布，
     * 保证下游消费逻辑统一（无需感知"这是兜底来的"）。
     *
     * @param genOptions     生成参数
     * @param effectiveModel 生效模型名
     * @param publisher      事件发布器
     */
    private void doNonStreamFallback(GenerateOptions genOptions, String effectiveModel, SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey() != null && !genOptions.apiKey().isBlank()
                ? genOptions.apiKey() : options.resolveApiKey();
        String baseUrl = genOptions.baseUrl() != null && !genOptions.baseUrl().isBlank()
                ? genOptions.baseUrl() : options.baseUrl();
        ObjectNode body = serializeRequest(withModel(genOptions, effectiveModel));
        body.put("stream", false);

        String payload = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveChatCompletionsUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmAdapterException(
                    extractErrorMessage(response.body()),
                    httpErrorCode(response.statusCode(), response.body()),
                    response.statusCode()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmAdapterException("non-stream response has no choices", LlmErrorCodes.EMPTY_RESPONSE);
        }

        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText(null);
        String reasoning = message.path("reasoning_content").asText(null);

        int nextIdx = 0;
        List<OpenBlock> blocks = new ArrayList<>();
        if (content != null && !content.isEmpty()) {
            publisher.submit(new StreamChunk.BlockStart(nextIdx, "text"));
            publisher.submit(new StreamChunk.TextDelta(nextIdx, content));
            OpenBlock tb = new OpenBlock(nextIdx, "text");
            tb.text.append(content);
            blocks.add(tb);
            nextIdx++;
        }
        if (reasoning != null && !reasoning.isEmpty()) {
            publisher.submit(new StreamChunk.BlockStart(nextIdx, "reasoning"));
            publisher.submit(new StreamChunk.ReasoningDelta(nextIdx, reasoning));
            blocks.add(new OpenBlock(nextIdx, "reasoning"));
            nextIdx++;
        }

        if (blocks.isEmpty()) {
            throw new LlmAdapterException("non-stream response has no content", LlmErrorCodes.EMPTY_RESPONSE);
        }

        for (OpenBlock block : blocks) {
            publisher.submit(new StreamChunk.BlockEnd(block.index, block.toContentBlock()));
        }

        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode()) {
            publisher.submit(new StreamChunk.Usage(mapUsage(usageNode)));
        }

        String finishReason = choices.get(0).path("finish_reason").asText("stop");
        publisher.submit(new StreamChunk.Finish(mapFinishReason(finishReason)));
    }

    /**
     * 把领域生成参数序列化为 OpenAI chat/completions 请求体。
     *
     * <p>运行距离：model/stream 基础字段 → system 提示词（插在最前）→
     * 遍历领域消息（文本拼接、工具调用块 → tool_calls 数组、
     * 工具结果块 → role=tool 消息）→ 工具 schema 定义 →
     * temperature/reasoning_effort/max_tokens/stop 可选参数 →
     * 返回完整 JSON 树。</p>
     *
     * <p>设计要点：领域 Message 是"内容块列表"，OpenAI 协议是"扁平消息 +
     * 角色"，这里的角色分发（tool/assistant+tool_calls/普通）就是两套
     * 结构之间的翻译规则，是适配器最核心的职责。</p>
     *
     * <p><b>实际传输出入参对照</b>（以 WaLiAPI 网关 + GLM 为例）：</p>
     *
     * <p><b>请求（本方法产出的 payload，即下面 curl 的 -d 请求体；
     * URL 与 Authorization 头来自 doStream 拼装）：</b></p>
     * <pre>
     * curl http://127.0.0.1:8777/v1/chat/completions \
     *   -H "Content-Type: application/json" \
     *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
     *   -d '{
     *     "model": "glm-5.3-flash",
     *     "stream": true,
     *     "messages": [
     *       {"role": "system", "content": "你是智能助手"},
     *       {"role": "user", "content": "Hello!"}
     *     ],
     *     "tools": [{"type": "function", "function": {"name": "get_weather", "description": "查天气", "parameters": {}}}],
     *     "temperature": 0.7,
     *     "max_tokens": 8192
     *   }'
     * </pre>
     * <p>字段来源映射：{@code model} ← options.model()（空时取配置目录第一个）；
     * {@code stream} 固定 true；{@code messages} ← system + options.messages()
     * （工具结果块转 role=tool + tool_call_id、工具调用块转 assistant.tool_calls）；
     * {@code tools} ← options.tools()；temperature/max_tokens/stop/ reasoning_effort
     * ← 同名可选参数，null 时不输出该字段。</p>
     *
     * <p><b>响应（流式，HTTP 200，Content-Type: text/event-stream，
     * 逐行喂给 {@link #translateSse} 解析）：</b></p>
     * <pre>
     * data: {"choices":[{"delta":{"role":"assistant","content":"Hel"},"index":0}]}
     * data: {"choices":[{"delta":{"content":"lo!"},"index":0}]}
     * data: {"choices":[{"delta":{},"finish_reason":"stop"}],
     *        "usage":{"prompt_tokens":9,"completion_tokens":4,"total_tokens":13}}
     * data: [DONE]
     * </pre>
     *
     * @param options 生成参数
     * @return 请求体 JSON 树
     */
    private ObjectNode serializeRequest(GenerateOptions options) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", options.model());
        root.put("stream", true);

        ArrayNode messages = objectMapper.createArrayNode();
        if (options.system() != null && !options.system().isBlank()) {
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", options.system());
            messages.add(sysMsg);
        }
        for (Message msg : options.messages()) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());

            StringBuilder textContent = new StringBuilder();
            List<ToolCallBlock> msgToolCalls = new ArrayList<>();
            List<ToolResultBlock> msgToolResults = new ArrayList<>();

            for (ContentBlock block : msg.content()) {
                if (block instanceof TextBlock tb) {
                    textContent.append(tb.text());
                } else if (block instanceof ToolCallBlock tcb) {
                    msgToolCalls.add(tcb);
                } else if (block instanceof ToolResultBlock trb) {
                    msgToolResults.add(trb);
                }
            }

            // 工具结果消息：role=tool + tool_call_id
            if (!msgToolResults.isEmpty() && "tool".equals(msg.role())) {
                for (ToolResultBlock trb : msgToolResults) {
                    ObjectNode trNode = objectMapper.createObjectNode();
                    trNode.put("role", "tool");
                    trNode.put("tool_call_id", trb.toolCallId());
                    StringBuilder trContent = new StringBuilder();
                    for (ContentBlock inner : trb.content()) {
                        if (inner instanceof TextBlock tb) trContent.append(tb.text());
                    }
                    trNode.put("content", trContent.toString());
                    messages.add(trNode);
                }
                continue;
            }

            // 助手消息带工具调用
            if (!msgToolCalls.isEmpty()) {
                if (textContent.length() > 0) {
                    msgNode.put("content", textContent.toString());
                } else {
                    msgNode.putNull("content");
                }
                ArrayNode callsNode = objectMapper.createArrayNode();
                for (ToolCallBlock tcb : msgToolCalls) {
                    ObjectNode callNode = objectMapper.createObjectNode();
                    callNode.put("id", tcb.id());
                    callNode.put("type", "function");
                    ObjectNode fnNode = objectMapper.createObjectNode();
                    fnNode.put("name", tcb.name());
                    fnNode.put("arguments", tcb.arguments() != null ? tcb.arguments() : "{}");
                    callNode.set("function", fnNode);
                    callsNode.add(callNode);
                }
                msgNode.set("tool_calls", callsNode);
            } else {
                // 普通文本消息
                msgNode.put("content", textContent.toString());
            }

            messages.add(msgNode);
        }
        root.set("messages", messages);

        // 工具定义
        if (options.tools() != null && !options.tools().isEmpty()) {
            ArrayNode tools = objectMapper.createArrayNode();
            for (var tool : options.tools()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", "function");
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("name", tool.name());
                fn.put("description", tool.description());
                fn.set("parameters", objectMapper.valueToTree(tool.parameters()));
                toolNode.set("function", fn);
                tools.add(toolNode);
            }
            root.set("tools", tools);
        }

        if (options.temperature() != null) root.put("temperature", options.temperature());
        if (options.reasoningEffort() != null && !options.reasoningEffort().isBlank()) {
            root.put("reasoning_effort", options.reasoningEffort());
        }
        if (options.maxTokens() != null) root.put("max_tokens", options.maxTokens());
        if (options.stop() != null && !options.stop().isEmpty()) {
            root.set("stop", objectMapper.valueToTree(options.stop()));
        }

        return root;
    }

    /**
     * 复制生成参数并替换生效模型名（record 的"改名"辅助方法）。
     *
     * <p>GenerateOptions 是 record 不可变，模型解析（{@link #resolveEffectiveModel}）
     * 补全默认模型后需要换出一个新实例再交给 {@link #serializeRequest}，
     * 其余 13 个字段原样透传。</p>
     *
     * <pre>
     * 入参：options = GenerateOptions[model=null, messages=[user:"Hello!"], ...]
     *       model   = "glm-5.3-flash"（解析出的生效模型）
     * 出参：GenerateOptions[model="glm-5.3-flash", 其余字段与入参完全相同]
     * </pre>
     */
    private GenerateOptions withModel(GenerateOptions options, String model) {
        return new GenerateOptions(
                options.provider(),
                model,
                options.reasoningEffort(),
                options.messages(),
                options.system(),
                options.tools(),
                options.temperature(),
                options.maxTokens(),
                options.stop(),
                options.sessionId(),
                options.purpose(),
                options.baseUrl(),
                options.apiKey(),
                options.protocol()
        );
    }

    /**
     * 最小版模型解析：请求为空时取配置目录第一个模型，非空原样透传。
     *
     * <p>目录为空时只能返回原值（交给上游报"模型不存在"）。
     * 注意这里不校验请求的模型是否在目录里——目录只是展示/兜底用途，
     * 用户指定什么就发什么，交给上游判断。</p>
     *
     * <pre>
     * 数据案例：
     *   requestedModel="glm-5.3-flash"，目录=["glm-5.3-flash","deepseek-chat"]
     *     → "glm-5.3-flash"（用户指定，直接透传）
     *   requestedModel=null，目录=["glm-5.3-flash","deepseek-chat"]
     *     → "glm-5.3-flash"（取第一个兜底）
     *   requestedModel=null，目录=[]
     *     → null（原样返回，由上游报错）
     * </pre>
     */
    private String resolveEffectiveModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel;
        }
        Set<String> configuredIds = new java.util.LinkedHashSet<>();
        for (DirectSeekCatalogModel model : options.models()) {
            configuredIds.add(model.id());
        }
        if (configuredIds.isEmpty()) {
            return requestedModel;
        }
        return configuredIds.iterator().next();
    }

    /**
     * SSE 逐行翻译：上游字节流 → 领域事件流（适配器最核心的方法）。
     *
     * <p>运行距离：</p>
     * <ol>
     *   <li>等待首字节（10 秒）：上游可能接受 stream=true 却永不吐数据，
     *       超时返回 false 触发非流式兜底；</li>
     *   <li>逐行读 {@code data:} 分片：JSON 解析 → 提取 delta 里的
     *       reasoning_content / content / tool_calls → 按需 BlockStart →
     *       逐个 publish 增量事件；</li>
     *   <li>{@code [DONE]} 终止行：推理兜底（只有思维链没有正文时合成文本块）
     *       → 关闭全部块 → publish Usage / Finish → 返回 true；</li>
     *   <li>流断在 [DONE] 之前：有数据则抛 STREAM_CLOSED，无数据返回 false。</li>
     * </ol>
     *
     * <p><b>输入示例</b>（即 {@link #serializeRequest} 注释中 curl 请求的
     * 流式响应体，Content-Type: text/event-stream，逐行喂入本方法）：</p>
     * <pre>
     * data: {"choices":[{"delta":{"role":"assistant","content":"Hel"},"index":0}]}
     * data: {"choices":[{"delta":{"reasoning_content":"思考中..."},"index":0}]}
     * data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",
     *         "function":{"name":"get_weather","arguments":"{\"city\":"}}]},"index":0}]}
     * data: {"choices":[{"delta":{},"finish_reason":"stop"}],
     *        "usage":{"prompt_tokens":9,"completion_tokens":4,"total_tokens":13}}
     * data: [DONE]
     * </pre>
     * <p>逐行对应的事件：content → TextDelta；reasoning_content → ReasoningDelta；
     * tool_calls[index] → ToolCallDelta（wire index 映射为块序号）；
     * finish_reason → 待定 FinishReason；usage → 待定 TokenUsage；
     * [DONE] → BlockEnd 全部块 + Usage + Finish。HTTP 非 2xx 时的响应体
     * （如 {@code {"error":{"message":"..."}}}）由 doStream 走
     * {@link #extractErrorMessage} 处理，不进本方法。</p>
     *
     * <p>状态管理：textBlockIndex / reasoningBlockIndex 记录两类块的序号
     * （首片到达时才建块），toolWireIndexToBlockIndex 处理并发多个工具调用
     * 的 wire index → 块序号映射。</p>
     *
     * @param reader    SSE 字符流
     * @param publisher 事件发布器
     * @return 是否成功流式输出（false = 需要非流式兜底）
     */
    //将上游大语言模型返回的 SSE（Server-Sent Events）流式响应，转换成你自己定义的 StreamChunk 事件，并通过 SubmissionPublisher 实时发布给下游消费者。
    //data: {"choices":[{"delta":{"reasoning_content":"让我"},"finish_reason":null}]}
    //data: {"choices":[{"delta":{"reasoning_content":"思考一下"},"finish_reason":null}]}
    //data: {"choices":[{"delta":{"content":"你好"},"finish_reason":null}]}
    //data: {"choices":[{"delta":{"content":"，世界！"},"finish_reason":null}]}
    //data: {"choices":[{"delta":{},"finish_reason":"stop"}]}
    //data: [DONE]
    //||
    //||
    //||
    //||
    //||
    //BlockStart(index=0, type=reasoning)
    //ReasoningDelta(index=0, "让我")
    //ReasoningDelta(index=0, "思考一下")
    //
    //BlockStart(index=1, type=text)
    //TextDelta(index=1, "你好")
    //TextDelta(index=1, "，世界！")
    //
    //BlockEnd(index=0)
    //BlockEnd(index=1)
    //
    //Finish(Stop)
    private boolean translateSse(BufferedReader reader, SubmissionPublisher<StreamChunk> publisher) throws IOException {
        int nextIndex = 0;
        Integer textBlockIndex = null;
        Integer reasoningBlockIndex = null;
        Map<Integer, Integer> toolWireIndexToBlockIndex = new LinkedHashMap<>();
        List<OpenBlock> openBlocks = new ArrayList<>();
        FinishReason pendingFinish = null;
        TokenUsage pendingUsage = null;
        boolean receivedData = false;

        long firstByteDeadline = System.currentTimeMillis() + 10_000L; // 10s first-byte timeout
        String line;
        // 等待首字节：上游可能接受了 stream=true 却始终不吐数据
        while (!reader.ready()) {
            if (System.currentTimeMillis() > firstByteDeadline) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                // 推理兜底：只有 reasoning_content 没有 content 时，合成一个文本块
                if (textBlockIndex == null && reasoningBlockIndex != null) {
                    OpenBlock reasoningBlock = openBlocks.get(reasoningBlockIndex);
                    int newIdx = nextIndex++;
                    OpenBlock textBlock = new OpenBlock(newIdx, "text");
                    textBlock.text.append(reasoningBlock.text.toString());
                    openBlocks.add(textBlock);
                    publisher.submit(new StreamChunk.BlockStart(newIdx, "text"));
                    publisher.submit(new StreamChunk.TextDelta(newIdx, reasoningBlock.text.toString()));
                }

                // 关闭所有块
                for (OpenBlock block : openBlocks) {
                    publisher.submit(new StreamChunk.BlockEnd(block.index, block.toContentBlock()));
                }
                if (pendingUsage != null) {
                    publisher.submit(new StreamChunk.Usage(pendingUsage));
                }
                FinishReason finish = pendingFinish != null ? pendingFinish : new FinishReason.Stop();
                if (finish instanceof FinishReason.Stop && openBlocks.isEmpty()) {
                    finish = new FinishReason.Error(new LlmFailure(
                            "model returned a completed response with no content",
                            LlmErrorCodes.EMPTY_RESPONSE
                    ));
                }
                publisher.submit(new StreamChunk.Finish(finish));
                return true;
            }

            receivedData = true;

            JsonNode chunk;
            try {
                chunk = objectMapper.readTree(data);
            } catch (Exception e) {
                throw new LlmAdapterException(
                        "malformed SSE payload: " + data.substring(0, Math.min(data.length(), 120)),
                        LlmErrorCodes.MALFORMED_RESPONSE
                );
            }

            JsonNode choices = chunk.path("choices");
            for (JsonNode choiceNode : choices) {
                JsonNode delta = choiceNode.path("delta");

                // 推理内容
                String reasoning = delta.path("reasoning_content").asText(null);
                if (reasoning != null && !reasoning.isEmpty()) {
                    if (reasoningBlockIndex == null) {
                        reasoningBlockIndex = (Integer) nextIndex++;
                        openBlocks.add(new OpenBlock(reasoningBlockIndex, "reasoning"));
                        publisher.submit(new StreamChunk.BlockStart(reasoningBlockIndex, "reasoning"));
                    }
                    OpenBlock block = openBlocks.get(reasoningBlockIndex);
                    block.text.append(reasoning);
                    publisher.submit(new StreamChunk.ReasoningDelta(reasoningBlockIndex, reasoning));
                }

                // 文本内容
                String content = delta.path("content").asText(null);
                if (content != null && !content.isEmpty()) {
                    if (textBlockIndex == null) {
                        textBlockIndex = (Integer) nextIndex++;
                        openBlocks.add(new OpenBlock(textBlockIndex, "text"));
                        publisher.submit(new StreamChunk.BlockStart(textBlockIndex, "text"));
                    }
                    OpenBlock block = openBlocks.get(textBlockIndex);
                    block.text.append(content);
                    publisher.submit(new StreamChunk.TextDelta(textBlockIndex, content));
                }

                // 工具调用
                JsonNode toolCalls = delta.path("tool_calls");
                for (JsonNode call : toolCalls) {
                    int wireIndex = call.path("index").asInt(0);
                    Integer blockIndex = toolWireIndexToBlockIndex.get(Optional.of(wireIndex));
                    if (blockIndex == null) {
                        blockIndex = (Integer) nextIndex++;
                        toolWireIndexToBlockIndex.put(Integer.valueOf(wireIndex), blockIndex);
                        openBlocks.add(new OpenBlock(blockIndex, "tool-call"));
                        publisher.submit(new StreamChunk.BlockStart(blockIndex, "tool-call"));
                    }
                    OpenBlock block = openBlocks.get(blockIndex);
                    String callId = call.path("id").asText(null);
                    if (callId != null) block.callId = callId;
                    String fnName = call.path("function").path("name").asText(null);
                    if (fnName != null) block.callName = fnName;
                    String argsDelta = call.path("function").path("arguments").asText("");
                    block.text.append(argsDelta);
                    publisher.submit(new StreamChunk.ToolCallDelta(
                            blockIndex,
                            block.callId != null ? block.callId : "",
                            block.callName,
                            argsDelta
                    ));
                }

                String finishReason = choiceNode.path("finish_reason").asText(null);
                if (finishReason != null) {
                    pendingFinish = mapFinishReason(finishReason);
                }
            }

            JsonNode usageNode = chunk.path("usage");
            if (!usageNode.isMissingNode()) {
                pendingUsage = mapUsage(usageNode);
            }
        }

        // 流没有 [DONE] 就断了
        if (!receivedData) {
            return false;
        }
        throw new LlmAdapterException("SSE payload stream ended without [DONE]", LlmErrorCodes.STREAM_CLOSED);
    }

    /**
     * 上游 finish_reason 方言 → 领域 FinishReason 变体。
     *
     * @param reason 上游结束原因（stop/tool_calls/length 等）
     * @return 领域结束原因；未识别的取值包装为 Error（信息不丢）
     */
    private static FinishReason mapFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> new FinishReason.Stop();
            case "tool_calls" -> new FinishReason.ToolCalls();
            case "length" -> new FinishReason.MaxTokens();
            default -> new FinishReason.Error(new LlmFailure(
                    "model stopped: " + reason, reason.toUpperCase()
            ));
        };
    }

    /**
     * 上游 usage 统计 → 领域 TokenUsage。
     *
     * <p>运行距离：读取 prompt/completion tokens → 兼容两种缓存命中字段
     * （prompt_tokens_details.cached_tokens 与 DeepSeek 的
     * prompt_cache_hit_tokens，0 视为未上报）→ 读取推理 token →
     * inputTokens 剔除缓存命中部分（缓存 token 不重复计费）。</p>
     *
     * @param usage 上游 usage JSON 节点
     * @return 领域 token 用量
     */
    private static TokenUsage mapUsage(JsonNode usage) {
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        Integer cacheRead = null;
        JsonNode details = usage.path("prompt_tokens_details");
        if (!details.isMissingNode()) {
            cacheRead = (Integer) details.path("cached_tokens").asInt(0);
            if (cacheRead == 0) cacheRead = null;
        }
        if (cacheRead == null) {
            cacheRead = (Integer) usage.path("prompt_cache_hit_tokens").asInt(0);
            if (cacheRead == 0) cacheRead = null;
        }
        Integer reasoning = null;
        JsonNode compDetails = usage.path("completion_tokens_details");
        if (!compDetails.isMissingNode()) {
            reasoning = (Integer) compDetails.path("reasoning_tokens").asInt(0);
            if (reasoning == 0) reasoning = null;
        }
        int inputTokens = cacheRead != null ? promptTokens - cacheRead : promptTokens;
        return new TokenUsage(inputTokens, completionTokens, cacheRead, null, reasoning);
    }

    /**
     * HTTP 状态码 → 领域错误码（错误归一化的第一站）。
     *
     * <p>映射依据"重试策略能否据此决策"：401/403→AUTH（不可重试）、
     * 429→RATE_LIMIT（可重试）、400→INVALID_REQUEST（不可重试）、
     * 5xx→SERVER（可重试），其余保留 HTTP_{status} 原始信息。</p>
     *
     * @param status    HTTP 状态码
     * @param errorBody 错误响应体（预留，用于细分判断）
     * @return 领域错误码
     */
    private String httpErrorCode(int status, String errorBody) {
        if (status == 401 || status == 403) return LlmErrorCodes.AUTH;
        if (status == 429) return LlmErrorCodes.RATE_LIMIT;
        if (status == 400) return LlmErrorCodes.INVALID_REQUEST;
        if (status >= 500) return LlmErrorCodes.SERVER;
        return "HTTP_" + status;
    }

    /**
     * 从错误响应体中提取人类可读的失败描述。
     *
     * <p>运行距离：尝试按 OpenAI 错误格式解析 {@code error.message} →
     * 解析失败或字段缺失时原样返回响应体（保证诊断信息不丢）。</p>
     *
     * @param body 错误响应体
     * @return 可读的失败描述
     */
    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText(null);
            if (msg != null && !msg.isBlank()) return msg;
        } catch (Exception ignored) {
            return body;
        }
        return body;
    }

    /**
     * 统一异常出口：把流式过程中抛出的任意异常转成 Finish(Error) 事件发布。
     *
     * <p>设计要点：订阅者永远通过事件流感知失败（而不是 onError 猜原因），
     * LlmAdapterException 保留结构化的 code/status，其他异常统一 UNKNOWN。</p>
     *
     * @param publisher 事件发布器
     * @param e         流式过程中的异常
     */
    private void publishError(SubmissionPublisher<StreamChunk> publisher, Exception e) {
        LlmFailure failure;
        if (e instanceof LlmAdapterException lae) {
            failure = new LlmFailure(lae.getMessage(), lae.code(), lae.status(), null, null);
        } else {
            failure = new LlmFailure(e.getMessage() != null ? e.getMessage() : "LLM adapter failed", LlmErrorCodes.UNKNOWN);
        }
        log.error("DeepSeek adapter stream error: {}", failure.message(), e);
        publisher.submit(new StreamChunk.Finish(new FinishReason.Error(failure)));
    }

    // ── Internal types ────────────────────────────────────────

    /**
     * 流式翻译过程中的"未关闭块"：一边累积一边 publish 增量，
     * [DONE] 时统一用 {@link #toContentBlock()} 生成完整内容块发 BlockEnd。
     */
    private static final class OpenBlock {
        /**
         * 块序号（领域事件里各事件靠它关联到同一块）。
         */
        final int index;
        /**
         * 块类型："text" / "reasoning" / "tool-call"。
         */
        final String kind;
        /**
         * 累积的文本/参数 JSON。
         */
        final StringBuilder text = new StringBuilder();
        /**
         * 工具调用 ID（仅 tool-call 块）。
         */
        String callId;
        /**
         * 工具调用名（仅 tool-call 块）。
         */
        String callName;

        OpenBlock(int index, String kind) {
            this.index = index;
            this.kind = kind;
        }

        /**
         * 按块类型生成完整内容块（BlockEnd 事件携带）。
         *
         * @return TextBlock / ReasoningBlock / ToolCallBlock
         */
        ContentBlock toContentBlock() {
            return switch (kind) {
                case "text" -> new TextBlock(text.toString());
                case "reasoning" -> new ReasoningBlock(text.toString());
                case "tool-call" -> new ToolCallBlock(
                        callId != null ? callId : "call-" + index,
                        callName != null ? callName : "",
                        text.toString()
                );
                default -> throw new IllegalStateException("cannot close block of type: " + kind);
            };
        }
    }

    /**
     * 适配器内部异常。
     */
    public static final class LlmAdapterException extends RuntimeException {
        private final String code;
        private final Integer status;

        public LlmAdapterException(String message, String code) {
            this(message, code, (Integer) null);
        }

        public LlmAdapterException(String message, String code, int status) {
            this(message, code, Integer.valueOf(status));
        }

        public LlmAdapterException(String message, String code, Integer status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() {
            return code;
        }

        public Integer status() {
            return status;
        }
    }
}
