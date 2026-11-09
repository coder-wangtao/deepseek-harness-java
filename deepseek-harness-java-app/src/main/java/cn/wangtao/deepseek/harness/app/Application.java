package cn.wangtao.deepseek.harness.app;

import cn.wangtao.deepseek.harness.app.ConfigProbeRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动 deepseek-harness 的 Java 版工程。
 *
 * <h2>设计目的</h2>
 * <p>这是整个 Harness 的 Spring Boot 宿主入口。工程拆成 7 个 Maven 模块
 * （api / types / domain / case / infrastructure / trigger / app），
 * 目的是用模块边界强制执行分层依赖：domain 不依赖 infrastructure，
 * 所有外部能力（LLM、数据库、插件）都通过 domain 定义的 Port 接口反向接入。</p>
 *
 * <p>初始工程（xfg-0-1）：仅装配 Spring Boot 与 7 模块骨架，
 * MyBatis MapperScan 等能力随后续章节（xfg-5-1）引入。</p>
 *
 * <p>包扫描范围固定为 {@code cn.xiaofuge.deepseek.harness}，
 * 保证 7 个模块里的 @Component 都能被同一个容器发现。</p>
 *
 * @author wt
 */
@SpringBootApplication(scanBasePackages = "cn.wangtao.deepseek.harness")
public class Application {

    /**
     * 启动 Spring Boot 应用。
     *
     * <p>运行距离：JVM 入口 → 初始化 Spring 容器 → 扫描并注册全部 Bean →
     * 内嵌 Tomcat 监听 8090 端口 → 发布 ApplicationReadyEvent（由
     * {@link ConfigProbeRunner} 接收并打印配置观测日志）→ 应用进入服务状态。</p>
     *
     * @param args 命令行参数（支持 --server.port=xxx 等标准 Spring Boot 参数）
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
