package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

import java.util.List;
import java.util.Objects;

/**
 * 一次 LLM 调用的"核心生成参数"快照（provider/model/采样参数的子集视图）。
 *
 * <h2>设计目的</h2>
 * <p>
 * {@link GenerateOptions} 字段很多，其中 sessionId、purpose、baseUrl、apiKey
 * 这些是<b>运维/传输属性</b>，不改变模型行为；而本类只保留真正影响模型输出的字段。
 * 有了它，重试去重、响应缓存、调用审计里"两次调用是否等价"的判断就有了
 * 明确、稳定的比较依据，不会被无关字段干扰。
 *
 * <p>为什么不用 record 自带的 equals：record 的 equals 是全字段比较，
 * 这里需要在"子集"上比较，所以提供 {@link #configEquals} 定制比较逻辑。
 * stop 列表按<b>顺序敏感</b>比较（stop 词的出现顺序影响停止行为）。</p>
 *
 * @param provider        模型提供方编码
 * @param model           模型名
 * @param reasoningEffort 推理力度档位（可空）
 * @param temperature     采样温度（可空 = 用上游默认）
 * @param maxTokens       最大输出 token（可空）
 * @param stop            停止词列表（可空）
 * @author wt
 */
public record LlmCallConfig(
        String provider,
        String model,
        String reasoningEffort,
        Double temperature,
        Integer maxTokens,
        List<String> stop
) {
    /** 紧凑构造器：必填校验 + stop 列表防御性拷贝。 */
    public LlmCallConfig {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        stop = stop == null ? null : List.copyOf(stop);
    }

    /**
     * 判断两次调用配置是否等价（语义上的 equals）。
     *
     * <p>运行距离：provider 硬比较（null 直接不等）→ model → reasoningEffort →
     * temperature → maxTokens（均用 Objects.equals 处理可空）→ stop 列表逐元素
     * 顺序比较 → 全部一致返回 true。短路设计保证最常见的"provider 不同"
     * 只花一次字符串比较。</p>
     *
     * @param other 待比较的配置
     * @return 核心生成参数完全一致返回 true
     */
    public boolean configEquals(LlmCallConfig other) {
        if (other == null) return false;
        if (!provider.equals(other.provider)) return false;
        if (!model.equals(other.model)) return false;
        if (!Objects.equals(reasoningEffort, other.reasoningEffort)) return false;
        if (!Objects.equals(temperature, other.temperature)) return false;
        if (!Objects.equals(maxTokens, other.maxTokens)) return false;
        // stop 双方都为 null 视为相等（引用比较即可）；否则逐元素顺序比较
        if (stop == null || other.stop == null) return stop == other.stop;
        if (stop.size() != other.stop.size()) return false;
        for (int i = 0; i < stop.size(); i++) {
            if (!stop.get(i).equals(other.stop.get(i))) return false;
        }
        return true;
    }
}
