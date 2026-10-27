package cn.wangtao.deepseek.harness.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动 deepseek-harness 的 Java 版工程。
 **/
@SpringBootApplication(scanBasePackages = "cn.wangtao.deepseek.harness")
public class Application {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
