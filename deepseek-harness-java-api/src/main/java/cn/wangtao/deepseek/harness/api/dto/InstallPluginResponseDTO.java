package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 安装插件响应。
 * <p>返回安装结果、落库后的运行形态与是否可直接执行。</p>
 *
 * @param pluginId 插件 ID
 * @param runtimeType 运行时类型
 * @param installMode 安装模式
 * @param status 安装后的插件状态
 * @param directlyExecutable 是否可直接执行（无需额外装配）
 * @param installedAt 安装时间
 * @author wt
 */
public record InstallPluginResponseDTO(
        String pluginId,
        String runtimeType,
        String installMode,
        String status,
        boolean directlyExecutable,
        Instant installedAt
) {
}
