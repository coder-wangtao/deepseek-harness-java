package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.PendingApprovalResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 平台审批查询接口：负责查询待审批项列表。
 *
 * @author wt
 */
public interface IHarnessApprovalQueryApi {

    /**
     * 查询当前待审批项列表。
     *
     * @param limit 返回条数上限
     * @return 待审批项列表，每项包含会话 ID、标题、内容预览、状态与创建时间
     */
    Response<List<PendingApprovalResponseDTO>> listPendingApprovals(int limit);
}
