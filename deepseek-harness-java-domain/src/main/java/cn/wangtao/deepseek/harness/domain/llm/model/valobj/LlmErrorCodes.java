package cn.wangtao.deepseek.harness.domain.llm.model.valobj;

/**
 * Harness 自有的大模型错误码体系（跨重试策略、适配器失败、诊断日志统一使用）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 上游错误信息五花八门：HTTP 状态码、厂商自定义 code、SDK 异常类型……
 * 如果直接透传，重试策略就没法写（"哪些错误值得重试"会变成一坨 if-else）。
 * 这里定义一套<b>稳定的领域错误码</b>：适配器把所有失败归一化到这些码上，
 * 重试策略只认码不认厂商。对应 TS 侧 {@code LlmError} 常量。
 *
 * <h2>错误码分类速查</h2>
 * <table border="1">
 *   <tr><th>分类</th><th>错误码</th><th>典型场景 / 是否可重试</th></tr>
 *   <tr><td rowspan="3">瞬时故障</td><td>{@link #RATE_LIMIT}</td><td>429 限流，建议按 Retry-After 重试</td></tr>
 *   <tr><td>{@link #SERVER}</td><td>5xx 上游故障，指数退避重试</td></tr>
 *   <tr><td>{@link #TIMEOUT} / {@link #TRANSPORT}</td><td>网络超时/连接失败，可重试</td></tr>
 *   <tr><td rowspan="2">响应异常</td><td>{@link #EMPTY_RESPONSE}</td><td>200 但没内容，可重试一次</td></tr>
 *   <tr><td>{@link #MALFORMED_RESPONSE} / {@link #STREAM_CLOSED}</td><td>响应格式坏/流中断</td></tr>
 *   <tr><td rowspan="3">不可重试</td><td>{@link #AUTH} / {@link #QUOTA_EXCEEDED}</td><td>401/403 鉴权、额度耗尽，重试无意义</td></tr>
 *   <tr><td>{@link #INVALID_REQUEST} / {@link #CONTEXT_WINDOW_EXCEEDED}</td><td>请求本身有错，需改参数</td></tr>
 *   <tr><td>{@link #ABORTED} / {@link #UNKNOWN}</td><td>主动中止 / 未归类错误</td></tr>
 * </table>
 *
 * <p> {@link #DEFAULT_RETRYABLE_CODES} 是普通重试策略的默认可重试集合，
 * 把"什么错该重试"这条策略也收拢在错误码定义处，一处维护。</p>
 *
 * @author wt
 */
public final class LlmErrorCodes {
    /** 纯常量类，禁止实例化。 */
    private LlmErrorCodes() {}

    /** 上游返回 200 但响应体没有有效内容。 */
    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";
    /** 额度/配额耗尽（非限流，重试无意义）。 */
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    /** 请求超出模型上下文窗口。 */
    public static final String CONTEXT_WINDOW_EXCEEDED = "CONTEXT_WINDOW_EXCEEDED";
    /** 429 触发限流。 */
    public static final String RATE_LIMIT = "RATE_LIMIT";
    /** 上游 5xx 服务端故障。 */
    public static final String SERVER = "SERVER";
    /** 请求超时。 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 网络传输层失败（连接拒绝、DNS 等）。 */
    public static final String TRANSPORT = "TRANSPORT";
    /** 鉴权失败（401/403）。 */
    public static final String AUTH = "AUTH";
    /** 请求参数非法（400）。 */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    /** 响应格式无法解析。 */
    public static final String MALFORMED_RESPONSE = "MALFORMED_RESPONSE";
    /** SSE 流中断（未收到 [DONE] 就断了）。 */
    public static final String STREAM_CLOSED = "STREAM_CLOSED";
    /** 调用方主动中止。 */
    public static final String ABORTED = "ABORTED";
    /** 未归类的未知错误。 */
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * 普通重试策略默认可重试的错误码集合。
     * <p>选择依据：这些都是"再试一次大概率能成"的瞬时故障；
     * AUTH/INVALID_REQUEST 等确定性失败不在其中，重试只会浪费时间。</p>
     */
    public static final java.util.List<String> DEFAULT_RETRYABLE_CODES = java.util.List.of(
            EMPTY_RESPONSE, RATE_LIMIT, SERVER, TIMEOUT, TRANSPORT
    );
}
