package cn.wangtao.deepseek.harness.api.dto;

/**
 * 会话恢复请求。
 * <p>从事件溯源记录重建指定会话的运行时上下文。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param sessionId 待恢复的会话 ID
 */
public record SessionRestoreRequestDTO(String sessionId) {
}
