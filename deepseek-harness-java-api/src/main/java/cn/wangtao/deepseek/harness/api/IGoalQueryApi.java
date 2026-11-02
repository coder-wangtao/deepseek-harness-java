package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.GoalViewResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * 目标查询接口：负责查询会话当前目标的展示视图。
 *
 * @author wt
 */
public interface IGoalQueryApi {

    /**
     * 查询指定会话当前的目标展示视图。
     *
     * @param sessionId 会话 ID
     * @return 目标展示视图，包含目标状态、轮次进度等；会话尚无目标时返回 exists 为 false 的空视图
     */
    Response<GoalViewResponseDTO> getGoal(String sessionId);
}
