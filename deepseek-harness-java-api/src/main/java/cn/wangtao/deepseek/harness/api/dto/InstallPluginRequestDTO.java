package cn.wangtao.deepseek.harness.api.dto;

/**
 * 安装插件请求。
 * <p>携带插件元信息与来源路径，由应用门面交予用例完成安装。</p>
 *
 * @param pluginId 插件 ID
 * @param displayName 插件显示名称
 * @param pluginVersion 插件版本
 * @param runtimeType 运行时类型
 * @param sourcePath 插件来源路径
 * @param entrypoint 插件入口；可选，缺省时走 ServiceLoader 发现
 * @author wt
 */
public record InstallPluginRequestDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String sourcePath,
        String entrypoint
) {
}
