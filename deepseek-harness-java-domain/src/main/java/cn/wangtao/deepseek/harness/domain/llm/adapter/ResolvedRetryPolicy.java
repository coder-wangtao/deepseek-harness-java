package cn.wangtao.deepseek.harness.domain.llm.adapter;

import java.util.List;

/**
 * 已解析的重试策略（运行时端口执行流式重试的依据）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 重试策略有两大来源：厂商渠道配置（普通策略）和特殊场景（如某些内部
 * provider 要求无限重试直到成功）。适配器通过 {@code retryPolicy(provider)}
 * 把自己关心的策略交给端口；端口拿到后用统一的退避算法执行重试。
 * sealed 接口保证端口处理策略时编译器强制覆盖两种变体。
 *
 * <h2>两种变体</h2>
 * <ul>
 *   <li>{@link Normal}：限次重试，可重试错误码白名单——绝大多数 provider 用这个；</li>
 *   <li>{@link Always}：无限重试（maxRetries 视为无穷大），仅用于"必须成功"的特殊场景，
 *       慎用——上游持续故障时会一直空转。</li>
 * </ul>
 *
 * <h2>退避参数的含义</h2>
 * <ul>
 *   <li><b>initialDelayMs</b>：第 1 次重试前的基础等待；</li>
 *   <li><b>maxDelayMs</b>：等待上限（指数退避 2^(n-1) 增长到顶后不再翻倍）；</li>
 *   <li><b>jitterRatio</b>：抖动比例（0~1），给等待时间加随机扰动，
 *       防止大量客户端在同一时刻同步重试打垮上游（惊群效应）。</li>
 * </ul>
 *
 * @author wt
 */
public sealed interface ResolvedRetryPolicy permits
        ResolvedRetryPolicy.Normal,
        ResolvedRetryPolicy.Always {

    /** 首次重试的基础等待毫秒数。 */
    int initialDelayMs();

    /** 重试等待的毫秒上限（指数退避封顶值）。 */
    int maxDelayMs();

    /** 抖动比例 0~1，用于打散重试时刻、避免惊群。 */
    double jitterRatio();

    /**
     * 普通限次重试策略。
     *
     * @param initialDelayMs 首次重试基础等待
     * @param maxDelayMs     等待封顶
     * @param jitterRatio    抖动比例
     * @param maxRetries     最大重试次数（不含首次请求）
     * @param retryableCodes 可重试的错误码白名单（参考 {@code LlmErrorCodes.DEFAULT_RETRYABLE_CODES}）
     */
    record Normal(
            int initialDelayMs,
            int maxDelayMs,
            double jitterRatio,
            int maxRetries,
            List<String> retryableCodes
    ) implements ResolvedRetryPolicy {
        /** 紧凑构造器：白名单转不可变列表，防止外部篡改策略。 */
        public Normal {
            retryableCodes = List.copyOf(retryableCodes);
        }
    }

    /**
     * 无限重试策略：忽略错误码与次数上限，只要未吐内容就一直重试。
     *
     * @param initialDelayMs 首次重试基础等待
     * @param maxDelayMs     等待封顶
     * @param jitterRatio    抖动比例
     */
    record Always(
            int initialDelayMs,
            int maxDelayMs,
            double jitterRatio
    ) implements ResolvedRetryPolicy {}
}
