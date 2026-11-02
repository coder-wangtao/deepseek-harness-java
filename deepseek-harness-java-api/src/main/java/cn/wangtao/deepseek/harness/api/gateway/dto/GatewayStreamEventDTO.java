package cn.wangtao.deepseek.harness.api.gateway.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 网关 SSE 流式推送事件。
 * <p>统一封装推送给前端的事件帧：来源、事件类型、发生时间与负载。</p>
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param source 事件来源，标识事件由哪条流（如 Agent / 工作流）产生
 * @param event 事件类型，前端据此选择渲染逻辑
 * @param occurredAt 事件发生时间
 * @param payload 事件负载，结构随事件类型而定；为 null 时以空 Map 兜底
 */
public record GatewayStreamEventDTO(
        String source,
        String event,
        Instant occurredAt,
        Object payload
) {
    /**
     * 静态工厂：以当前时间作为事件发生时间构造事件帧。
     *
     * @param source 事件来源
     * @param event 事件类型
     * @param payload 事件负载
     * @return 网关流式推送事件
     */
    public static GatewayStreamEventDTO of(String source, String event, Object payload) {
        return new GatewayStreamEventDTO(source, event, Instant.now(), payload);
    }

    public GatewayStreamEventDTO {
        if (payload == null) {
            payload = Map.of();
        }
    }
}
