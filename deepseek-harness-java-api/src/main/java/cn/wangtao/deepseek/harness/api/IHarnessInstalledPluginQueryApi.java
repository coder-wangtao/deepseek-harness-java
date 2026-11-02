package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.InstalledPluginResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 已安装插件查询接口：负责查询已安装插件列表。
 *
 * @author wt
 */
public interface IHarnessInstalledPluginQueryApi {

    /**
     * 查询当前已安装的插件列表。
     *
     * @return 已安装插件列表，每项包含插件 ID、显示名称、版本、运行时类型、安装方式、状态与安装时间等
     */
    Response<List<InstalledPluginResponseDTO>> listInstalledPlugins();
}
