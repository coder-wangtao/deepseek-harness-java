package cn.wangtao.deepseek.harness.api.gateway;

import cn.wangtao.deepseek.harness.api.gateway.dto.GatewayStreamRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 流式网关接口。
 * <p>统一入口：按请求中的来源标识建立 SSE 连接，将 Agent 消息流或工作流事件流
 * 以 {@code GatewayStreamEventDTO} 事件帧持续推送给前端，直至流结束或连接关闭。</p>
 *
 * @author wt
 */
public interface IGatewayStreamApi {

    /**
     * 建立 SSE 连接并订阅事件流。
     *
     * @param request 流订阅请求，含来源标识与对应流的启动参数
     * @return SseEmitter 事件流发射器：以 {@code GatewayStreamEventDTO} 的
     *         event/payload 结构逐帧推送，运行结束后完成连接
     */
    SseEmitter stream(GatewayStreamRequestDTO request);
}
