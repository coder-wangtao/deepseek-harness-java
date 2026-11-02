package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 插件清单响应。
 * <p>汇总全部插件条目的清单快照，供前端整体展示插件资产。</p>
 *
 * @param generatedAt 清单生成时间
 * @param count 插件数量
 * @param plugins 插件条目列表
 * @author wt
 */
public record PluginInventoryResponseDTO(
        Instant generatedAt,
        long count,
        List<PluginInventoryItemDTO> plugins
) {
}
