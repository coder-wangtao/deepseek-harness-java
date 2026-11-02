package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;

/**
 * 已安装插件响应。
 * <p>描述一个已安装插件的元信息、运行形态与安装来源。</p>
 *
 * @param pluginId 插件 ID
 * @param displayName 插件显示名称
 * @param pluginVersion 插件版本
 * @param runtimeType 运行时类型
 * @param installMode 安装模式
 * @param status 插件状态
 * @param sourcePath 插件来源路径
 * @param entrypoint 插件入口
 * @param installedAt 安装时间
 * @author wt
     */
public record InstalledPluginResponseDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String installMode,
        String status,
        String sourcePath,
        String entrypoint,
        Instant installedAt
) {
}
