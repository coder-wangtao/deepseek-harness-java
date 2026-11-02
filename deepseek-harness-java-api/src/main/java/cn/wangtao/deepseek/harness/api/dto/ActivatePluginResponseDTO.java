package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 激活插件响应。
 * <p>返回插件激活后的运行时状态与桥接就绪情况，供前端确认插件可用。</p>
 *
 * @param pluginId 插件 ID
 * @param status 激活后的插件状态
 * @param packageType 插件包形态类型
 * @param runtimeCommand 运行时启动命令
 * @param bridgeReady 插件桥接是否就绪
 * @param message 激活结果说明
 * @param checkedAt 本次激活检查时间
 * @author wt
 */
public record ActivatePluginResponseDTO(
        String pluginId,
        String status,
        String packageType,
        String runtimeCommand,
        boolean bridgeReady,
        String message,
        Instant checkedAt
) {
}
