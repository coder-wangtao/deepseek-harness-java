package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;

/**
 * 模型流式输出的最小事件单元（领域层统一的事件语言）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 不同厂商的流式协议（OpenAI SSE 的 delta、Anthropic 的 content_block_delta…）
 * 事件名字和结构都不一样。适配器的核心职责就是把各家方言<b>归一化</b>成这一套
 * 统一事件，下游（组装器、前端 SSE、Agent 编排）只需要认识 8 种事件，
 * 不需要认识任何厂商。
 *
 * <h2>一次典型对话的事件序列</h2>
 * <pre>
 *   BlockStart(0, "text")        ← 一个内容块开始（0 号文本块）
 *   TextDelta(0, "你")           ← 文本增量（可能成百上千个）
 *   TextDelta(0, "好")
 *   BlockEnd(0, TextBlock)       ← 0 号块结束，携带完整内容块
 *   Usage(TokenUsage)            ← token 消耗统计
 *   Finish(Stop)                 ← 终态：正常结束
 * </pre>
 * 工具调用时会出现 {@code BlockStart(i,"tool-call")} + 多个 {@code ToolCallDelta}
 * （参数 JSON 分片到达）；推理模型会先吐 {@code ReasoningDelta} 再吐 {@code TextDelta}。
 *
 * <h2>sealed 的价值</h2>
 * <p>sealed interface + pattern matching 让编译器保证：任何处理 StreamChunk 的
 * switch 要么覆盖全部 8 种事件，要么显式写 default。新增事件类型时，
 * 所有遗漏处理的地方都会编译报错——这是领域语言演进的安全网。</p>
 *
 * @author wt
 */
public sealed interface StreamChunk permits
        StreamChunk.BlockStart,
        StreamChunk.TextDelta,
        StreamChunk.ReasoningDelta,
        StreamChunk.ToolCallDelta,
        StreamChunk.BlockEnd,
        StreamChunk.Usage,
        StreamChunk.Finish,
        StreamChunk.Retry {

    /**
     * 内容块开始事件。
     *
     * @param index     块序号（一次响应内从 0 递增，同一块的 Start/Delta/End 用同一序号）
     * @param blockType 块类型："text" / "reasoning" / "tool-call"
     */
    record BlockStart(int index, String blockType) implements StreamChunk {}

    /**
     * 文本内容增量事件（流式输出的主体，一小段一小段到达）。
     *
     * @param index 所属块序号
     * @param text  本段新增文本
     */
    record TextDelta(int index, String text) implements StreamChunk {}

    /**
     * 推理内容增量事件（思维链，推理模型如 o1/DeepSeek-R1 会先输出这部分）。
     *
     * @param index 所属块序号
     * @param text  本段新增推理文本
     */
    record ReasoningDelta(int index, String text) implements StreamChunk {}

    /**
     * 工具调用增量事件：工具名、ID、参数 JSON 都是分片到达的，由组装器拼接。
     *
     * @param index          所属块序号
     * @param id             工具调用 ID（上游生成，回传工具结果时要用；可能分片，首个非空分片为准）
     * @param name           工具名（首个分片携带）
     * @param argumentsDelta 参数 JSON 的本段增量
     */
    record ToolCallDelta(int index, String id, String name, String argumentsDelta) implements StreamChunk {}

    /**
     * 内容块结束事件，携带<b>完整</b>内容块；收到后该序号即冻结。
     *
     * @param index 结束的块序号
     * @param block 完整内容块（TextBlock / ReasoningBlock / ToolCallBlock）
     */
    record BlockEnd(int index, ContentBlock block) implements StreamChunk {}

    /**
     * token 用量统计事件（通常在 Finish 之前到达一次）。
     *
     * @param usage 输入/输出/缓存/推理 token 统计
     */
    record Usage(TokenUsage usage) implements StreamChunk {}

    /**
     * 终态事件：整个响应流的最后一个事件，之后流关闭。
     *
     * @param reason 结束原因（正常停止 / 触发工具 / 截断 / 错误）
     */
    record Finish(FinishReason reason) implements StreamChunk {}

    /**
     * 重试通知事件：运行时端口在自动重建流之前发给下游，用于 UI 提示"正在重试"。
     *
     * @param attempt        即将开始的尝试次数（从 1 起）
     * @param maxAttempts    最大尝试次数（策略不限时为 null）
     * @param failureMessage 触发重试的失败原因
     */
    record Retry(int attempt, Integer maxAttempts, String failureMessage) implements StreamChunk {}

    /**
     * 判断一个事件是否是"消耗 token 的内容增量"。
     *
     * <p>设计目的：重试模块用它判定"是否已经向下游吐过内容"——
     * 一旦吐过内容就不能重试（会产生重复文本）；空白增量不算。</p>
     *
     * @param chunk 待判断的事件
     * @return 是内容增量且非空返回 true
     */
    static boolean isTokenDelta(StreamChunk chunk) {
        if (chunk instanceof TextDelta td) return !td.text().isEmpty();
        if (chunk instanceof ReasoningDelta rd) return !rd.text().isEmpty();
        if (chunk instanceof ToolCallDelta tcd) return !tcd.argumentsDelta().isEmpty() || tcd.name() != null;
        return false;
    }
}
