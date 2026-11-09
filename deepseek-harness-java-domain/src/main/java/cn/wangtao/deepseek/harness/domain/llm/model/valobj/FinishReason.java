package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

/**
 * 模型为什么停止输出（流式终态的原因描述）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 各厂商的 finish_reason 取值不同（OpenAI: stop/tool_calls/length，
 * 其他厂商还有 content_filter 等）。适配器负责把厂商方言映射到这里的 5 个变体，
 * 下游只需要 switch 这 5 种即可。sealed 结构保证新增变体时编译器会
 * 逼着所有处理方补齐分支。
 *
 * <h2>变体与上游取值的对应关系</h2>
 * <table border="1">
 *   <tr><th>变体</th><th>含义</th><th>典型上游取值</th></tr>
 *   <tr><td>{@link Stop}</td><td>模型自然说完</td><td>stop</td></tr>
 *   <tr><td>{@link ToolCalls}</td><td>模型要求调用工具</td><td>tool_calls / tool_use</td></tr>
 *   <tr><td>{@link MaxTokens}</td><td>输出被 max_tokens 截断（下游应丢弃残缺工具调用）</td><td>length / max_tokens</td></tr>
 *   <tr><td>{@link Aborted}</td><td>调用方主动中止</td><td>无（框架内部产生）</td></tr>
 *   <tr><td>{@link Error}</td><td>流以失败收场，携带 {@link LlmFailure} 详情</td><td>HTTP 错误/空响应等</td></tr>
 * </table>
 *
 * @author wt
 */
public sealed interface FinishReason permits
        FinishReason.Stop,
        FinishReason.ToolCalls,
        FinishReason.MaxTokens,
        FinishReason.Aborted,
        FinishReason.Error {

    /** 正常结束：模型把话说完了。 */
    record Stop() implements FinishReason {}

    /** 模型请求调用工具，等待执行结果后继续对话。 */
    record ToolCalls() implements FinishReason {}

    /** 输出达到 max_tokens 上限被截断，内容可能不完整。 */
    record MaxTokens() implements FinishReason {}

    /** 调用方主动中止（如用户点了停止按钮）。 */
    record Aborted(LlmFailure failure) implements FinishReason {
        public Aborted {
            java.util.Objects.requireNonNull(failure, "failure");
        }
    }

    /** 流以错误收场，{@link LlmFailure} 携带错误码/HTTP 状态等诊断信息。 */
    record Error(LlmFailure failure) implements FinishReason {
        public Error {
            java.util.Objects.requireNonNull(failure, "failure");
        }
    }

    /**
     * 是否为失败终态（Aborted 或 Error）。
     * <p>设计目的：调用方据此决定是否向用户展示错误、是否触发补偿逻辑，
     * 避免到处写 {@code instanceof Aborted || instanceof Error}。</p>
     *
     * @return 失败终态返回 true
     */
    default boolean isFailure() {
        return this instanceof Aborted || this instanceof Error;
    }
}
