package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 运行插件响应。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param pluginId 插件 ID
 * @param processId 插件运行进程 ID
 * @param runtimeCommand 启动插件的实际运行时命令
 * @param workingDirectory 插件进程的工作目录
 * @param running 插件进程是否处于运行中
 * @param startedAt 启动时间
 * @param message 附加说明信息，如启动失败原因；正常时可为 null
 */
public record RunPluginResponseDTO(
        String pluginId,
        long processId,
        String runtimeCommand,
        String workingDirectory,
        boolean running,
        Instant startedAt,
        String message
) {
}
