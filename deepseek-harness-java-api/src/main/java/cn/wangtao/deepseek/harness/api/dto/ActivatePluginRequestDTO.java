package cn.wangtao.deepseek.harness.api.dto;

/**
 * 激活插件请求。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 * @param pluginId 待激活的插件 ID
 * @author wt
 */
public record ActivatePluginRequestDTO(String pluginId) {
}
