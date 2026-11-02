package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.SubmitTaskRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.SubmitTaskResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * 平台任务接口：负责提交任务并返回任务受理结果。
 *
 * @author wt
 */
public interface IHarnessTaskApi {
    /**
     * 提交一个任务，由平台解析渠道、模型与工具后受理执行。
     *
     * @param request 任务提交请求，包含工作区 ID、用户提示词、执行档位编码、模型渠道编码（为空时使用当前激活渠道）与请求启用的工具编码
     * @return 任务受理结果，包含任务 ID、会话 ID、生命周期状态、解析后的渠道与模型、启用的工具、是否需要人工审批与接收时间
     */
    Response<SubmitTaskResponseDTO> submitTask(SubmitTaskRequestDTO request);
}
