package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.StartWorkflowRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.WorkflowEventResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.WorkflowStartResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.function.Consumer;

/**
 * 工作流命令接口：负责工作流的启动、流式启动与取消。
 *
 * @author wt
 */
public interface IWorkflowCommandApi {

    /**
     * 启动一次工作流运行。
     *
     * @param request 工作流启动请求，包含工作流脚本、名称、父级工作目录与启动参数
     * @return 启动结果，包含运行 ID 与运行状态
     */
    Response<WorkflowStartResponseDTO> start(StartWorkflowRequestDTO request);

    /**
     * 启动一次工作流运行，并在执行过程中通过回调推送工作流事件。
     *
     * @param request 工作流启动请求，包含工作流脚本、名称、父级工作目录与启动参数
     * @param eventSink 工作流事件回调，每产生一个事件推送一次
     * @return 启动结果，包含运行 ID 与运行状态
     */
    Response<WorkflowStartResponseDTO> startStreaming(
            StartWorkflowRequestDTO request,
            Consumer<WorkflowEventResponseDTO> eventSink
    );

    /**
     * 取消指定的工作流运行。
     *
     * @param runId 工作流运行 ID
     * @param reason 取消原因，可为空
     * @return 取消结果，仅包含状态码与说明，无业务数据
     */
    Response<Void> cancel(String runId, String reason);
}
