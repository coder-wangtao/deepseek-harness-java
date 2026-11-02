package cn.wangtao.deepseek.harness.api.dto;

/**
 * 工作区条目。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param name 工作区名称
 * @param path 工作区目录路径
 */
public record WorkspaceEntryResponseDTO(String name, String path) {
}
