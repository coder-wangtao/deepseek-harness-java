package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

/**
 * 一次 LLM 调用失败的完整诊断信息（错误的"身份证"）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 裸异常只有一个 message，丢失了重试策略需要的结构化信息。
 * 本值对象把失败的关键字段固化下来：
 * <ul>
 *   <li><b>code</b>：{@link LlmErrorCodes} 体系内的稳定错误码，重试策略据此判断是否可重试；</li>
 *   <li><b>status</b>：HTTP 状态码（如 429），便于日志排查，可空（非 HTTP 失败）；</li>
 *   <li><b>providerRetryAfterMs</b>：上游通过 Retry-After 头建议的等待毫秒数，
 *       重试延迟计算时优先尊重它（厂商最清楚自己什么时候恢复）；</li>
 *   <li><b>requestId</b>：上游请求 ID，提交工单/查日志时的关键凭证，可空。</li>
 * </ul>
 *
 * <p>不可变 record：一旦产生就不允许篡改诊断信息，
 * 紧凑构造器保证 message/code 两大必填项永不为 null。</p>
 *
 * @param message              人类可读的失败描述
 * @param code                 领域错误码（{@link LlmErrorCodes} 体系）
 * @param status               HTTP 状态码，非 HTTP 失败为 null
 * @param providerRetryAfterMs 上游建议的重试等待毫秒数，未提供为 null
 * @param requestId            上游请求追踪 ID，未提供为 null
 * @author wt
 */
public record LlmFailure(
        String message,
        String code,
        Integer status,
        Long providerRetryAfterMs,
        String requestId
) {
    /** 紧凑构造器：两大必填字段判空，保证失败信息永远可用。 */
    public LlmFailure {
        java.util.Objects.requireNonNull(message, "message");
        java.util.Objects.requireNonNull(code, "code");
    }

    /**
     * 简化构造器：只有 message + code 的常用场景（如适配器内部主动构造的失败）。
     *
     * @param message 失败描述
     * @param code    领域错误码
     */
    public LlmFailure(String message, String code) {
        this(message, code, null, null, null);
    }
}
