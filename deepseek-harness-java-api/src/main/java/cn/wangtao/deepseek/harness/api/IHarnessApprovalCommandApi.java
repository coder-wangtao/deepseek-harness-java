package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.ApprovalActionResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * 平台审批命令接口：负责对会话待审批项执行审批通过操作。
 *
 * @author wt
 */
public interface IHarnessApprovalCommandApi {

    /**
     * 对指定会话的当前待审批项执行审批通过。
     *
     * @param sessionId 会话 ID
     * @return 审批操作结果，包含会话 ID、审批后状态、说明与操作时间
     */
    Response<ApprovalActionResponseDTO> approve(String sessionId);
}
