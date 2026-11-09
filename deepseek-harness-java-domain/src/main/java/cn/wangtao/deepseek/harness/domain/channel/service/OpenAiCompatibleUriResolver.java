package cn.wangtao.deepseek.harness.domain.channel.service;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * OpenAI 兼容端点解析器（渠道层领域服务）。
 *
 * <h2>设计目的</h2>
 * <p>
 * 不同"OpenAI 兼容"厂商给的基础地址五花八门：
 * <pre>
 *   https://api.deepseek.com              ← 没有版本段，要补 /v1
 *   https://api.openai.com/v1             ← 自带 /v1，不能再补
 *   https://dashscope.aliyuncs.com/compatible-mode/v1  ← 路径里埋着 /v1
 *   https://generativelanguage.googleapis.com/v1beta  ← 版本段带字母后缀
 * </pre>
 * 如果在适配器里无脑拼 {@code baseUrl + "/v1/chat/completions"}，后三种配置就会 404。
 * 这个解析器把"怎么拼 URL"这条规则收拢到领域层一处，适配器只管调用。
 * <p>
 * 它被放在 <b>domain</b> 层而不是 infrastructure，是因为"渠道地址规范化"
 * 是一条与具体厂商无关的业务规则——后续 Anthropic、Ollama 等适配器
 * 只要兼容 OpenAI 协议，都复用它。
 *
 * <h2>拼接规则（按顺序判定）</h2>
 * <ol>
 *   <li>去掉末尾多余的 {@code /}；</li>
 *   <li>地址已经以目标 path 结尾 → 原样返回（用户配了完整端点）；</li>
 *   <li>地址中含版本段（{@code /v1}、{@code /v3}、{@code /v1beta} 等）→ 直接追加 path；</li>
 *   <li>都没有 → 补上默认的 {@code /v1} 再追加 path。</li>
 * </ol>
 *
 * @author wt
 */
public final class OpenAiCompatibleUriResolver {

    /**
     * 版本段识别正则：匹配 {@code /v1}、{@code /v3}、{@code /v1beta}、{@code /v4} 等。
     * <p>{@code (?:[a-z]+)?} 允许 v 数字后跟字母后缀（如 beta），
     * {@code (?:/|$)} 要求版本段后面是 {@code /} 或字符串结尾，
     * 避免把 {@code /vendor} 这种普通路径误判成版本段。</p>
     */
    private static final Pattern VERSIONED_PATH = Pattern.compile("/v\\d+(?:[a-z]+)?(?:/|$)");

    /** 纯静态工具类，禁止实例化。 */
    private OpenAiCompatibleUriResolver() {
    }

    /**
     * 解析 OpenAI 兼容模型列表端点（GET），用于从渠道侧拉取可用模型目录。
     *
     * <p>示例：{@code https://api.deepseek.com} → {@code https://api.deepseek.com/v1/models}</p>
     *
     * @param baseUrl 渠道基础地址
     * @return 模型列表 URI
     */
    public static URI resolveModelsUri(String baseUrl) {
        return resolve(baseUrl, "/models");
    }

    /**
     * 解析 OpenAI 兼容 Chat Completions 端点（POST），对话补全的统一入口。
     *
     * <p>示例：{@code http://127.0.0.1:8777} → {@code http://127.0.0.1:8777/v1/chat/completions}</p>
     *
     * @param baseUrl 渠道基础地址
     * @return Chat Completions URI
     */
    public static URI resolveChatCompletionsUri(String baseUrl) {
        return resolve(baseUrl, "/chat/completions");
    }

    /**
     * 通用拼接逻辑（见类注释"拼接规则"）。
     *
     * <p>运行距离：规范化末尾斜杠 → 判断是否已是完整端点 → 正则探测版本段 →
     * 按需补 {@code /v1} → 生成 {@link URI}。</p>
     *
     * @param baseUrl 渠道基础地址
     * @param path    目标端点路径（如 {@code /chat/completions}）
     * @return 拼接完成的 URI
     */
    private static URI resolve(String baseUrl, String path) {
        // 规则 1：去掉末尾 /，统一成无尾斜杠形态，避免出现 //chat/completions
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        // 规则 2：用户已经配了完整端点，尊重原样
        if (normalized.endsWith(path)) {
            return URI.create(normalized);
        }
        // 规则 3：自带版本段则沿用（不同厂商版本号不同，不能瞎补 /v1）
        if (VERSIONED_PATH.matcher(normalized).find()) {
            return URI.create(normalized + path);
        }
        // 规则 4：兜底补 OpenAI 标准版本段
        return URI.create(normalized + "/v1" + path);
    }

}
