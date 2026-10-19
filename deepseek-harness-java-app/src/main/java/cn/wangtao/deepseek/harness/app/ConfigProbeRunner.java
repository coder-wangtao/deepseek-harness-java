package cn.wangtao.deepseek.harness.app;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 配置体系探针：应用就绪后打印配置分层与 profile 生效情况。
 *
 * <p>执行流程：Spring 容器就绪 → 监听 ApplicationReadyEvent →
 * 打印激活的 profile → 打印关键配置键的最终生效值 → 验证 profile 覆盖优先级。</p>
 *
 * <p>设计目的：让"配置从哪来"在启动日志里肉眼可见——
 * application.yml（框架层）→ harness.yml（业务层，双通道）→ 环境变量（嵌套兜底）→ 命令行参数（最高优先级）。</p>
 *
 * @author wangtao
 */
@Component
public class ConfigProbeRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigProbeRunner.class);

    private final Environment environment;

    /**
     * 注入 Spring 环境对象，用于按优先级解析配置键。
     *
     * @param environment Spring 环境（已合并所有配置源）
     */
    public ConfigProbeRunner(Environment environment) {
        this.environment = environment;
    }

    /**
     * 应用完全就绪后输出配置分层观测结果。
     *
     * @param event Spring 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        String[] activeProfiles = environment.getActiveProfiles();
        String profileText = activeProfiles.length == 0 ? "<default>" : Arrays.toString(activeProfiles);
        log.info("[配置探针] 激活 profile：{}", profileText);

        // 业务键：来自 harness.yml（classpath 或 ./ 覆盖）
        log.info("[配置探针] harness.llm.deepseek.base-url   = {}", resolve("harness.llm.deepseek.base-url"));
        log.info("[配置探针] harness.llm.deepseek.default-model = {}", resolve("harness.llm.deepseek.default-model"));
        // 覆盖优先级观测键：默认 profile 为 dev-default，standalone profile 应为 standalone
        log.info("[配置探针] harness.stage-name（观察 profile 覆盖）= {}", resolve("harness.stage-name"));
        // 环境变量嵌套兜底验证：不设 LLM_MAX_TOKENS 时应回落 harness.yml 的 8192
        log.info("[配置探针] harness.llm.deepseek.max-tokens = {}", resolve("harness.llm.deepseek.max-tokens"));

        ConfigurableApplicationContext context = event.getApplicationContext();
        log.info("[配置探针] 应用就绪：http://localhost:{}/actuator/health",
                environment.getProperty("server.port", "8090"));
    }

    /**
     * 读取配置键，未配置时返回占位标记（区别于空串）。
     */
    private String resolve(String key) {
        String value = environment.getProperty(key);
        return value == null ? "<unset>" : value;
    }

}
