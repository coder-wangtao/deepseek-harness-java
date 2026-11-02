package cn.wangtao.deepseek.harness.api.dto;

/**
 * 插件制品候选信息。
 * <p>扫描来源目录得到的疑似插件制品及其校验结论，供安装前确认。</p>
 *
 * @param pluginId 插件 ID
 * @param displayName 插件显示名称
 * @param pluginVersion 插件版本
 * @param runtimeType 运行时类型
 * @param entrypoint 插件入口
 * @param author 作者
 * @param description 插件描述
 * @param sourcePath 制品来源路径
 * @param groupId Maven 坐标 groupId；非 JAR 制品为 null
 * @param artifactId Maven 坐标 artifactId；非 JAR 制品为 null
 * @param version Maven 坐标版本；非 JAR 制品为 null
 * @param valid 是否为有效插件候选
 * @param message 校验结果说明
 * @param sourceType 来源类型
 * @author wt
 */
public record PluginArtifactCandidateDTO(
        String pluginId,
        String displayName,
        String pluginVersion,
        String runtimeType,
        String entrypoint,
        String author,
        String description,
        String sourcePath,
        String groupId,
        String artifactId,
        String version,
        boolean valid,
        String message,
        String sourceType
) {
}
