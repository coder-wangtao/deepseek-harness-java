package cn.wangtao.deepseek.harness.api.dto;

/**
 * 会话列表分页查询请求。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param workspaceId 目标工作区 ID
 * @param offset 分页偏移量，从 0 开始
 * @param limit 每页返回条数上限
 */
public record SessionListRequestDTO(
        String workspaceId,
        int offset,
        int limit
) {
}
