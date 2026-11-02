package cn.wangtao.deepseek.harness.api.stream;

import cn.wangtao.deepseek.harness.api.dto.StartWorkflowRequestDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工作流流式接口：负责以 SSE 方式流式推送工作流运行事件。
 *
 * @author wt
 */
public interface IWorkflowStreamApi {

    /**
     * 启动一次工作流运行，并通过 SSE 流式推送运行过程中的工作流事件。
     *
     * @param request 工作流启动请求，包含工作流脚本、名称、父级工作目录与启动参数
     * @return SSE 推送器，用于向前端持续推送工作流事件
     */
    SseEmitter stream(StartWorkflowRequestDTO request);
}
