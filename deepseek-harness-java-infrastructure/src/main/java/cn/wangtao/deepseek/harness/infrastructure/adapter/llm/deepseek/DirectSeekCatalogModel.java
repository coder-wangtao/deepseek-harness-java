package cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek;

import java.util.Optional;

/**
 * DeepSeek 适配器对外暴露的一个可选模型目录项。
 *
 * <h2>设计目的</h2>
 * <p>
 * 最小版适配器不做"从上游 /v1/models 拉取目录"，而是在
 * {@link DeepSeekAdapterOptions} 里静态声明本渠道可用哪些模型。
 * 本 record 就是目录里的一行：模型编码 + 展示名 + 可选元数据。
 * 对应 TS 侧 {@code DeepSeekCatalogModel}。
 *
 * <p>字段大量可空（name/description/contextWindow/maxTokens）：
 * 只有 id 是必须的——调用时真正发到上游的 model 参数就是它，
 * 其余仅用于控制台展示与参数默认值，缺了就回落适配器全局默认。</p>
 *
 * @param id            模型编码（请求体的 model 字段值，必填）
 * @param name          显示名，可空
 * @param description   模型描述，可空
 * @param contextWindow 上下文窗口大小，可空（回落 defaultContextWindow）
 * @param maxTokens     默认最大输出 token，可空（回落适配器 maxTokens）
 * @author wt
 */
public record DirectSeekCatalogModel(
        String id,
        String name,
        String description,
        Integer contextWindow,
        Integer maxTokens
) {
    /** 紧凑构造器：id 必填校验——没有 id 的目录项毫无意义。 */
    public DirectSeekCatalogModel {
        java.util.Objects.requireNonNull(id, "id");
    }

    /**
     * 显示名的 Optional 视图（调用方链式 orElse 回落默认值，避免手写判空）。
     *
     * @return 显示名 Optional
     */
    public Optional<String> nameOpt() { return Optional.ofNullable(name); }

    /**
     * 描述的 Optional 视图。
     *
     * @return 描述 Optional
     */
    public Optional<String> descriptionOpt() { return Optional.ofNullable(description); }

    /**
     * 上下文窗口的 Optional 视图。
     *
     * @return 上下文窗口 Optional
     */
    public Optional<Integer> contextWindowOpt() { return Optional.ofNullable(contextWindow); }

    /**
     * 最大输出 token 的 Optional 视图。
     *
     * @return 最大输出 token Optional
     */
    public Optional<Integer> maxTokensOpt() { return Optional.ofNullable(maxTokens); }
}
