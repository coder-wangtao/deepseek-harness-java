package cn.wangtao.deepseek.harness.domain.llm.adapter;

import cn.wangtao.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.StreamChunk;

import java.util.List;
import java.util.concurrent.Flow;

/**
 * 与具体供应商无关的大模型适配器 SPI（领域层定义的能力契约）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 这是"插座标准"：领域层只声明"一个 LLM 供应商适配器必须会做什么"，
 * 基础设施层（DeepSeek/OpenAI 兼容、Anthropic、Ollama…）各自实现插头。
 * 上层代码（端口、Agent 编排）只依赖本接口，换厂商/加厂商不动领域代码——
 * 这正是 DDD 依赖倒置在模型接入上的落地。
 *
 * <h2>实现方的职责清单</h2>
 * <ol>
 *   <li>{@link #stream}：把 {@link GenerateOptions} 翻译成厂商 HTTP 请求，
 *       并把厂商流式响应归一化为 {@link StreamChunk} 事件流；</li>
 *   <li>{@link #listModels} / {@link #resolveModel}：暴露模型目录与模型元数据
 *       （上下文窗口、默认输出上限）；</li>
 *   <li>{@link #providerInfo} / {@link #providerId}：provider 展示信息与唯一编码；</li>
 *   <li>{@link #retryPolicy}（可选）：声明重试策略，不声明则端口不做重试。</li>
 * </ol>
 *
 * <h2>调用流程</h2>
 * <p>调用方 → 端口按 provider 找到本接口实现 → stream() 返回事件流 →
 * 下游订阅消费。适配器内部通常：领域请求 → HTTP 客户端 → 解析 JSON/SSE →
 * 逐事件 publish。</p>
 *
 * <h2>curl 验证样例（WaLiAPI 网关，OpenAI 兼容协议）</h2>
 * <p>适配器最终发出的就是下面这类 HTTP 请求，可用 curl 直接复现
 * {@link #stream} 覆盖的核心链路（流式对话 / 工具调用 / 推理 / 用量统计）：</p>
 *
 * <pre>
 * # ── 1. 流式对话（覆盖 stream() 基础链路：TextDelta 事件流）──
 * curl -N http://127.0.0.1:8777/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
 *   -d '{
 *     "model": "glm-5.3-flash",
 *     "stream": true,
 *     "messages": [{"role": "user", "content": "用一句话介绍你自己"}]
 *   }'
 *
 * # ── 2. 推理模型（覆盖 ReasoningDelta：reasoning_content 分片）──
 * curl -N http://127.0.0.1:8777/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
 *   -d '{
 *     "model": "glm-5.3-flash",
 *     "stream": true,
 *     "reasoning_effort": "medium",
 *     "messages": [{"role": "user", "content": "9.11 和 9.9 哪个大？先推理再回答"}]
 *   }'
 *
 * # ── 3. 工具调用（覆盖 ToolCallDelta 与第二轮 role=tool 消息）──
 * curl -N http://127.0.0.1:8777/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
 *   -d '{
 *     "model": "glm-5.3-flash",
 *     "stream": true,
 *     "tools": [{
 *       "type": "function",
 *       "function": {
 *         "name": "get_weather",
 *         "description": "查询指定城市当前天气",
 *         "parameters": {
 *           "type": "object",
 *           "properties": {"city": {"type": "string", "description": "城市名"}},
 *           "required": ["city"]
 *         }
 *       }
 *     }],
 *     "messages": [{"role": "user", "content": "北京今天天气怎么样？"}]
 *   }'
 *
 * # ── 4. 工具结果回传（模型发起 tool_calls 后的第二轮请求，
 * #      覆盖 serializeRequest 的 role=tool 消息翻译）──
 * curl -N http://127.0.0.1:8777/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
 *   -d '{
 *     "model": "glm-5.3-flash",
 *     "stream": true,
 *     "tools": [{
 *       "type": "function",
 *       "function": {
 *         "name": "get_weather",
 *         "description": "查询指定城市当前天气",
 *         "parameters": {
 *           "type": "object",
 *           "properties": {"city": {"type": "string"}},
 *           "required": ["city"]
 *         }
 *       }
 *     }],
 *     "messages": [
 *       {"role": "user", "content": "北京今天天气怎么样？"},
 *       {"role": "assistant", "content": null,
 *        "tool_calls": [{"id": "call_001", "type": "function",
 *         "function": {"name": "get_weather", "arguments": "{\"city\":\"北京\"}"}}]},
 *       {"role": "tool", "tool_call_id": "call_001",
 *        "content": "{\"city\":\"北京\",\"weather\":\"晴\",\"temp\":\"28℃\"}"}
 *     ]
 *   }'
 *
 * # ── 5. 用量统计（非流式，覆盖 Usage/TokenUsage 解析：
 * #      prompt_tokens / completion_tokens / 缓存命中字段）──
 * curl http://127.0.0.1:8777/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
 *   -d '{
 *     "model": "glm-5.3-flash",
 *     "stream_options": {"include_usage": true},
 *     "messages": [{"role": "user", "content": "1+1=?"}]
 *   }'
 * </pre>
 *
 * <p>事件覆盖对照：样例 1 → BlockStart/TextDelta/BlockEnd/Finish(Stop)；
 * 样例 2 → ReasoningDelta（无正文时触发推理兜底合成文本块）；
 * 样例 3 → ToolCallDelta + Finish(ToolCalls)；样例 4 → 请求侧 tool 消息翻译；
 * 样例 5 → Usage（含 stream_options.include_usage 触发 usage 分片）。</p>
 *
 * @author wt
 */
