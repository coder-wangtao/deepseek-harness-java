package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.ResolveRuntimeApprovalRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.ResolveRuntimeApprovalResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.RuntimeApprovalResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 运行时审批接口：负责查询运行期待审批项并对其作出裁决。
 *
 * @author wt
 */
public interface IRuntimeApprovalApi {

    /**
     * 查询当前全部待审批项。
     *
     * @return 待审批项列表，每项包含审批 ID、会话 ID、工具名称、展示用命令、参数与创建时间
     */
    Response<List<RuntimeApprovalResponseDTO>> listPending();

    /**
     * 对指定待审批项作出裁决（批准或拒绝）。
     *
     * @param approvalId 审批 ID
     * @param request 裁决请求，包含审批结论 verdict（批准/拒绝）
     * @return 裁决结果，包含是否裁决成功、生效的结论与错误说明
     */
    Response<ResolveRuntimeApprovalResponseDTO> resolve(
            String approvalId,
            ResolveRuntimeApprovalRequestDTO request
    );
}
