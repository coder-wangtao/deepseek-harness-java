package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolSchema;

import java.util.List;
import java.util.Objects;

/**
 * 一次 LLM 生成请求的完整参数（领域层统一的"生成请求"值对象）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 调用方（trigger 层、agent 编排、CLI 探针）不应直接关心"某厂商的 HTTP 请求长什么样"，
 * 只需要把业务意图装进这个值对象，交给端口/适配器去翻译。
 * 它是整条 LLM 链路的<b>入参契约</b>：从 main 到 HTTP 报文，全程传递的都是它。
 *
 * <h2>参数速查</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>含义</th><th>使用方</th></tr>
 *   <tr><td>provider</td><td>模型提供方编码，决定路由到哪个适配器</td><td>端口路由</td></tr>
 *   <tr><td>model</td><td>模型名（如 deepseek-chat / glm-5.3-flash）</td><td>适配器请求体</td></tr>
 *   <tr><td>reasoningEffort</td><td>推理力度档位（低/中/高），仅推理模型生效</td><td>适配器请求体</td></tr>
 *   <tr><td>messages</td><td>对话历史（含文本/工具调用/工具结果块）</td><td>适配器序列化</td></tr>
 *   <tr><td>system</td><td>系统提示词（单独放在 messages 最前）</td><td>适配器序列化</td></tr>
 *   <tr><td>tools</td><td>可调用工具的 schema 列表（Function Calling）</td><td>适配器序列化</td></tr>
 *   <tr><td>temperature</td><td>采样温度，越高越随机</td><td>适配器请求体</td></tr>
 *   <tr><td>maxTokens</td><td>最大输出 token 数</td><td>适配器请求体</td></tr>
 *   <tr><td>stop</td><td>停止词列表</td><td>适配器请求体</td></tr>
 *   <tr><td>sessionId</td><td>会话 ID，透传到上游请求头用于追踪</td><td>适配器请求头</td></tr>
 *   <tr><td>purpose</td><td>调用用途标记（如 chat / summary），用于诊断</td><td>日志/诊断</td></tr>
 *   <tr><td>baseUrl / apiKey</td><td>调用方临时覆盖渠道配置（如用户自带 Key）</td><td>适配器（优先于全局配置）</td></tr>
 *   <tr><td>protocol</td><td>渠道协议编码（openai / anthropic / ollama）；空值按 openai 兼容协议处理</td><td>协议路由</td></tr>
 * </table>
 *
 * <p>record 的紧凑构造器做了两件防御：必填字段判空、集合转不可变列表，
 * 保证值对象一经创建就是合法且不可变的（值对象的本质要求）。</p>
 *
 * @param protocol 渠道协议编码（openai / anthropic / ollama）；空值按 openai 兼容协议处理
 * @author wt
 */
public record GenerateOptions(
        String provider,
        String model,
        String reasoningEffort,
        List<Message> messages,
        String system,
        List<ToolSchema> tools,
        Double temperature,
        Integer maxTokens,
        List<String> stop,
        String sessionId,
        String purpose,
        String baseUrl,
        String apiKey,
        String protocol
) {
    /** 紧凑构造器：必填校验 + 集合防御性拷贝，保证值对象不可变。 */
    public GenerateOptions {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        messages = List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
        stop = stop == null ? null : List.copyOf(stop);
    }

    /**
     * 兼容旧调用方的构造器：协议按 openai 兼容处理。
     * <p>在 protocol 字段引入（2-4 协议路由章节）之前创建的调用方继续可用，
     * 新代码建议直接用 14 参完整构造器。</p>
     */
    public GenerateOptions(
            String provider,
            String model,
            String reasoningEffort,
            List<Message> messages,
            String system,
            List<ToolSchema> tools,
            Double temperature,
            Integer maxTokens,
            List<String> stop,
            String sessionId,
            String purpose,
            String baseUrl,
            String apiKey
    ) {
        this(provider, model, reasoningEffort, messages, system, tools, temperature,
                maxTokens, stop, sessionId, purpose, baseUrl, apiKey, null);
    }

    /**
     * 抽取"影响模型输出"的参数子集，形成 {@link LlmCallConfig}。
     * <p>设计目的：重试/缓存等机制判断"两次调用参数是否相同"时，
     * 不应该被 sessionId、purpose 这类运维字段干扰——
     * 只有 temperature、stop 这些真正改变模型行为的字段才参与比较。</p>
     *
     * @return 用于等价比较的调用配置
     */
    public LlmCallConfig toCallConfig() {
        return new LlmCallConfig(provider, model, reasoningEffort, temperature, maxTokens, stop);
    }
}
