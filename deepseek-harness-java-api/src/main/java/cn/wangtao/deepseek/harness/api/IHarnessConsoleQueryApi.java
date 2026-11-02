package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.ConversationMessageResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.ConversationSessionSummaryResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 控制台查询接口：负责查询最近会话列表与会话历史消息。
 *
 * @author wt
 */
public interface IHarnessConsoleQueryApi {

    /**
     * 分页查询最近的会话摘要列表。
     *
     * @param limit 返回条数上限
     * @param offset 跳过的条数，用于分页
     * @return 会话摘要列表，每项包含会话 ID、标题、内容预览、状态、创建时间、Agent ID 与工作区 ID
     */
    Response<List<ConversationSessionSummaryResponseDTO>> listRecentSessions(int limit, int offset);

    /**
     * 查询指定会话的历史消息列表。
     *
     * @param sessionId 会话 ID
     * @return 历史消息列表，每项包含角色、内容、消息块与发生时间
     */
    Response<List<ConversationMessageResponseDTO>> listMessages(String sessionId);
}
