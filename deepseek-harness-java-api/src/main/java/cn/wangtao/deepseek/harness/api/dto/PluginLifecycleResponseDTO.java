package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 插件生命周期操作响应。
 * <p>停用/启用/卸载等生命周期操作统一返回的状态机结果。</p>
 *
 * @param pluginId 插件 ID
 * @param status 操作后的插件状态
 * @param message 操作结果说明
 * @param updatedAt 状态更新时间
 * @author wt
 */
public record PluginLifecycleResponseDTO(
        String pluginId,
        String status,
        String message,
        Instant updatedAt
) {
}
