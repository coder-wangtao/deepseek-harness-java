package cn.wangtao.deepseek.harness.api.dto;

import java.util.List;

/**
 * 会话事件查询请求。
 * <p>按序列号区间与事件类型过滤会话事件溯源记录。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param sessionId 目标会话 ID
 * @param fromSeq 起始序列号（含）；为空时从头开始
 * @param toSeq 结束序列号（含）；为空时查到最新
 * @param eventTypes 事件类型过滤列表；为空时返回全部类型
 */
public record SessionEventQueryRequestDTO(
        String sessionId,
        Long fromSeq,
        Long toSeq,
        List<String> eventTypes
) {
}
