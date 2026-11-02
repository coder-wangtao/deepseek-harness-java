package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.CreateWorkspaceRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.RenameWorkspaceRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.ReorderWorkspaceRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.WorkspaceEntryResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 工作区命令接口：负责工作区的创建、删除、重命名与排序。
 *
 * @author wt
 */
public interface IWorkspaceCommandApi {

    /**
     * 创建一个工作区。
     *
     * @param request 创建工作区请求，包含工作区名称
     * @return 操作后的最新工作区列表，每项包含名称与路径
     */
    Response<List<WorkspaceEntryResponseDTO>> createWorkspace(CreateWorkspaceRequestDTO request);

    /**
     * 删除指定名称的工作区。
     *
     * @param name 工作区名称
     * @return 操作后的最新工作区列表，每项包含名称与路径
     */
    Response<List<WorkspaceEntryResponseDTO>> deleteWorkspace(String name);

    /**
     * 重命名指定的工作区。
     *
     * @param name 工作区当前名称
     * @param request 重命名工作区请求，包含新的工作区名称
     * @return 操作后的最新工作区列表，每项包含名称与路径
     */
    Response<List<WorkspaceEntryResponseDTO>> renameWorkspace(String name, RenameWorkspaceRequestDTO request);

    /**
     * 按指定顺序对工作区重新排序。
     *
     * @param request 工作区排序请求，包含按目标顺序排列的工作区名称列表
     * @return 排序后的最新工作区列表，每项包含名称与路径
     */
    Response<List<WorkspaceEntryResponseDTO>> reorderWorkspaces(ReorderWorkspaceRequestDTO request);
}
