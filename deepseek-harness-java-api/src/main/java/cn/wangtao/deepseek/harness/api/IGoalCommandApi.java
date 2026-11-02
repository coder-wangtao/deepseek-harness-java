package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.GoalCommandRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.GoalViewResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * 目标命令接口：负责对会话目标执行创建、更新与状态流转等变更操作。
 *
 * @author wt
 */
public interface IGoalCommandApi {

    /**
     * 对指定会话应用一次目标变更命令，具体行为由请求中的 action 决定。
     *
     * @param sessionId 会话 ID
     * @param request 目标变更请求，包含 action、goalId、标题、描述、阻塞原因、最大轮次与是否启用续跑等字段
     * @return 变更后的目标展示视图，包含目标状态、轮次进度等
     */
    Response<GoalViewResponseDTO> apply(String sessionId, GoalCommandRequestDTO request);
}
