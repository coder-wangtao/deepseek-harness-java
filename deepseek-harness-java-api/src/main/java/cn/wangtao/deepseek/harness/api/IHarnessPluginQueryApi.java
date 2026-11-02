package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.InstalledPluginResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.PluginInventoryResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.QueryPluginStatusResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 平台插件查询接口：负责查询已安装插件、插件清单与插件运行状态。
 *
 * @author wt
 */
public interface IHarnessPluginQueryApi {

    /**
     * 查询当前已安装的插件列表。
     *
     * @return 已安装插件列表，每项包含插件 ID、显示名称、版本、运行时类型、安装方式、状态与安装时间等
     */
    Response<List<InstalledPluginResponseDTO>> listInstalledPlugins();

    /**
     * 查询插件清单（inventory）。
     *
     * @return 插件清单，包含生成时间、插件数量与插件明细列表
     */
    Response<PluginInventoryResponseDTO> listPluginInventory();

    /**
     * 查询指定插件的运行状态。
     *
     * @param pluginId 插件 ID
     * @return 插件运行状态，包含进程 ID、是否运行中、运行时命令、工作目录与启动时间等
     */
    Response<QueryPluginStatusResponseDTO> queryPluginStatus(String pluginId);
}
