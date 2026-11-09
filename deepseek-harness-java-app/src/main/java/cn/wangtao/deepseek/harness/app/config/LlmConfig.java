package cn.wangtao.deepseek.harness.app.config;

import cn.wangtao.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.wangtao.deepseek.harness.domain.llm.service.LlmGenerationService;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.InMemoryLlmRuntimePort;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek.DeepSeekAdapter;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek.DirectSeekCatalogModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * 把的手工装配改为 Spring 装配：
 * DeepSeekAdapterOptions（配置绑定）→ DeepSeekAdapter（适配器）
 * → InMemoryLlmRuntimePort（端口）→ LlmGenerationService（领域服务）。
 * 协议路由工厂与多渠道适配器在替换本装配。
 *
 * @author wt
 */
@Configuration
public class LlmConfig {

    /**
     * 构建适配器配置项：把 application.yml 中 harness.llm.deepseek.* 五项配置
     * 绑定为不可变的 DeepSeekAdapterOptions；流空闲超时沿用适配器默认值，
     * 模型目录注册 default-model 一个模型（多模型目录在扩展）。
     */
    @Bean
    public DeepSeekAdapterOptions deepSeekAdapterOptions(
            @Value("${harness.llm.deepseek.base-url}") String baseUrl,
            @Value("${harness.llm.deepseek.api-key}") String apiKey,
            @Value("${harness.llm.deepseek.max-tokens}") int maxTokens,
            @Value("${harness.llm.deepseek.context-window}") int contextWindow,
            @Value("${harness.llm.deepseek.default-model}") String defaultModel
    ) {
        return new DeepSeekAdapterOptions(
                baseUrl,
                apiKey,
                maxTokens,
                contextWindow,
                DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                List.of(new DirectSeekCatalogModel(defaultModel, defaultModel, null, contextWindow, maxTokens)),
                null
        );
    }

    /**
     * 构建运行时端口：内存版端口 + 注册 deepseek 适配器。
     * <p>
     * 端口是领域层唯一的"插座"，按 provider 路由到具体适配器；
     * 本节只注册 deepseek 一个渠道，多渠道路由在替换。
     */
    @Bean
    public ILlmRuntimePort llmRuntimePort(DeepSeekAdapterOptions options, ObjectMapper objectMapper) {
        InMemoryLlmRuntimePort port = new InMemoryLlmRuntimePort();
        port.registerAdapter("deepseek", new DeepSeekAdapter(options, objectMapper));
        return port;
    }

    /** 构建领域生成服务：注入端口即可，generate() 的流→阻塞翻译都在领域层完成。 */
    @Bean
    public LlmGenerationService llmGenerationService(ILlmRuntimePort llmRuntimePort) {
        return new LlmGenerationService(llmRuntimePort);
    }
}
