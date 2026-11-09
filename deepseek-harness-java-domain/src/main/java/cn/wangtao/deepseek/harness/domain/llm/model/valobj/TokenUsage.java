package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

/**
 * 一次（或多次累计的）token 用量统计。
 *
 * <h2>设计目的</h2>
 * <p>
 * 计费、限流、上下文窗口管理都依赖精确的 token 计数。这个值对象把各家
 * 用量字段（prompt_tokens/completion_tokens、缓存命中、推理 token）
 * 归一化成统一结构，并提供 {@link #plus} 做跨调用累计（如多轮工具调用的总消耗）。
 *
 * <h2>字段语义</h2>
 * <ul>
 *   <li><b>inputTokens</b>：本次实际计费的输入 token（已剔除缓存命中部分）；</li>
 *   <li><b>outputTokens</b>：模型输出 token（含推理 token）；</li>
 *   <li><b>cacheReadTokens</b>：命中前缀缓存的输入 token（计费打折，可空——上游没报就是 null）；</li>
 *   <li><b>cacheWriteTokens</b>：写入缓存的 token（Anthropic 语义，OpenAI 系通常为 null）；</li>
 *   <li><b>reasoningTokens</b>：推理模型思考过程消耗的 token（包含在 outputTokens 内）。</li>
 * </ul>
 *
 * <p>为什么用 Integer 而不是 int：可空语义。上游没上报的字段保持 null，
 * 区别于"确实是 0"，避免 UI 把没数据渲染成 0。</p>
 *
 * @param inputTokens       计费输入 token（剔除缓存命中）
 * @param outputTokens      输出 token
 * @param cacheReadTokens   缓存命中 token，未上报为 null
 * @param cacheWriteTokens  缓存写入 token，未上报为 null
 * @param reasoningTokens   推理消耗 token，未上报为 null
 * @author wt
 */
public record TokenUsage(
        int inputTokens,
        int outputTokens,
        Integer cacheReadTokens,
        Integer cacheWriteTokens,
        Integer reasoningTokens
) {
    /**
     * 简化构造器：只关心输入/输出两个核心数字（缓存与推理信息未上报）。
     *
     * @param inputTokens  输入 token
     * @param outputTokens 输出 token
     */
    public TokenUsage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, null, null, null);
    }

    /**
     * 输入 + 输出的总 token 数（计费口径的粗略合计）。
     *
     * @return 总 token 数
     */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * 累加另一次用量，得到合计值（null 字段按缺失处理：双方都缺才保持 null）。
     *
     * <p>运行距离：逐字段相加 → nullable 字段做"双 null 判定" → 返回新值对象
     * （record 不可变，不修改自身）。</p>
     *
     * @param other 另一次用量；为 null 时直接返回自身
     * @return 合计后的新 TokenUsage
     */
    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                addNullable(cacheReadTokens, other.cacheReadTokens),
                addNullable(cacheWriteTokens, other.cacheWriteTokens),
                addNullable(reasoningTokens, other.reasoningTokens)
        );
    }

    /**
     * 两个可空整数相加：任一方有值就出结果，双方都为 null 才返回 null。
     */
    private static Integer addNullable(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (Integer) ((left == null ? 0 : left) + (right == null ? 0 : right));
    }
}
