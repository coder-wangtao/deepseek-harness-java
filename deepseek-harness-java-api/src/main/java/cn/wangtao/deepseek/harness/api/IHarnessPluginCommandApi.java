package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.ActivatePluginRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.ActivatePluginResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.InstallPluginRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.InstallPluginResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.PluginLifecycleResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.RunPluginRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.RunPluginResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

/**
 * 平台插件命令接口：负责插件的安装、激活、运行、停用、启用与卸载。
 *
 * @author wt
 */
public interface IHarnessPluginCommandApi {

    /**
     * 安装插件。
     *
     * @param request 安装插件请求，包含插件 ID、显示名称、版本、运行时类型、来源路径与入口
     * @return 安装结果，包含插件 ID、运行时类型、安装方式、状态、是否可直接执行与安装时间
     */
    Response<InstallPluginResponseDTO> installPlugin(InstallPluginRequestDTO request);

    /**
     * 激活插件，检查运行时环境并准备插件桥接。
     *
     * @param request 激活插件请求，包含插件 ID
     * @return 激活结果，包含插件 ID、状态、包类型、运行时命令、桥接是否就绪与检查时间
     */
    Response<ActivatePluginResponseDTO> activatePlugin(ActivatePluginRequestDTO request);

    /**
     * 运行插件，启动其运行时进程。
     *
     * @param request 运行插件请求，包含插件 ID
     * @return 运行结果，包含插件 ID、进程 ID、运行时命令、工作目录、是否运行中与启动时间
     */
    Response<RunPluginResponseDTO> runPlugin(RunPluginRequestDTO request);

    /**
     * 停用指定插件。
     *
     * @param pluginId 插件 ID
     * @return 插件生命周期操作结果，包含插件 ID、操作后状态、说明与更新时间
     */
    Response<PluginLifecycleResponseDTO> disablePlugin(String pluginId);

    /**
     * 启用指定插件。
     *
     * @param pluginId 插件 ID
     * @return 插件生命周期操作结果，包含插件 ID、操作后状态、说明与更新时间
     */
    Response<PluginLifecycleResponseDTO> enablePlugin(String pluginId);

    /**
     * 卸载指定插件。
     *
     * @param pluginId 插件 ID
     * @return 插件生命周期操作结果，包含插件 ID、操作后状态、说明与更新时间
     */
    Response<PluginLifecycleResponseDTO> uninstallPlugin(String pluginId);
}
