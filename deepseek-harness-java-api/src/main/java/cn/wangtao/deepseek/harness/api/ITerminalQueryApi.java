package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.TerminalReadResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.TerminalSessionResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 终端查询接口：负责查询终端会话列表与读取终端输出。
 *
 * @author wt
 */
public interface ITerminalQueryApi {

    /**
     * 查询当前全部终端会话。
     *
     * @return 终端会话列表，每项包含会话 ID、名称、工作目录、状态、创建时间与输出摘要等
     */
    Response<List<TerminalSessionResponseDTO>> listSessions();

    /**
     * 读取指定终端会话的当前输出内容，供前端轮询。
     *
     * @param sessionId 终端会话 ID
     * @param maxChars 最多读取的输出字符数
     * @return 终端读取结果，包含会话 ID、输出内容、是否截断、会话状态与退出码
     */
    Response<TerminalReadResponseDTO> read(String sessionId, int maxChars);
}
