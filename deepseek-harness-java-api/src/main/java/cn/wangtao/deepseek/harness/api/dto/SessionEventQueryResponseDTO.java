package cn.wangtao.deepseek.harness.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 会话事件查询响应条目。
 * <p>对应会话事件溯源记录中的一条事件。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param seq 事件序列号，会话内单调递增
 * @param eventType 事件类型编码
 * @param timestamp 事件发生时间
 * @param data 事件负载，结构随事件类型而定
 */
public record SessionEventQueryResponseDTO(
        long seq,
        String eventType,
        Instant timestamp,
        Map<String, Object> data
) {
}
