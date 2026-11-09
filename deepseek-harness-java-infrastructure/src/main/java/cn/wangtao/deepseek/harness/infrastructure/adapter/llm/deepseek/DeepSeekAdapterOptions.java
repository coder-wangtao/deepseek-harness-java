package cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek;

import cn.wangtao.deepseek.harness.domain.llm.adapter.ResolvedRetryPolicy;
import java.util.List;

/**
 * {@link DeepSeekAdapter} 的配置项（适配器的"出厂设置"）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 适配器需要的所有环境信息（连哪、用什么 Key、有哪些模型、怎么重试）
 * 都封装在这一个不可变 record 里，随适配器构造时一次性注入——
 * 适配器本身无 setter，运行期配置不变，天然线程安全。
 * 对应 TS 侧 {@code DeepSeekConnectionOptions} 和 {@code DeepSeekAdapterOptions}。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li><b>baseUrl</b>：渠道基础地址（如 {@code http://127.0.0.1:8777}），
 *       适配器会用 {@code OpenAiCompatibleUriResolver} 拼出完整端点；</li>
 *   <li><b>apiKey</b>：鉴权 Key，可被 GenerateOptions.apiKey() 逐次覆盖；</li>
 *   <li><b>maxTokens</b>：未在模型目录指定时的默认输出上限；</li>
 *   <li><b>defaultContextWindow</b>：未在模型目录指定时的默认上下文窗口；</li>
 *   <li><b>streamIdleTimeoutMs</b>：流式空闲超时（预留参数）；</li>
 *   <li><b>models</b>：静态模型目录（最小版不做上游发现，模型写死在配置）；</li>
 *   <li><b>retryPolicy</b>：重试策略，null 表示不重试。</li>
 * </ul>
 *
 * @param baseUrl              渠道基础地址
 * @param apiKey               鉴权 Key
 * @param maxTokens            默认最大输出 token
 * @param defaultContextWindow 默认上下文窗口大小
 * @param streamIdleTimeoutMs  流式空闲超时毫秒数
 * @param models               静态模型目录
 * @param retryPolicy          重试策略（null = 不重试）
 * @author wt
 */
public record DeepSeekAdapterOptions(
        String baseUrl,
        String apiKey,
        int maxTokens,
        int defaultContextWindow,
        int streamIdleTimeoutMs,
        List<DirectSeekCatalogModel> models,
        ResolvedRetryPolicy retryPolicy
) {
    /** 紧凑构造器：必填校验 + 模型目录防御性拷贝。 */
    public DeepSeekAdapterOptions {
        java.util.Objects.requireNonNull(baseUrl, "baseUrl");
        java.util.Objects.requireNonNull(apiKey, "apiKey");
        models = List.copyOf(models);
    }

    /**
     * 解析实际生效的 API Key。
     *
     * <p>当前实现直接返回配置值；预留此方法是为了后续支持
     * "从密钥管理服务按需取 Key"等扩展，调用方无需感知差异。</p>
     *
     * @return 生效的 API Key
     */
    public String resolveApiKey() {
        return apiKey;
    }
}
