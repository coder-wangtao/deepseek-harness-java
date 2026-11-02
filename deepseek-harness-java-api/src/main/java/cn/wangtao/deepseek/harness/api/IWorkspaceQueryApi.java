package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.WorkspaceEntryResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 工作区查询接口：负责查询工作区列表与浏览目录。
 *
 * @author wt
 */
public interface IWorkspaceQueryApi {

    /**
     * 查询当前全部工作区。
     *
     * @return 工作区列表，每项包含名称与路径
     */
    Response<List<WorkspaceEntryResponseDTO>> getWorkspaces();

    /**
     * 列出指定路径下的子目录。
     *
     * @param path 目录绝对路径
     * @return 该路径下的子目录名称列表
     */
    Response<List<String>> listDirectories(String path);
}
