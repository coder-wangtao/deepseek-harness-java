package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.TerminalOpenRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.TerminalSendRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.TerminalSendResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.TerminalSessionResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * 终端命令接口：负责终端会话的打开、命令发送与关闭。
 *
 * @author wt
 */
public interface ITerminalCommandApi {

    /**
     * 打开一个终端会话，由基础设施层启动 shell 进程。
     *
     * @param request 终端打开请求，包含终端名称与工作目录
     * @return 终端会话信息，包含会话 ID、名称、工作目录、状态、创建时间与输出摘要等
     */
    Response<TerminalSessionResponseDTO> open(TerminalOpenRequestDTO request);

    /**
     * 向指定终端会话发送命令，并等待返回本次命令输出。
     *
     * @param sessionId 终端会话 ID
     * @param request 终端发送请求，包含命令文本与超时时间（毫秒）
     * @return 本次命令输出，包含会话 ID、输出内容、是否截断、会话状态与退出码
     */
    Response<TerminalSendResponseDTO> send(String sessionId, TerminalSendRequestDTO request);

    /**
     * 关闭指定终端会话，终止其 shell 进程。
     *
     * @param sessionId 终端会话 ID
     * @return 关闭后的终端会话信息，包含会话 ID、名称、工作目录、状态与退出码等
     */
    Response<TerminalSessionResponseDTO> close(String sessionId);
}
