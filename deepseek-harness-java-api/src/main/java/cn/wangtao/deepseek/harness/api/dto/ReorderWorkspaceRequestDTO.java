package cn.wangtao.deepseek.harness.api.dto;

import java.util.List;

/**
 * 工作区排序请求。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param order 工作区名称列表，按前端期望的展示顺序排列；用例按该顺序持久化排序
 */
public record ReorderWorkspaceRequestDTO(List<String> order) {
}
