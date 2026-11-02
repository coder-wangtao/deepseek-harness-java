package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 插件运行状态查询响应。
 * <p>返回插件当前进程级运行信息，供前端监控插件运行状况。</p>
 *
 * @param pluginId 插件 ID
 * @param processId 运行进程 ID；未运行为 null
 * @param running 是否运行中
 * @param runtimeCommand 运行时启动命令
 * @param workingDirectory 进程工作目录
 * @param startedAt 启动时间；未运行为 null
 * @param message 状态说明
 * @author wt
 */
public record QueryPluginStatusResponseDTO(
        String pluginId,
        Long processId,
        boolean running,
        String runtimeCommand,
        String workingDirectory,
        Instant startedAt,
        String message
) {
}