public interface LlmAdapter {

    /**
     * 从模型调用中持续输出块数据（流式生成的唯一入口）。
     *
     * <p>实现注意：必须是异步的（内部起线程/响应式），调用后立即返回可订阅的
     * Publisher；事件顺序须遵循 BlockStart → *Delta → BlockEnd → (Usage) → Finish；
     * 任何失败也要以 Finish(Error) 或 onError 体现，不允许"无声卡死"。</p>
     *
     * <p><b>curl 出入参</b>（OpenAI 兼容协议，完整样例见类注释「curl 验证样例」）：
     * 入参 = 本方法收到的 {@link GenerateOptions} 经适配器翻译后的请求体，
     * 出参 = SSE 流式响应，逐行解析为 {@link StreamChunk} 事件：</p>
     *
     * <pre>
     * # 入参（POST，翻译 GenerateOptions 而来）
     * curl -N http://127.0.0.1:8777/v1/chat/completions \
     *   -H "Content-Type: application/json" \
     *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9" \
     *   -d '{
     *     "model": "glm-5.3-flash", "stream": true,
     *     "messages": [{"role": "user", "content": "1+1"}]
     *   }'
     *
     * # 出参（text/event-stream，逐行 → 领域事件）
     * data: {"choices":[{"delta":{"role":"assistant","content":"Hel"},"index":0}]}   → BlockStart/TextDelta
     * data: {"choices":[{"delta":{"content":"lo!"},"index":0}]}                      → TextDelta
     * data: {"choices":[{"delta":{},"finish_reason":"stop"}],
     *        "usage":{"prompt_tokens":9,"completion_tokens":4,"total_tokens":13}}    → Finish(Stop)/Usage
     * data: [DONE]                                                                    → 流关闭
     * </pre>
     *
     * @param options 生成参数
     * @return 模型输出事件流
     */
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);

    /**
     * 列出该 provider 暴露的模型目录。
     *
     * <p><b>curl 出入参</b>（xfg-2-1 最小版：模型目录来自本地配置
     * {@code DeepSeekAdapterOptions.models()}，<b>不发起任何 HTTP 请求</b>，
     * 下面 curl 是上游的对应端点，后续章节引入上游发现时使用）：</p>
     *
     * <pre>
     * # 入参（GET，无请求体）
     * curl http://127.0.0.1:8777/v1/models \
     *   -H "Authorization: Bearer sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9"
     *
     * # 出参（JSON）→ 映射为 List&lt;LlmModelInfo&gt;
     * {"object": "list", "data": [
     *   {"id": "glm-5.3-flash", "owned_by": "zhipu"},
     *   {"id": "deepseek-chat", "owned_by": "deepseek"}
     * ]}
     * </pre>
     *
     * @param provider 模型提供方编码
     * @return 模型条目列表（可为空列表，不可为 null）
     */
    List<LlmModelInfo> listModels(String provider);

    /**
     * 解析某个模型的精确元数据（上下文窗口、默认输出上限等），
     * 供路由层做上下文长度预检与参数默认值填充。
     *
     * <p><b>curl 出入参</b>（xfg-2-1 最小版：元数据同样来自本地配置的
     * {@code contextWindow/maxTokens}，<b>不发起 HTTP 请求</b>；
     * OpenAI 协议没有"单模型详情"端点，标准做法是 GET /v1/models
     * 后按 id 过滤，出入参见 {@link #listModels}。参数即方法签名：
     * 入 {@code provider="deepseek", model="glm-5.3-flash"}，
     * 出 {@code LlmResolvedModelInfo[id, context.contextWindow, defaultMaxTokens...]}）。</p>
     *
     * @param provider 模型提供方编码
     * @param model    模型编码
     * @return 已解析的模型元数据
     */
    LlmResolvedModelInfo resolveModel(String provider, String model);

    /**
     * 返回 provider 的展示信息（控制台/列表页用）。
     *
     * <p><b>curl 出入参</b>：纯本地契约方法，<b>无 HTTP 对应</b>——
     * 入 {@code provider="deepseek"}，出 {@code LlmProviderInfo[id="deepseek", name="DeepSeek"]}，
     * 数据来自适配器内部（providerId() 与实现类硬编码显示名）。</p>
     *
     * @param provider 模型提供方编码
     * @return provider 展示信息
     */
    LlmProviderInfo providerInfo(String provider);

    /**
     * 返回当前适配器服务的 provider 标识（注册到端口时使用的 key）。
     *
     * <p><b>curl 出入参</b>：纯本地契约方法，<b>无 HTTP 对应</b>——
     * 无入参，出 {@code "deepseek"}，即适配器在端口注册表中的 key。</p>
     *
     * @return provider 编码，如 "deepseek"
     */
    String providerId();

    /**
     * 返回 provider 自有的重试策略；若不需要则返回 null（端口不做重试）。
     *
     * <p><b>curl 出入参</b>：纯本地契约方法，<b>无 HTTP 对应</b>——
     * 入 {@code provider="deepseek"}，出 {@code ResolvedRetryPolicy}
     * （来自渠道配置的重试参数）。它与 curl 样例 5 之外的场景相关：
     * 当 chat/completions 请求返回 429/5xx 时，端口依据本策略决定是否重试，
     * 对应的错误响应体如：</p>
     *
     * <pre>
     * HTTP/1.1 429 Too Many Requests
     * {"error": {"message": "rate limit reached", "type": "rate_limit_error"}}
     * </pre>
     *
     * <p>默认实现返回 null，即"无策略"是最常见选择。</p>
     *
     * @param provider 模型提供方编码
     * @return 重试策略，无则 null
     */
    default ResolvedRetryPolicy retryPolicy(String provider) {
        return null;
    }

    // ── 嵌套类型 ──────────────────────────────────────────────

    /** 单个 provider 的展示信息（id 为编码，name 为控制台显示名）。 */
    record LlmProviderInfo(String id, String name) {}

    /** 单个模型条目（目录列表用，轻量元数据）。 */
    record LlmModelInfo(
            /** 所属 provider 编码。 */
            String provider,
            /** 模型编码（请求时的 model 参数值）。 */
            String id,
            /** 显示名。 */
            String name,
            /** 模型描述，可空。 */
            String description,
            /** 支持的输入模态（如 "text"、"image"）。 */
            List<String> inputModalities
    ) {}

    /** 精确路由的模型元数据，包含上下文与推理信息。 */
    record LlmResolvedModelInfo(
            /** 所属 provider 编码。 */
            String provider,
            /** 模型编码。 */
            String id,
            /** 显示名。 */
            String name,
            /** 模型描述，可空。 */
            String description,
            /** 支持的输入模态列表。 */
            List<String> inputModalities,
            /** 上下文窗口信息。 */
            LlmModelContext context,
            /** 默认最大输出 token，可空。 */
            Integer defaultMaxTokens,
            /** 推理能力描述，非推理模型为 null。 */
            LlmModelReasoning reasoning
    ) {}

    /** 模型上下文窗口。 */
    record LlmModelContext(int contextWindow) {}

    /** 模型推理能力描述（推理档位列表与默认档位）。 */
    record LlmModelReasoning(
            /** 可选推理档位。 */
            List<LlmReasoningEffortInfo> efforts,
            /** 默认档位编码，可空。 */
            String defaultEffort
    ) {}

    /** 单个推理档位（低/中/高等）。 */
    record LlmReasoningEffortInfo(String id, String name, String description) {}
}
