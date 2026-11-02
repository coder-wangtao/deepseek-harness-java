package cn.wangtao.deepseek.harness.api.dto;

/**
 * 插件清单条目。
 * <p>插件清单（inventory）中单个插件的完整档案，含元信息、来源与清单路径。</p>
 *
 * @param pluginId 插件 ID
 * @param displayName 插件显示名称
 * @param pluginVersion 插件版本
 * @param runtimeType 运行时类型
 * @param installMode 安装模式
 * @param status 插件状态
 * @param sourcePath 插件来源路径
 * @param entrypoint 插件入口
 * @param manifestPath 插件清单文件路径
 * @param author 作者
 * @param description 插件描述
 * @param scope 作用范围
 * @author wt
 */
public record PluginInventoryItemDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String installMode,
        String status,
        String sourcePath,
        String entrypoint,
        String manifestPath,
        String author,
        String description,
        String scope
) {
}
