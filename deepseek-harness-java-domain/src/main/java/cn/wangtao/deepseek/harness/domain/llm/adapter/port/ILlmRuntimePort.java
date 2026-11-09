package cn.wangtao.deepseek.harness.domain.llm.adapter.port;

import cn.wangtao.deepseek.harness.domain.llm.adapter.LlmAdapter;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.StreamChunk;

import java.util.concurrent.Flow;

/**
 * 大模型运行时端口（Port）——领域层与"模型供应商世界"之间的唯一插座。
 *
 * <h2>设计目的</h2>
 * <p>
 * 端口-适配器（六边形架构）里的"端口"：领域层只面向本接口编程，
 * 不 import 任何基础设施类型。实现方（infrastructure 层的
 * {@code InMemoryLlmRuntimePort}）维护 provider → 适配器的路由表，
 * 并在路由之上叠加横切能力（未注册兜底、异常归一化、流式重试）。
 * 端口接口本身保持纯粹——只声明"能做什么"，不声明"怎么做"。
 *
 * <h2>与 {@link LlmAdapter} 的分工</h2>
 * <ul>
 *   <li>Adapter：单个供应商的翻译官（领域事件 ⇄ 厂商协议）；</li>
 *   <li>Port：多适配器的路由器 + 横切层（重试、异常包装、兜底创建）。</li>
 * </ul>
 *
 * <h2>典型调用链</h2>
 * <pre>
 *   调用方 → port.stream(options)
 *            → resolve provider → adapter.stream(options)
 *            → 归一化 StreamChunk 事件流返回给调用方
 * </pre>
 *
 * @author wt
 */
public interface ILlmRuntimePort {

    /**
     * 注册 provider 对应的适配器（写入路由表）。
     *
     * @param provider 模型提供方编码
     * @param adapter  LLM 适配器实现
     */
    void registerAdapter(String provider, LlmAdapter adapter);

    /**
     * 注销 provider 的适配器（从路由表移除，后续请求路由失败）。
     *
     * @param provider 模型提供方编码
     */
    void unregisterAdapter(String provider);

    /**
     * 判断 provider 是否已注册适配器（路由前的快速探测）。
     *
     * @param provider 模型提供方编码
     * @return 已注册返回 true
     */
    default boolean hasAdapter(String provider) {
        return false;
    }

    /**
     * 列出全部已注册 provider 的展示信息。
     *
     * @return provider 信息列表
     */
    java.util.List<LlmAdapter.LlmProviderInfo> listProviders();

    /**
     * 列出指定 provider 的模型目录（委托给其适配器）。
     *
     * @param provider 模型提供方编码
     * @return 模型条目列表；provider 未注册时为空列表
     */
    java.util.List<LlmAdapter.LlmModelInfo> listModels(String provider);

    /**
     * 解析指定 provider 下某模型的精确元数据。
     *
     * @param provider 模型提供方编码
     * @param model    模型编码
     * @return 已解析的模型元数据
     * @throws IllegalArgumentException provider 未注册时抛出
     */
    LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model);

    /**
     * 发起流式模型调用（端口的核心能力）。
     *
     * <p>运行距离：按 options.provider() 路由适配器 → 读取重试策略 →
     * 返回（可能带重试包装的）StreamChunk 事件流。路由失败时也应返回
     * 一个只含 Finish(Error) 的流而非抛异常，保证调用方统一用流式语义处理失败。</p>
     *
     * @param options 生成参数
     * @return 可订阅的模型输出流
     */
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);

}
